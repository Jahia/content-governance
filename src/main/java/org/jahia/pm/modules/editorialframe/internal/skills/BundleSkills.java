/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skills that modules ship. A bundle opts in with the manifest header {@code Jahia-Skills},
 * whose value names the folder of its skills, such as {@code skills}. Each folder of that folder
 * that holds a {@code SKILL.md} is a skill. These skills are read only: a site changes one by
 * creating a skill of the same name, which hides it.
 */
public final class BundleSkills implements BundleTrackerCustomizer<Long> {

    private static final Logger logger = LoggerFactory.getLogger(BundleSkills.class);
    public static final String HEADER = "Jahia-Skills";

    private final Map<Long, List<LoadedSkill>> byBundle = new ConcurrentHashMap<>();
    private final Map<Long, String> moduleOf = new ConcurrentHashMap<>();
    private BundleTracker<Long> tracker;

    public void open(BundleContext context) {
        tracker = new BundleTracker<>(context, Bundle.ACTIVE, this);
        tracker.open();
    }

    public void close() {
        if (tracker != null) {
            tracker.close();
        }
        byBundle.clear();
        moduleOf.clear();
    }

    /**
     * The skills of the modules in the list, such as the modules installed on a site, in the order
     * the modules started. On a name clash, the reader keeps the first one.
     */
    public List<LoadedSkill> forModules(Collection<String> modules) {
        List<LoadedSkill> skills = new ArrayList<>();
        for (Map.Entry<Long, List<LoadedSkill>> entry : new TreeMap<>(byBundle).entrySet()) {
            if (modules.contains(moduleOf.get(entry.getKey()))) {
                skills.addAll(entry.getValue());
            }
        }
        return skills;
    }

    /** The skills of every active module, in the order the modules started. */
    public List<LoadedSkill> all() {
        List<LoadedSkill> skills = new ArrayList<>();
        new TreeMap<>(byBundle).values().forEach(skills::addAll);
        return skills;
    }

    @Override
    public Long addingBundle(Bundle bundle, BundleEvent event) {
        String folder = bundle.getHeaders().get(HEADER);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        List<LoadedSkill> skills = load(bundle, folder.trim().replaceAll("^/+|/+$", ""));
        byBundle.put(bundle.getBundleId(), skills);
        moduleOf.put(bundle.getBundleId(), bundle.getSymbolicName());
        logger.info("Read {} skills from the module {}", skills.size(), bundle.getSymbolicName());
        return bundle.getBundleId();
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, Long id) {
        // A started bundle keeps its entries.
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, Long id) {
        byBundle.remove(id);
        moduleOf.remove(id);
    }

    static List<LoadedSkill> load(Bundle bundle, String folder) {
        Enumeration<URL> found = bundle.findEntries(folder, "SKILL.md", true);
        List<LoadedSkill> skills = new ArrayList<>();
        if (found == null) {
            return skills;
        }
        TreeSet<String> directories = new TreeSet<>();
        while (found.hasMoreElements()) {
            String path = found.nextElement().getPath();
            directories.add(path.substring(0, path.length() - "SKILL.md".length()));
        }
        List<String> roots = new ArrayList<>();
        for (String directory : directories) {
            // A SKILL.md inside another skill, such as a sub-agent, is a file of that skill.
            if (roots.stream().noneMatch(directory::startsWith)) {
                roots.add(directory);
            }
        }
        for (String root : roots) {
            try {
                skills.add(loadOne(bundle, root));
            } catch (IOException | IllegalArgumentException e) {
                logger.warn("The skill {} of the module {} is skipped: {}", root, bundle.getSymbolicName(), e.getMessage());
            }
        }
        return skills;
    }

    private static LoadedSkill loadOne(Bundle bundle, String root) throws IOException {
        String trimmed = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String folderName = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        SkillDocument document = SkillDocument.parse(read(bundle.getEntry(root + "SKILL.md")), folderName);
        Map<String, String> files = new LinkedHashMap<>();
        Enumeration<URL> entries = bundle.findEntries(root, "*", true);
        while (entries != null && entries.hasMoreElements()) {
            URL entry = entries.nextElement();
            String path = entry.getPath();
            if (path.endsWith("/")) {
                continue;
            }
            String relative = path.substring(root.length());
            if ("SKILL.md".equals(relative) || !SkillPaths.isSafeRelative(relative) || !SkillPaths.isKept(relative)) {
                continue;
            }
            files.put(relative, read(entry));
        }
        String activation = document.metadata.getOrDefault(JahiaSkillKeys.ACTIVATION, "manual");
        List<String> consumers = document.metadataList(JahiaSkillKeys.CONSUMERS);
        if (consumers.isEmpty()) {
            consumers = List.of("chat", "mcp");
        }
        String action = document.metadata.getOrDefault(JahiaSkillKeys.ACTION, "find");
        String source = bundle.getSymbolicName() + ":" + trimmed;
        for (String warning : document.warnings) {
            logger.info("The skill {}: {}", source, warning);
        }
        SkillSummary summary = new SkillSummary(document.name, document.description, "module", "module",
                activation, consumers, action, source, List.of(), document.label, document.tools, document.coverage);
        return new LoadedSkill(summary, document.body, files);
    }

    private static String read(URL url) throws IOException {
        if (url == null) {
            throw new IOException("Missing entry");
        }
        try (InputStream in = url.openStream()) {
            byte[] bytes = in.readNBytes(SkillPaths.MAX_FILE_CHARS + 1);
            if (bytes.length > SkillPaths.MAX_FILE_CHARS) {
                throw new IOException("The file " + url.getPath() + " is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
