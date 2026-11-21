package org.jahia.community.contentgovernance.rules;

import java.util.List;

/**
 * The closed list of the domains of a rule. Each domain is one tab or one section of the drawer, so a
 * finding always has a place to show.
 *
 * <ul>
 *   <li>{@code accessibility}, {@code seo}, {@code readability}, {@code links}, {@code health} and
 *       {@code ecodesign}: the tabs of Page quality.</li>
 *   <li>{@code writing}: the Readability tab, for a rule on how a text is written.</li>
 *   <li>{@code geo}: GEO readiness. {@code compliance}: Compliance.</li>
 * </ul>
 */
public final class RuleDomains {

    /** Every domain, in the order of the drawer. */
    public static final List<String> ALL = List.of("accessibility", "seo", "readability", "writing", "links", "health",
            "ecodesign", "geo", "compliance");

    private RuleDomains() {
    }

    /** True when the domain is one of {@link #ALL}. */
    public static boolean known(Object domain) {
        return domain != null && ALL.contains(domain.toString());
    }

    /**
     * The domain of a rule node that names none: the part of its id before the first dot, when that part
     * is a known domain. Example: {@code seo.title} gives {@code seo}, and {@code brand.tone} gives null.
     */
    public static String fromId(String id) {
        String prefix = RuleValues.domainOf(id);
        return known(prefix) ? prefix : null;
    }
}
