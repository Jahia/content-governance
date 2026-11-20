/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.modules.mcp.spi.tool.JahiaMcpTool;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolError;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.modules.mcp.spi.tool.ToolSafety;
import org.jahia.pm.modules.editorialframe.api.EditorialFrameService;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code frame.check}: the writing rules that a text breaks, checked by code. */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class FrameCheckTool implements JahiaMcpTool {

    static final int MAX_TEXT = 100_000;

    @Reference
    private EditorialFrameService frames;

    public FrameCheckTool() {
        // Used by OSGi.
    }

    FrameCheckTool(EditorialFrameService frames) {
        this.frames = frames;
    }

    @Override
    public String name() {
        return "frame.check";
    }

    @Override
    public String description() {
        return "Check a text against the writing rules of a Jahia site, with code and no model: forbidden terms, "
                + "terms to replace, style patterns and, for a whole page, mandatory mentions. Call it on a text "
                + "before you propose it, and fix each violation. A mandatory mention that the page lacks comes "
                + "with 'missing'. For a translation, give the source text and its locale too: each glossary term "
                + "of the source whose rendering the translation lacks comes with kind 'glossary', and its "
                + "rendering in 'missing'. A forbidden translation of a term of the source comes with kind "
                + "'glossary', the text found in 'match', and the rendering to write in 'replacement'.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = FrameTools.schema("A text to check");
        FrameTools.property(schema, FrameTools.SITE_KEY_INPUT, FrameTools.STRING_TYPE, "The key of the site, such as digitall.", true);
        FrameTools.property(schema, "locale", FrameTools.STRING_TYPE, "The language of the text, such as en or fr.", true);
        FrameTools.property(schema, "text", FrameTools.STRING_TYPE, "The text, plain or HTML.", true);
        FrameTools.property(schema, "sourceText", FrameTools.STRING_TYPE,
                "For a translation: the text that it translates, plain or HTML. The glossary is checked only with it.", false);
        FrameTools.property(schema, "sourceLocale", FrameTools.STRING_TYPE, "For a translation: the language of the source text, such as en.", false);
        FrameTools.property(schema, "document", "boolean",
                "True when the text is the whole text of a page, so that the mandatory mentions are checked too: "
                        + "all the texts of a page together, such as a page that you translate, or a content item "
                        + "shown as its own page, such as a news item, with the items under it. False for one field "
                        + "or one small item, such as a teaser: a mandatory mention belongs to the page.", false);
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
        String siteKey = FrameTools.text(input, FrameTools.SITE_KEY_INPUT);
        ToolResult denied = FrameTools.denied(siteKey, ctx);
        if (denied != null) {
            return denied;
        }
        String text = FrameTools.text(input, "text");
        if (text == null || text.length() > MAX_TEXT) {
            return ToolResult.error(ToolError.CODE_VALIDATION, "text is missing or longer than " + MAX_TEXT + " characters",
                    "Check one page or one field at a time.");
        }
        String locale = FrameTools.text(input, "locale");
        boolean document = input.path("document").asBoolean(false);
        String sourceText = FrameTools.text(input, "sourceText");
        String sourceLocale = FrameTools.text(input, "sourceLocale");
        if (sourceText != null && sourceText.length() > MAX_TEXT) {
            return ToolResult.error(ToolError.CODE_VALIDATION, "sourceText is longer than " + MAX_TEXT + " characters",
                    "Check one page or one field at a time.");
        }
        String language = locale == null ? "en" : locale;
        List<RuleViolation> violations = sourceText == null || sourceLocale == null
                ? frames.check(siteKey, language, text, document)
                : frames.checkTranslation(siteKey, sourceLocale, sourceText, language, text, document);
        ObjectNode result = FrameTools.MAPPER.createObjectNode();
        ArrayNode list = result.putArray("violations");
        for (RuleViolation violation : violations) {
            ObjectNode item = list.addObject();
            // The rule and its folder, as the Compliance section of the drawer names them.
            item.put("ruleId", violation.ruleId());
            item.put("folder", violation.folder());
            item.put("kind", violation.kind().name().toLowerCase());
            item.put("severity", violation.severity().name().toLowerCase());
            item.put("pattern", violation.pattern());
            if (violation.kind() == WritingRule.Kind.MANDATORY) {
                // A mandatory mention is missing from the text: it has no match and no excerpt.
                item.put("missing", violation.pattern());
            } else if (violation.kind() == WritingRule.Kind.GLOSSARY && violation.match() == null) {
                // The rendering of a term of the source is missing from the translation. The excerpt is of the source.
                item.put("missing", violation.replacement());
            } else {
                item.put("match", violation.match());
            }
            item.put("replacement", violation.replacement());
            item.put("justification", violation.justification());
            item.put("excerpt", violation.excerpt());
        }
        result.put("ok", violations.isEmpty());
        ctx.audit().recordEvent("frame.check", Map.of(FrameTools.SITE_KEY_INPUT, siteKey, "violations", violations.size()));
        return ToolResult.ok(result);
    }
}
