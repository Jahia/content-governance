package org.jahia.community.contentgovernance.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sections of the drawer and the tabs of each, by id. A feature id is a section ({@code geo}) or
 * a tab of a section ({@code geo.citations}); a site stores the ids it switched off, so a section or a
 * tab added in a later version starts switched on.
 *
 * <p>The ids are part of the stored settings and of the GraphQL API: never rename one.</p>
 */
public final class Features {

    public static final String AUDIT = "audit";
    public static final String GEO = "geo";
    public static final String COMPLIANCE = "compliance";

    /** Sections in drawer order, each with its tabs in tab order. */
    public static final Map<String, List<String>> SECTIONS;

    static {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put(AUDIT, List.of("accessibility", "seo", "vitals", "readability", "ecodesign", "links", "jahia", "ai"));
        sections.put(GEO, List.of("score", "crawler", "files", "schema", "citations"));
        sections.put(COMPLIANCE, List.of());
        SECTIONS = Collections.unmodifiableMap(sections);
    }

    private Features() {
    }

    /** True for a section id or a {@code section.tab} id this version knows. */
    public static boolean isKnown(String id) {
        if (id == null) {
            return false;
        }
        int dot = id.indexOf('.');
        if (dot < 0) {
            return SECTIONS.containsKey(id);
        }
        List<String> tabs = SECTIONS.get(id.substring(0, dot));
        return tabs != null && tabs.contains(id.substring(dot + 1));
    }

    /** The section of a feature id: the id itself for a section, the part before the dot for a tab. */
    public static String sectionOf(String id) {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }
}
