package org.jahia.community.contentgovernance.settings;

import java.util.regex.Pattern;

/**
 * The ids of the features a site switches on or off in the {@code off} list of its layout. A feature id is a panel of
 * the drawer ({@code governance}, {@code chat}), a domain ({@code quality}), a subdomain ({@code geo.citations}), or
 * a tab that another module adds to the governance panel. The tree of the system site lists the domains and the
 * subdomains, and the registry of the drawer lists the panels.
 *
 * <p>The ids are part of the stored layouts and of the GraphQL API: never rename one.</p>
 */
public final class Features {

    /** The governance panel, which holds the domains: switched off, every domain of the panel is off. */
    public static final String GOVERNANCE = "governance";
    public static final String QUALITY = "quality";
    public static final String GEO = "geo";
    public static final String COMPLIANCE = "compliance";

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
