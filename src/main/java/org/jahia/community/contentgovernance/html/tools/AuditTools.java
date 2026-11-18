/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.mcp.spi.tool.ToolError;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What the two tools of the HTML check share: their input, their access check and their errors. */
final class AuditTools {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String PATH = "path";
    static final String LOCALE = "locale";
    private static final String DESCRIPTION = "description";
    /** The permission to open jContent on a site, as the Page quality dashboard requires it. */
    static final String ACCESS = "jContentAccess";
    private static final Pattern SITE_PATH = Pattern.compile("^/sites/([A-Za-z0-9_-]{1,64})(/.*)?$");
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8}){0,3}$");

    /** A valid input: the path to check, the key of its site and its language. */
    record Request(String path, String siteKey, String locale) {
    }

    private AuditTools() {
    }

    static ObjectNode schema(String description) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put(DESCRIPTION, description);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject(PATH).put("type", "string").put("pattern", "^/sites/")
                .put(DESCRIPTION, "Absolute JCR path of a page, such as /sites/digitall/home/about, or of a "
                        + "content item. A content item gets the rules that read one item only.");
        properties.putObject(LOCALE).put("type", "string").put("pattern", LANGUAGE.pattern())
                .put(DESCRIPTION, "Language of the content to check, such as en or fr_FR. "
                        + "Find the languages of a site with site.get.");
        schema.putArray("required").add(PATH).add(LOCALE);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** The request, or {@code null} when the input is not valid. */
    static Request read(JsonNode input) {
        String path = text(input, PATH);
        String locale = text(input, LOCALE);
        if (path == null || locale == null || path.contains("..") || !LANGUAGE.matcher(locale).matches()) {
            return null;
        }
        Matcher site = SITE_PATH.matcher(path);
        return site.matches() ? new Request(path, site.group(1), locale) : null;
    }

    static ToolResult invalid() {
        return ToolResult.error(ToolError.CODE_VALIDATION, "path and locale are missing or invalid",
                "Give the path of a page or of a content item under /sites, such as /sites/digitall/home/about, "
                        + "and a language of the site, such as en. Find the languages of a site with site.get.");
    }

    /**
     * {@code null} when the user may check the site and the site has the check on, else the error to
     * return. A site that does not exist and a site the user cannot open get the same answer.
     *
     * @param feature the id of the tab of Page quality, which is also the name of the tool
     */
    static ToolResult denied(JCRSessionWrapper session, String siteKey, String feature) throws RepositoryException {
        String sitePath = "/sites/" + siteKey;
        if (!session.nodeExists(sitePath) || !session.getNode(sitePath).hasPermission(ACCESS)) {
            return ToolResult.error(ToolError.CODE_NOT_FOUND, "No site " + siteKey + " for this user",
                    "Use site.list to find the sites you can read.");
        }
        if (!SiteFeatures.isEnabled(sitePath, feature)) {
            return ToolResult.error(ToolError.CODE_UNAVAILABLE,
                    "The check " + feature + " is switched off on the site " + siteKey + ".",
                    "A site administrator switches it on in Site Settings > Content governance.");
        }
        return null;
    }

    /** The error of a render that gave no HTML. */
    static ToolResult failed(HtmlCheck.Outcome outcome) {
        if (outcome instanceof HtmlCheck.RenderFailed failed) {
            return ToolResult.error(ToolError.CODE_UNAVAILABLE, "Jahia could not render " + failed.path() + ".",
                    "Check that the template of the page exists, then try again.");
        }
        return ToolResult.error(ToolError.CODE_NOT_FOUND,
                "No page or content item at " + ((HtmlCheck.NotFound) outcome).path() + " for this user.",
                "Check the path. Find a page with page.list, or a content item with content.search.");
    }

    static ToolResult unreadable() {
        return ToolResult.error(ToolError.CODE_INTERNAL, "The content could not be read.",
                "Check the path, then try again.");
    }

    /** The fields that both tools return first. */
    static ObjectNode head(HtmlCheck.Rendered rendered, Request request, String workspace) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put(PATH, rendered.path());
        out.put("primaryType", rendered.primaryType());
        out.put("scope", rendered.scope().id());
        out.put(LOCALE, request.locale());
        out.put("workspace", workspace);
        return out;
    }

    private static String text(JsonNode input, String name) {
        JsonNode value = input == null ? null : input.get(name);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText().trim();
    }
}
