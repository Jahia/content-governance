package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.check.spi.Rule;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One rule of the catalog, as it applies to one site.
 *
 * @param id        the id that every finding of the rule carries, such as {@code seo.title}
 * @param subdomains the ids of the subdomains of the rule, such as {@code seo} and {@code geo.score}: the findings
 *                  of the rule show in each; empty when the rule names none and no default applies
 * @param evaluator what checks the rule: one of {@link #EVALUATORS}, or the id of a check that a module registers
 * @param severity  one of {@link #SEVERITIES}
 * @param blocking  true when a finding of the rule stops the publication of the content
 * @param enabled   false when the site turned the rule off
 * @param fixer     the role of the person who fixes a finding: editor, developer or administrator
 * @param advice    what to do about a finding, in English; for a {@code model} rule, the criterion that the model checks
 * @param unit      the unit of the limits, or null when the rule has none
 * @param minimum   the lowest value that passes, or null when the rule has no such limit
 * @param maximum   the highest value that passes, or null when the rule has no such limit
 * @param values    every value of the rule, keyed as {@link RuleValues} keys them
 * @param folder    the path of the folder that holds the node of the rule, such as
 *                  {@code /sites/digitall/contents/compliance-glossaries/legal}, or null for a shipped rule
 *                  that the site has no node of
 * @param placed    true when one of the subdomains is a subdomain of the site, under a domain of the site: only
 *                  then does a finding have a place, and only then does the rule run
 */
public record ContentRule(String id, List<String> subdomains, String evaluator, String severity, boolean blocking,
                          boolean enabled, String fixer, String advice, String unit, Double minimum, Double maximum,
                          Map<String, Object> values, String folder, boolean placed) implements Rule {

    /** The severities of a rule, from the most serious to the least. */
    public static final List<String> SEVERITIES = List.of("critical", "serious", "moderate", "minor");
    /** The roles of the person who fixes a finding. */
    public static final List<String> FIXERS = List.of("editor", "developer", "administrator");
    /** A text matches the pattern of the rule. */
    public static final String PATTERN = "pattern";
    /** A measured value is inside the minimum and the maximum of the rule. */
    public static final String THRESHOLD = "threshold";
    /** Something that the rule names is present. */
    public static final String PRESENCE = "presence";
    /** A model judges the text against the advice of the rule. Only a module that calls a model registers it. */
    public static final String MODEL = "model";
    /** The evaluators that a rule names by a word. Any other evaluator is the id of a check of its own. */
    public static final List<String> EVALUATORS = List.of(PATTERN, THRESHOLD, PRESENCE, MODEL);
    /** The check of the rule runs on the server: the default. */
    public static final String SERVER = "server";
    /** The check of the rule runs in the browser, on the preview of the page, where the drawer registers it. */
    public static final String BROWSER = "browser";
    /** The values of {@link RuleValues#RUNTIME}. */
    public static final List<String> RUNTIMES = List.of(SERVER, BROWSER);

    public ContentRule {
        subdomains = List.copyOf(subdomains);
        values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    public Object value(String property) {
        return values.get(property);
    }

    /**
     * A text of the rule in a language, or in English when the rule has none in that language.
     *
     * @param key      {@link RuleValues#TITLE}, {@link RuleValues#LABEL} or {@link RuleValues#ADVICE}
     * @param language a language such as {@code fr}, or null for English
     * @return the text, or null when the rule has none
     */
    public String text(String key, String language) {
        if (language != null && !"en".equals(language)
                && values.get(RuleValues.TRANSLATIONS) instanceof Map<?, ?> translations
                && translations.get(language) instanceof Map<?, ?> texts
                && texts.get(key) instanceof String text && !text.isBlank()) {
            return text;
        }
        Object english = values.get(key);
        return english == null || english.toString().isBlank() ? null : english.toString();
    }

    /** The keys of the sites that the rule applies to, or {@value RuleValues#ALL_SITES}. */
    public List<String> sites() {
        List<String> sites = RuleValues.strings(values, RuleValues.SITES);
        return sites.isEmpty() ? List.of(RuleValues.ALL_SITES) : sites;
    }

    /**
     * True when the rule states {@code runtime: browser}: the drawer runs its check on the preview of the page,
     * and the server never runs it.
     */
    public boolean inBrowser() {
        return BROWSER.equals(values.get(RuleValues.RUNTIME));
    }

    /**
     * @param path the path of the folder that holds the node of the rule
     * @return this rule in that folder
     */
    public ContentRule inFolder(String path) {
        return new ContentRule(id, subdomains, evaluator, severity, blocking, enabled, fixer, advice, unit, minimum,
                maximum, values, path, placed);
    }

    /**
     * @param ids         the subdomains of the rule
     * @param inHierarchy true when one of them is a subdomain of the site, under a domain of the site
     * @return this rule with these subdomains
     */
    ContentRule placedIn(List<String> ids, boolean inHierarchy) {
        return new ContentRule(id, ids, evaluator, severity, blocking, enabled, fixer, advice, unit, minimum,
                maximum, values, folder, inHierarchy);
    }

    /**
     * @return the lowest value that passes
     * @throws IllegalStateException when the rule has no minimum
     */
    public double min() {
        if (minimum == null) {
            throw new IllegalStateException("The rule " + id + " has no minimum");
        }
        return minimum;
    }

    /**
     * @return the highest value that passes
     * @throws IllegalStateException when the rule has no maximum
     */
    public double max() {
        if (maximum == null) {
            throw new IllegalStateException("The rule " + id + " has no maximum");
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
        String newSeverity = siteSeverity != null && SEVERITIES.contains(siteSeverity) ? siteSeverity : severity;
        boolean newEnabled = siteEnabled == null ? enabled : siteEnabled;
        Double newMinimum = siteMinimum != null ? siteMinimum : minimum;
        Double newMaximum = siteMaximum != null ? siteMaximum : maximum;
        Map<String, Object> newValues = new HashMap<>(values);
        newValues.put(RuleValues.SEVERITY, newSeverity);
        newValues.put(RuleValues.ENABLED, newEnabled);
        if (newMinimum != null) {
            newValues.put(RuleValues.MINIMUM, newMinimum);
        }
        if (newMaximum != null) {
            newValues.put(RuleValues.MAXIMUM, newMaximum);
        }
        return new ContentRule(id, subdomains, evaluator, newSeverity, blocking, newEnabled, fixer, advice, unit,
                newMinimum, newMaximum, newValues, folder, placed);
    }
}
