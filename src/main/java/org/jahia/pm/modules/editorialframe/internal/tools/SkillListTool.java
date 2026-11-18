/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.modules.mcp.spi.tool.JahiaMcpTool;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.modules.mcp.spi.tool.ToolSafety;
import org.jahia.pm.modules.editorialframe.api.EditorialFrameService;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Set;

/** {@code skill.list}: the catalogue of the skills of a site. */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class SkillListTool implements JahiaMcpTool {

    @Reference
    private EditorialFrameService frames;

    @Override
    public String name() {
        return "skill.list";
    }

    @Override
    public String description() {
        return "List the skills of a Jahia site: the name and the description of each skill, which say what it "
                + "does and when to use it. A skill holds the method and the rules of a writing task, such as an "
                + "article brief or an SEO review. Call skill.read with a name to get its instructions before the "
                + "task. The skill brand-frame holds the voice, the readers and the writing rules of the site.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = FrameTools.schema("The skills of a site");
        FrameTools.property(schema, "siteKey", "string", "The key of the site, such as digitall.", true);
        FrameTools.property(schema, "consumer", "string", "Who uses the skills: mcp (the default) or chat.", false);
        return schema;
    }

    @Override
    public Set<String> tags() {
        return Set.of("read-only", "skills");
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
        String consumer = FrameTools.text(input, "consumer");
        ObjectNode result = FrameTools.MAPPER.createObjectNode();
        ArrayNode skills = result.putArray("skills");
        for (SkillSummary summary : frames.listSkills(siteKey, consumer == null ? "mcp" : consumer)) {
            if ("pinned-only".equals(summary.activation())) {
                continue;
            }
            ObjectNode skill = skills.addObject();
            skill.put("name", summary.name());
            skill.put("description", summary.description());
            skill.put("scope", summary.scope());
        }
        ctx.audit().recordEvent("skill.list", java.util.Map.of("siteKey", siteKey, "count", skills.size()));
        return ToolResult.ok(result);
    }
}
