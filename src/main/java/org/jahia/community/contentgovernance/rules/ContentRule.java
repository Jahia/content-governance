package org.jahia.community.contentgovernance.rules;

import java.util.List;

/**
 * One rule of the catalog, as it applies to one site.
 *
 * @param id       the id that every finding of the rule carries, such as {@code seo.title}
 * @param domain   the group of the rule, the part of the id before the dot
 * @param severity one of {@link #SEVERITIES}
 * @param enabled  false when the site turned the rule off
 * @param fixer    the role of the person who fixes a finding: editor, developer or administrator
 * @param advice   what to do about a finding, in English
 * @param unit     the unit of the limits, or null when the rule has none
 * @param minimum  the lowest value that passes, or null when the rule has no such limit
 * @param maximum  the highest value that passes, or null when the rule has no such limit
 */
public record ContentRule(String id, String domain, String severity, boolean enabled, String fixer,
                          String advice, String unit, Double minimum, Double maximum) {

    /** The severities of a rule, from the most serious to the least. */
    public static final List<String> SEVERITIES = List.of("critical", "serious", "moderate", "minor");
    /** The roles of the person who fixes a finding. */
    public static final List<String> FIXERS = List.of("editor", "developer", "administrator");

    /**
     * @return the lowest value that passes
     * @throws IllegalStateException when the catalog gives the rule no minimum
     */
    public double min() {
        if (minimum == null) {
            throw new IllegalStateException("The rule " + id + " has no minimum in the catalog");
        }
        return minimum;
    }

    /**
     * @return the highest value that passes
     * @throws IllegalStateException when the catalog gives the rule no maximum
     */
    public double max() {
        if (maximum == null) {
            throw new IllegalStateException("The rule " + id + " has no maximum in the catalog");
        }
        return maximum;
    }

    /** True when {@code value} is inside the limits the rule has. */
    public boolean accepts(double value) {
        return (minimum == null || value >= minimum) && (maximum == null || value <= maximum);
    }

    /**
     * This rule with what a site changed. A null argument keeps the value of this rule, and so does a
     * severity that is not one of {@link #SEVERITIES}.
     */
    ContentRule changedBy(Boolean siteEnabled, String siteSeverity, Double siteMinimum, Double siteMaximum) {
        return new ContentRule(id, domain,
                siteSeverity != null && SEVERITIES.contains(siteSeverity) ? siteSeverity : severity,
                siteEnabled == null ? enabled : siteEnabled,
                fixer, advice, unit,
                minimum != null && siteMinimum != null ? siteMinimum : minimum,
                maximum != null && siteMaximum != null ? siteMaximum : maximum);
    }
}
