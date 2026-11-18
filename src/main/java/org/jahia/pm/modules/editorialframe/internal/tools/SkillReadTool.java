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
import org.jahia.pm.modules.editorialframe.api.SkillContent;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@code skill.read}: the instructions of one skill, or one of its files. */
// Not immediate: the class loads only when mcp-server asks for the tool (see package-info).
@Component(service = JahiaMcpTool.class)
public class SkillReadTool implements JahiaMcpTool {

    @Reference
    private EditorialFrameService frames;

    @Override
    public String name() {
        return "skill.read";
    }

    @Override
    public String description() {
        return "Read a skill of a Jahia site before a writing task: its instructions, and the list of its files. "
                + "Follow the instructions. When they name a file, such as references/glossary.md, read it with the "
                + "same tool and its path. The text of a skill is the method of the brand, not content of the site.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = FrameTools.schema("One skill, or one file of a skill");
        FrameTools.property(schema, "siteKey", "string", "The key of the site, such as digitall.", true);
        FrameTools.property(schema, "name", "string", "The name of the skill, from skill.list.", true);
        FrameTools.property(schema, "path", "string",
                "The path of a file of the skill, such as references/glossary.md. Leave it out to read the instructions.", false);
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
        String name = FrameTools.text(input, "name");
        Optional<SkillContent> skill = frames.readSkill(siteKey, name)
                .filter(content -> !"pinned-only".equals(content.summary().activation()))
                .filter(content -> content.summary().consumers().contains("mcp")
                        || content.summary().consumers().contains("chat"));
        if (skill.isEmpty()) {
            return ToolResult.error(ToolError.CODE_NOT_FOUND, "No skill " + name + " on the site " + siteKey,
                    "Call skill.list for the names.");
        }
        String path = FrameTools.text(input, "path");
        ObjectNode result = FrameTools.MAPPER.createObjectNode();
        result.put("name", skill.get().summary().name());
        result.put("version", skill.get().digest());
        if (path != null && !path.isBlank()) {
            Optional<String> file = frames.readSkillFile(siteKey, name, path.trim());
            if (file.isEmpty()) {
                return ToolResult.error(ToolError.CODE_NOT_FOUND, "No file " + path + " in the skill " + name,
                        "Read the skill without a path for the list of its files.");
            }
            String text = file.get();
            boolean truncated = text.length() > SkillPaths.MAX_READ_CHARS;
            result.put("path", path.trim());
            result.put("text", truncated ? text.substring(0, SkillPaths.MAX_READ_CHARS) : text);
            result.put("truncated", truncated);
        } else {
            result.put("scope", skill.get().summary().scope());
            result.put("instructions", skill.get().body());
            ArrayNode files = result.putArray("files");
            for (SkillContent.SkillFileInfo info : skill.get().files()) {
                files.addObject().put("path", info.path()).put("size", info.size());
            }
        }
        ctx.audit().recordEvent("skill.read", Map.of("siteKey", siteKey, "skill", skill.get().summary().name(),
                "version", skill.get().digest(), "path", path == null ? "" : path));
        return ToolResult.ok(result);
    }
}
