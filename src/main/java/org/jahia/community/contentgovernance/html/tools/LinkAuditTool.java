/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.modules.mcp.spi.tool.JahiaMcpTool;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.modules.mcp.spi.tool.ToolSafety;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code audit.links}: renders a page or a content item, and checks its links. It changes nothing in
 * the repository. The external links are called after the JCR session is closed.
 */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class LinkAuditTool implements JahiaMcpTool {

    static final String NAME = "audit.links";
    private static final String STATUS = "status";
    private static final String URL = "url";

    @Reference
    private HtmlCheck check;

    /** The render and its internal links, or the error that stopped the check before them. */
    private record PageLinks(ToolResult error, HtmlCheck.Rendered rendered, LinkCheck.InternalLinks internal) {
    }

    public LinkAuditTool() {
        // Used by OSGi.
    }

    LinkAuditTool(HtmlCheck check) {
        this.check = check;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Check the links of one page, or of one content item. An internal link is broken when its node "
                + "does not exist, or when its node is not published, because a visitor then gets a 404. An "
                + "external link is broken when it answers HTTP 404 or 410; a 401, 403 or 429 is reported as not "
                + "verified. The server sends a HEAD request to each external link, up to "
                + LinkCheck.MAX_EXTERNAL_LINKS + " links, and never to a private or local address. The check reads "
                + "the content as the user sees it in the edit workspace, with the changes that are not published yet.";
    }

    @Override
    public JsonNode inputSchema() {
        return AuditTools.schema("A page or a content item whose links to check");
    }

    @Override
    public Set<String> tags() {
        return Set.of("read-only", "audit", "reads-ugc");
    }

    @Override
    public ToolSafety safety() {
        return ToolSafety.READ_ONLY;
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext ctx) {
        AuditTools.Request request = AuditTools.read(input);
        ToolResult result = request == null ? AuditTools.invalid() : run(request, ctx);
        ctx.audit().recordEvent(NAME, Map.of("ok", result.isOk(),
                AuditTools.PATH, request == null ? "" : request.path()));
        return result;
    }

    private ToolResult run(AuditTools.Request request, ToolContext ctx) {
        PageLinks found;
        try {
            found = ctx.withUserSession(Locales.of(request.locale()), session -> {
                JCRSessionWrapper jcr = (JCRSessionWrapper) session;
                ToolResult denied = AuditTools.denied(jcr, request.siteKey(), NAME);
                if (denied != null) {
                    return new PageLinks(denied, null, null);
                }
                HtmlCheck.Outcome outcome = check.render(jcr, request.path());
                if (!(outcome instanceof HtmlCheck.Rendered rendered)) {
                    return new PageLinks(AuditTools.failed(outcome), null, null);
                }
                return new PageLinks(null, rendered, check.internalLinks(rendered, jcr));
            });
        } catch (RepositoryException e) {
            return AuditTools.unreadable();
        }
        if (found.error() != null) {
            return found.error();
        }
        ObjectNode out = AuditTools.head(found.rendered(), request, ctx.workspace());
        out.set("internal", internal(found.internal()));
        out.set("external", external(check.externalLinks(found.rendered())));
        return ToolResult.ok(out);
    }

    static ObjectNode internal(LinkCheck.InternalLinks links) {
        ObjectNode internal = AuditTools.MAPPER.createObjectNode();
        internal.put("checked", links.checked());
        internal.put("notCheckedVanityLinks", links.notCheckedVanityLinks());
        ArrayNode broken = internal.putArray("broken");
        for (LinkCheck.BrokenPath link : links.broken()) {
            broken.addObject().put(AuditTools.PATH, link.path()).put("reason", link.reason());
        }
        return internal;
    }

    static ObjectNode external(LinkCheck.ExternalLinks links) {
        ObjectNode external = AuditTools.MAPPER.createObjectNode();
        external.put("found", links.found());
        if (links.skippedAboveLimit() > 0) {
            external.put("skippedAboveLimit", links.skippedAboveLimit());
        }
        statuses(external.putArray("broken"), links.broken());
        statuses(external.putArray("notVerified"), links.notVerified());
        statuses(external.putArray("unreachable"), links.unreachable());
        ArrayNode blocked = external.putArray("blocked");
        links.blocked().forEach(blocked::add);
        external.put("ok", links.ok());
        return external;
    }

    private static void statuses(ArrayNode array, List<LinkCheck.UrlStatus> links) {
        for (LinkCheck.UrlStatus link : links) {
            array.addObject().put(URL, link.url()).put(STATUS, link.status());
        }
    }
}
