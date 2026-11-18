/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.skills.JahiaSkillKeys;
import org.jahia.pm.modules.editorialframe.internal.skills.JcrSkills;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillDocument;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes an import plan as drafts in the edit workspace, in the session of the user, so that the
 * access rights of the user apply. Nothing is published: the frame and the skills reach the AI
 * features only after a publication, through the workflow of the site.
 */
public final class ImportWriter {

    /** The one parent folder of everything an import writes: voice, personas, rules, glossary and skills. */
    public static final String FOLDER = "ai-assistant";
    private static final String RULE_TYPE = "efnt:writingRule";
    private static final String PERSONAS_FOLDER = "personas";
    /**
     * The sub-folders of the parent folder, and the only type each one accepts. jContent reads the
     * restriction from {@code j:contributeTypes} of the mixin {@code jmix:contributeMode}: its create
     * menu and its paste offer that type only.
     */
    static final Map<String, String> SUB_FOLDERS = Map.of(
            "voice", "efnt:brandVoice",
            PERSONAS_FOLDER, "efnt:persona",
            "rules", RULE_TYPE,
            "glossary", "efnt:glossaryTerm",
            "skills", "efnt:skill");
    /** The sub-folders in the order the setup creates them. */
    static final List<String> SUB_FOLDER_NAMES = List.of("voice", PERSONAS_FOLDER, "rules", "glossary", "skills");
    static final String RESTRICTIONS = "jmix:contributeMode";
    static final String RESTRICTED_TYPES = "j:contributeTypes";
    static final List<String> ACTIVATIONS = List.of("auto", "manual", "pinned-only");
    static final List<String> CONSUMERS = List.of("chat", "review", "ckeditor", "translation", "mcp");
    /** The actions a skill may run under. Publication is never one: a skill never publishes. */
    static final List<String> ACTIONS = List.of("find", "edit", "proofread", "translate", "audit", "create");

    private ImportWriter() {
    }

    public static ObjectNode apply(JCRSessionWrapper session, String siteKey, ImportPlan plan, String sourceRef,
                                   ObjectMapper mapper) throws RepositoryException {
        JCRNodeWrapper base = ensureStructure(session, siteKey);
        ObjectNode report = mapper.createObjectNode();
        report.put("folder", base.getPath());
        report.put("language", session.getLocale() == null ? null : session.getLocale().toString());
        ArrayNode written = report.putArray("written");

        if (plan.voice != null) {
            JCRNodeWrapper voice = node(base.getNode("voice"), plan.voice.name, "efnt:brandVoice");
            voice.setProperty("jcr:title", plan.voice.title);
            voice.setProperty("body", plan.voice.body);
            written.add(voice.getPath());
        }
        if (!plan.personas.isEmpty()) {
            JCRNodeWrapper personas = base.getNode(PERSONAS_FOLDER);
            for (ImportPlan.TextItem item : plan.personas) {
                JCRNodeWrapper persona = node(personas, item.name, "efnt:persona");
                persona.setProperty("jcr:title", item.title);
                persona.setProperty("body", item.body);
                persona.setProperty("isDefault", item.isDefault);
                written.add(persona.getPath());
            }
        }
        if (!plan.rules.isEmpty()) {
            JCRNodeWrapper rulesFolder = base.getNode("rules");
            Map<String, List<WritingRule>> bySource = new LinkedHashMap<>();
            for (ImportPlan.RuleItem item : plan.rules) {
                bySource.computeIfAbsent(item.source, key -> new ArrayList<>()).add(item.rule);
            }
            for (Map.Entry<String, List<WritingRule>> source : bySource.entrySet()) {
                // A new import of a rule file replaces the rules of its last import. The rules sit
                // in the rules folder itself, which accepts rules only, and carry their file.
                String slug = ImportPlan.slug(source.getKey());
                removeRulesOf(rulesFolder, source.getKey(), slug);
                int index = 0;
                for (WritingRule rule : source.getValue()) {
                    JCRNodeWrapper node = rulesFolder.addNode(String.format("%s-%03d", slug, ++index), RULE_TYPE);
                    node.setProperty("source", source.getKey());
                    node.setProperty("kind", rule.kind().name().toLowerCase(Locale.ROOT));
                    node.setProperty("pattern", rule.pattern());
                    node.setProperty("matchMode", rule.matchMode().name().toLowerCase(Locale.ROOT));
                    if (rule.replacement() != null) {
                        node.setProperty("replacement", rule.replacement());
                    }
                    if (rule.language() != null) {
                        node.setProperty("ruleLanguage", rule.language());
                    }
                    node.setProperty("severity", rule.severity().name().toLowerCase(Locale.ROOT));
                    if (rule.justification() != null) {
                        node.setProperty("justification", rule.justification());
                    }
                    if (!rule.wrongExamples().isEmpty()) {
                        node.setProperty("wrongExamples", rule.wrongExamples().toArray(new String[0]));
                    }
                    if (!rule.rightExamples().isEmpty()) {
                        node.setProperty("rightExamples", rule.rightExamples().toArray(new String[0]));
                    }
                    setOptional(node, "owner", rule.owner());
                    setDay(node, "decidedOn", rule.decidedOn());
                    setDay(node, "validFrom", rule.validFrom());
                    setDay(node, "validUntil", rule.validUntil());
                }
                written.add(rulesFolder.getPath() + " (" + index + " rules of " + source.getKey() + ")");
            }
        }
        if (!plan.glossary.isEmpty()) {
            JCRNodeWrapper glossaryFolder = base.getNode("glossary");
            Map<String, List<GlossaryTerm>> bySource = new LinkedHashMap<>();
            for (ImportPlan.TermItem item : plan.glossary) {
                bySource.computeIfAbsent(item.source, key -> new ArrayList<>()).add(item.term);
            }
            for (Map.Entry<String, List<GlossaryTerm>> source : bySource.entrySet()) {
                // A new import of a glossary file replaces the terms of its last import.
                String slug = ImportPlan.slug(source.getKey());
                removeOfSource(glossaryFolder, "efnt:glossaryTerm", source.getKey());
                int index = 0;
                for (GlossaryTerm term : source.getValue()) {
                    JCRNodeWrapper node = glossaryFolder.addNode(String.format("%s-%04d", slug, ++index), "efnt:glossaryTerm");
                    node.setProperty("source", source.getKey());
                    node.setProperty("term", term.term());
                    node.setProperty("termLanguage", term.termLanguage());
                    node.setProperty("translation", term.translation());
                    node.setProperty("translationLanguage", term.translationLanguage());
                    if (term.note() != null) {
                        node.setProperty("note", term.note());
                    }
                    if (!term.termForms().isEmpty()) {
                        node.setProperty("termForms", term.termForms().toArray(new String[0]));
                    }
                    if (!term.translationForms().isEmpty()) {
                        node.setProperty("translationForms", term.translationForms().toArray(new String[0]));
                    }
                    if (term.definition() != null) {
                        node.setProperty("definition", term.definition());
                    }
                    node.setProperty("status", term.status().name().toLowerCase(Locale.ROOT));
                }
                written.add(glossaryFolder.getPath() + " (" + index + " terms of " + source.getKey() + ")");
            }
        }
        session.save();

        if (!plan.skills.isEmpty()) {
            JCRNodeWrapper skills = base.getNode("skills");
            for (ImportPlan.SkillItem item : plan.skills) {
                written.add(writeSkill(skills, item, sourceRef, mapper).getPath());
                // One save a skill keeps the memory of a large import low.
                session.save();
            }
        }
        report.put("skills", plan.skills.size());
        report.put("personas", plan.personas.size());
        report.put("rules", plan.rules.size());
        report.put("glossary", plan.glossary.size());
        report.put("voice", plan.voice == null ? null : plan.voice.name);
        report.set("warnings", mapper.valueToTree(plan.warnings));
        report.set("skipped", mapper.valueToTree(plan.skipped));
        return report;
    }

    private static JCRNodeWrapper writeSkill(JCRNodeWrapper parent, ImportPlan.SkillItem item, String sourceRef,
                                             ObjectMapper mapper) throws RepositoryException {
        SkillDocument document = item.document;
        JCRNodeWrapper skill = node(parent, document.name, "efnt:skill");
        removeChildren(skill, "efnt:skillFile");
        skill.setProperty("description", document.description);
        skill.setProperty("body", document.body.isEmpty() ? " " : document.body);
        skill.setProperty("activation", known(ACTIVATIONS, document.metadata.get(JahiaSkillKeys.ACTIVATION),
                item.activation()));
        List<String> consumers = document.metadataList(JahiaSkillKeys.CONSUMERS);
        skill.setProperty("consumers", (!consumers.isEmpty() && CONSUMERS.containsAll(consumers) ? consumers
                : item.consumers()).toArray(new String[0]));
        skill.setProperty("assistantAction", known(ACTIONS, document.metadata.get(JahiaSkillKeys.ACTION),
                item.action()));
        setOptional(skill, "license", document.license);
        setOptional(skill, "compatibility", document.compatibility);
        setOptional(skill, "allowedTools", document.allowedTools);
        skill.setProperty(JcrSkills.METADATA, metadataValues(document, mapper).toArray(new String[0]));
        skill.setProperty("origin", "file");
        skill.setProperty("sourcePath", item.sourcePath);
        setOptional(skill, "sourceRef", sourceRef);
        skill.setProperty("importedAt", Calendar.getInstance());
        int index = 0;
        for (Map.Entry<String, String> file : item.files.entrySet()) {
            JCRNodeWrapper child = skill.addNode(String.format("file-%03d", ++index), "efnt:skillFile");
            child.setProperty("relativePath", file.getKey());
            child.setProperty("text", file.getValue());
        }
        return skill;
    }

    /**
     * A key of the archive replaces the option of the request only with a value that Jahia knows. An
     * absent key keeps the option: {@code List.of(...).contains(null)} throws.
     */
    static String known(List<String> knownValues, String value, String option) {
        return value != null && knownValues.contains(value) ? value : option;
    }

    /**
     * The keys of {@code metadata} as {@code key=value}. A list or a map, such as the examples of
     * {@code jahia-in-scope}, is written as JSON, which {@link JcrSkills} reads back.
     */
    static List<String> metadataValues(SkillDocument document, ObjectMapper mapper) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : document.rawMetadata.entrySet()) {
            Object value = entry.getValue();
            String text;
            if (value instanceof Collection || value instanceof Map) {
                try {
                    text = mapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    text = SkillDocument.string(value);
                }
            } else {
                text = value == null ? "" : String.valueOf(value);
            }
            values.add(entry.getKey() + "=" + text);
        }
        return values;
    }

    private static void setOptional(JCRNodeWrapper node, String property, String value) throws RepositoryException {
        if (value != null && !value.isBlank()) {
            node.setProperty(property, value);
        }
    }

    /** A day as jContent stores the value of a date picker: midnight UTC. */
    private static void setDay(JCRNodeWrapper node, String property, LocalDate day) throws RepositoryException {
        if (day != null) {
            node.setProperty(property, GregorianCalendar.from(day.atStartOfDay(ZoneOffset.UTC)));
        }
    }

    /**
     * Creates the parent folder of the site and its sub-folders when they are missing, and sets the
     * types that each one accepts: the parent the types of the frame, each sub-folder its own type.
     * Saves the session. Returns the parent folder.
     */
    public static JCRNodeWrapper ensureStructure(JCRSessionWrapper session, String siteKey) throws RepositoryException {
        JCRNodeWrapper contents = session.getNode("/sites/" + siteKey + "/contents");
        // The parent accepts the types of the frame and nothing else. A restriction to folders
        // only would do nothing: jContent intersects it with the content types that a folder holds,
        // finds none, and offers every type. A new sub-folder inherits the restriction of the parent.
        List<String> types = new ArrayList<>();
        for (String name : SUB_FOLDER_NAMES) {
            types.add(SUB_FOLDERS.get(name));
        }
        JCRNodeWrapper base = restricted(folder(contents, FOLDER), types.toArray(new String[0]));
        for (String name : SUB_FOLDER_NAMES) {
            restricted(folder(base, name), SUB_FOLDERS.get(name));
        }
        session.save();
        return base;
    }

    private static JCRNodeWrapper restricted(JCRNodeWrapper folder, String... types) throws RepositoryException {
        if (!folder.isNodeType(RESTRICTIONS)) {
            folder.addMixin(RESTRICTIONS);
        }
        folder.setProperty(RESTRICTED_TYPES, types);
        return folder;
    }

    /**
     * Removes the rules of the last import of a file: the rules that name it as their source, and
     * the sub-folder that a former version of the import wrote for that file.
     */
    private static void removeRulesOf(JCRNodeWrapper rulesFolder, String source, String slug) throws RepositoryException {
        List<JCRNodeWrapper> old = new ArrayList<>();
        JCRNodeIteratorWrapper children = rulesFolder.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            boolean ruleOfFile = child.isNodeType(RULE_TYPE) && source.equals(child.getPropertyAsString("source"));
            boolean formerFolder = child.isNodeType("jnt:contentFolder") && child.getName().equals(slug);
            if (ruleOfFile || formerFolder) {
                old.add(child);
            }
        }
        for (JCRNodeWrapper child : old) {
            child.remove();
        }
    }

    /** Removes the nodes of a type that name the file as their source. */
    private static void removeOfSource(JCRNodeWrapper folder, String type, String source) throws RepositoryException {
        List<JCRNodeWrapper> old = new ArrayList<>();
        JCRNodeIteratorWrapper children = folder.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(type) && source.equals(child.getPropertyAsString("source"))) {
                old.add(child);
            }
        }
        for (JCRNodeWrapper child : old) {
            child.remove();
        }
    }

    static JCRNodeWrapper folder(JCRNodeWrapper parent, String name) throws RepositoryException {
        return parent.hasNode(name) ? parent.getNode(name) : parent.addNode(name, "jnt:contentFolder");
    }

    private static JCRNodeWrapper node(JCRNodeWrapper parent, String name, String type) throws RepositoryException {
        if (parent.hasNode(name)) {
            JCRNodeWrapper existing = parent.getNode(name);
            if (existing.isNodeType(type)) {
                return existing;
            }
            throw new RepositoryException(existing.getPath() + " exists and is not a " + type);
        }
        return parent.addNode(name, type);
    }

    private static void removeChildren(JCRNodeWrapper parent, String type) throws RepositoryException {
        List<JCRNodeWrapper> old = new ArrayList<>();
        JCRNodeIteratorWrapper children = parent.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
            if (child.isNodeType(type)) {
                old.add(child);
            }
        }
        for (JCRNodeWrapper child : old) {
            child.remove();
        }
    }
}
