/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The skills that a site or the group holds as content, read from the live workspace. The property
 * {@code skillMetadata} holds the keys of {@code metadata} of the skill as {@code key=value}, and a
 * list value, such as {@code jahia-in-scope}, as JSON.
 */
public final class JcrSkills {

    private static final Logger logger = LoggerFactory.getLogger(JcrSkills.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String SKILL = "efnt:skill";
    public static final String FILE = "efnt:skillFile";
    public static final String METADATA = "skillMetadata";

    private JcrSkills() {
    }

    /** The skills under a site, with the given scope: {@code site} or {@code group}. */
    public static List<LoadedSkill> read(JCRSessionWrapper session, String site, String scope) throws RepositoryException {
        List<LoadedSkill> skills = new ArrayList<>();
        if (!session.nodeExists("/sites/" + site)) {
            return skills;
        }
        for (JCRNodeWrapper node : FrameReader.query(session, SKILL, site)) {
            try {
                skills.add(load(node, scope));
            } catch (RepositoryException | RuntimeException e) {
                logger.warn("The skill {} cannot be read: {}", node.getPath(), e.getMessage());
            }
        }
        return skills;
    }

    static LoadedSkill load(JCRNodeWrapper node, String scope) throws RepositoryException {
        Map<String, String> files = new LinkedHashMap<>();
        JCRNodeIteratorWrapper children = node.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (!child.isNodeType(FILE)) {
                continue;
            }
            String path = child.getPropertyAsString("relativePath");
            String text = child.getPropertyAsString("text");
            if (path != null && SkillPaths.isSafeRelative(path) && text != null) {
                files.put(path, text);
            }
        }
        List<String> consumers = values(node, "consumers");
        if (consumers.isEmpty()) {
            consumers = List.of("chat", "mcp");
        }
        String activation = node.getPropertyAsString("activation");
        String action = node.getPropertyAsString("assistantAction");
        String origin = node.getPropertyAsString("origin");
        String description = node.getPropertyAsString("description");
        Map<String, Object> metadata = metadata(values(node, METADATA));
        List<String> warnings = new ArrayList<>();
        String label = JahiaSkillKeys.label(metadata, warnings);
        List<String> tools = JahiaSkillKeys.tools(node.getPropertyAsString("allowedTools"), warnings);
        SkillSummary summary = new SkillSummary(node.getName(), description == null ? "" : description, scope,
                origin == null ? "ui" : origin, activation == null ? "manual" : activation, consumers,
                action == null ? "find" : action, node.getPath(), List.of(), label, tools,
                JahiaSkillKeys.coverage(metadata, description, warnings));
        for (String warning : warnings) {
            logger.debug("The skill {}: {}", node.getPath(), warning);
        }
        return new LoadedSkill(summary, node.getPropertyAsString("body"), files);
    }

    /** The values of {@code skillMetadata} as a map: a value that opens a JSON list is read as one. */
    public static Map<String, Object> metadata(List<String> entries) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String entry : entries) {
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String value = entry.substring(equals + 1);
            Object read = value;
            if (value.startsWith("[")) {
                try {
                    read = MAPPER.readValue(value, List.class);
                } catch (JsonProcessingException e) {
                    read = value;
                }
            }
            metadata.put(entry.substring(0, equals).trim(), read);
        }
        return metadata;
    }

    static List<String> values(JCRNodeWrapper node, String property) throws RepositoryException {
        List<String> values = new ArrayList<>();
        if (!node.hasProperty(property)) {
            return values;
        }
        if (node.getProperty(property).isMultiple()) {
            for (Value value : node.getProperty(property).getValues()) {
                values.add(value.getString());
            }
        } else {
            values.add(node.getProperty(property).getString());
        }
        return values;
    }
}
