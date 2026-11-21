/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.rules.HtmlRule;
import org.jahia.community.contentgovernance.html.rules.HtmlRules;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code audit.accessibility}: renders a page or a content item, and checks its HTML against the
 * rules of the HTML check. It changes nothing in the repository.
 */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class AccessibilityAuditTool implements JahiaMcpTool {

    static final String NAME = "audit.accessibility";

    @Reference
    private HtmlCheck check;

    public AccessibilityAuditTool() {
        // Used by OSGi.
    }

    AccessibilityAuditTool(HtmlCheck check) {
        this.check = check;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Check one page, or one content item, for accessibility and SEO defects that need no browser "
                + "engine: image text alternatives, heading structure, link and button names, form labels, frame "
                + "titles, table headers, and for a whole page its language, title, description and ids. Each "
                + "finding names its rule, the severity that the site gives the rule and, when one applies, a WCAG "
                + "2.2 success criterion. A rule that the site switched off gives no finding. Colour contrast and "
                + "keyboard focus are not checked. The check reads the content as the user sees it in the edit "
                + "workspace, with the changes that are not published yet.";
    }

    @Override
    public JsonNode inputSchema() {
        return AuditTools.schema("A page or a content item to check for accessibility defects");
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
        Locale locale = Locales.of(request.locale());
        try {
            return ctx.withUserSession(locale, session -> {
                JCRSessionWrapper jcr = (JCRSessionWrapper) session;
                ToolResult denied = AuditTools.denied(jcr, request.siteKey(), NAME);
                if (denied != null) {
                    return denied;
                }
                HtmlCheck.Outcome outcome = check.render(jcr, request.path());
                if (!(outcome instanceof HtmlCheck.Rendered rendered)) {
                    return AuditTools.failed(outcome);
                }
                return ToolResult.ok(report(rendered, request, ctx.workspace(), check.findings(rendered, locale)));
            });
        } catch (RepositoryException e) {
            return AuditTools.unreadable();
        }
    }

    static ObjectNode report(HtmlCheck.Rendered rendered, AuditTools.Request request, String workspace,
                             List<Finding> findings) {
        ObjectNode out = AuditTools.head(rendered, request, workspace);
        out.put("findingCount", findings.size());
        out.put("limit", "Colour contrast and keyboard focus are not checked. At most "
                + HtmlRules.MAX_FINDINGS + " findings are listed. A content item gets the rules of one item only.");
        Map<String, Integer> byRule = new TreeMap<>();
        ArrayNode list = out.putArray("findings");
        for (Finding finding : findings) {
            byRule.merge(finding.ruleId(), 1, Integer::sum);
            ObjectNode item = list.addObject()
                    .put("rule", finding.ruleId())
                    .put("severity", finding.severity());
            HtmlRule htmlRule = HtmlRule.byId(finding.ruleId());
            if (htmlRule != null) {
                item.put("wcag", htmlRule.wcag()).put("scope", htmlRule.scope().id());
            }
            item.put("element", finding.text());
        }
        ObjectNode counts = out.putObject("countByRule");
        byRule.forEach(counts::put);
        return out;
    }
}
