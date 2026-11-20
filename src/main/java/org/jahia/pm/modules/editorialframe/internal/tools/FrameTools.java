/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolError;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/** What the tools of the frame share: their schemas and the check of the site. */
final class FrameTools {

    static final ObjectMapper MAPPER = new ObjectMapper();
    /** The permission that the jContent of a site needs, which every editor of the site holds. */
    static final String ACCESS = "jContentAccess";
    /** The name of the input that names the site. */
    static final String SITE_KEY_INPUT = "siteKey";
    /** The JSON Schema type of a text input. */
    static final String STRING_TYPE = "string";
    private static final String REQUIRED = "required";

    private FrameTools() {
    }

    static ObjectNode schema(String description) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("description", description);
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    static void property(ObjectNode schema, String name, String type, String description, boolean required) {
        ((ObjectNode) schema.get("properties")).putObject(name).put("type", type).put("description", description);
        if (required) {
            (schema.has(REQUIRED) ? (com.fasterxml.jackson.databind.node.ArrayNode) schema.get(REQUIRED)
                    : schema.putArray(REQUIRED)).add(name);
        }
    }

    static String text(JsonNode input, String name) {
        JsonNode value = input == null ? null : input.get(name);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }

    /**
     * {@code null} when the user may read the frame of the site, else the error to return. A site
     * that does not exist and a site the user cannot read get the same answer.
     */
    static ToolResult denied(String siteKey, ToolContext ctx) {
        if (siteKey == null || !FrameReader.SITE_KEY.matcher(siteKey).matches()) {
            return ToolResult.error(ToolError.CODE_VALIDATION, "siteKey is missing or invalid",
                    "Give the key of the site, such as digitall.");
        }
        try {
            boolean allowed = ctx.withUserSession(session -> {
                String path = "/sites/" + siteKey;
                if (!session.nodeExists(path)) {
                    return false;
                }
                Node site = session.getNode(path);
                return site instanceof JCRNodeWrapper node && node.hasPermission(ACCESS);
            });
            return allowed ? null : ToolResult.error(ToolError.CODE_NOT_FOUND, "No site " + siteKey + " for this user",
                    "Use site.list to find the sites you can read.");
        } catch (RepositoryException e) {
            return ToolResult.error(ToolError.CODE_INTERNAL, "The site could not be checked", "Try again.");
        }
    }
}
