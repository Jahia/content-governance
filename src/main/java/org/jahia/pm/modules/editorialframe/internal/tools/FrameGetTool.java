/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.modules.mcp.spi.tool.JahiaMcpTool;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolError;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.modules.mcp.spi.tool.ToolSafety;
import org.jahia.pm.modules.editorialframe.api.EditorialFrameService;
import org.jahia.pm.modules.editorialframe.api.FrameContext;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;
import java.util.Set;

/** {@code frame.get}: the voice, a reader, the writing rules and the glossary of a site, in one language. */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class FrameGetTool implements JahiaMcpTool {

    @Reference
    private EditorialFrameService frames;

    @Override
    public String name() {
        return "frame.get";
    }

    @Override
    public String description() {
        return "Get the editorial frame of a Jahia site in one language: the essentials of the voice of the brand "
                + "and of the reader to write for, the writing rules, and the glossary terms of the text you work on. "
                + "Call it before you write, change or translate content of the site, and follow it. Give the text, "
                + "such as the source of a translation, to get its glossary terms. Name a persona to write for "
                + "another reader of the site.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = FrameTools.schema("The frame of a site");
        FrameTools.property(schema, "siteKey", "string", "The key of the site, such as digitall.", true);
        FrameTools.property(schema, "locale", "string", "The language of the content, such as en or fr.", true);
        FrameTools.property(schema, "persona", "string", "The name of a persona. Leave it out for the default persona.", false);
        FrameTools.property(schema, "text", "string",
                "The text you work on, plain or HTML, such as the source of a translation. The frame then lists the "
                        + "glossary terms that it holds, and the rules that it breaks first.", false);
        return schema;
    }

    @Override
    public Set<String> tags() {
        return Set.of("read-only", "frame");
    }

    @Override
    public ToolSafety safety() {
        return ToolSafety.READ_ONLY;
    }

    @Override
    public ToolResult execute(JsonNode input, ToolContext ctx) {
        String siteKey = FrameTools.text(input, "siteKey");
        ToolResult denied = FrameTools.denied(siteKey, ctx);
        if (denied != null) {
            return denied;
        }
        String locale = FrameTools.text(input, "locale");
        String text = FrameTools.text(input, "text");
        if (text != null && text.length() > FrameCheckTool.MAX_TEXT) {
            return ToolResult.error(ToolError.CODE_VALIDATION, "text is longer than " + FrameCheckTool.MAX_TEXT
                    + " characters", "Give the part of the text you work on.");
        }
        FrameContext context = frames.buildContext(new FrameRequest(siteKey, locale == null ? "en" : locale,
                FrameTools.text(input, "persona"), 4000, text));
        ObjectNode result = FrameTools.MAPPER.createObjectNode();
        result.put("fingerprint", context.fingerprint());
        result.put("persona", context.persona());
        result.set("personas", FrameTools.MAPPER.valueToTree(context.personas()));
        result.put("frame", context.isEmpty() ? "This site has no editorial frame." : context.text());
        ctx.audit().recordEvent("frame.get", Map.of("siteKey", siteKey,
                "fingerprint", context.fingerprint() == null ? "" : context.fingerprint()));
        return ToolResult.ok(result);
    }
}
