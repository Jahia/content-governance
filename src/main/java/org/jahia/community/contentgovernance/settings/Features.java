package org.jahia.community.contentgovernance.settings;

import java.util.regex.Pattern;

/**
 * The ids of the features a site switches on or off. A feature id is a panel of the drawer
 * ({@code governance}, {@code chat}), a tab of the governance panel ({@code geo}) or a part of a tab
 * ({@code geo.citations}). The panels and tabs of the module and those that other modules register
 * in the drawer have ids of the same form. The server keeps no list of them: the drawer's registry is
 * the list, and a site stores the ids it switched off. A panel or a tab that a module adds later
 * therefore starts switched on.
 *
 * <p>The ids are part of the stored settings and of the GraphQL API: never rename one.</p>
 */
public final class Features {

    /** The governance panel, which holds the tabs: switched off, every tab is off. */
    public static final String GOVERNANCE = "governance";
    public static final String AUDIT = "audit";
    public static final String GEO = "geo";
    public static final String COMPLIANCE = "compliance";

    /** The most ids a site stores as switched off. */
    public static final int MAX_IDS = 200;

    /** A tab key, then an optional part after a dot: a letter, then letters, digits, {@code _} or {@code -}. */
    private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}(\\.[A-Za-z][A-Za-z0-9_-]{0,63})?$");

    private Features() {
    }

    /** True for an id of the form of a tab ({@code geo}) or of a part of a tab ({@code geo.citations}). */
    public static boolean isValid(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** True for an id of the form of a tab of the drawer, with no part. */
    public static boolean isTab(String id) {
        return isValid(id) && id.indexOf('.') < 0;
    }

    /** The tab of a feature id: the id itself for a tab, the part before the dot for a part of a tab. */
    public static String sectionOf(String id) {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }
}
