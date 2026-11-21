package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;

/**
 * A finding of the check registry on one value, placed in the text of the value, with what an issue of the
 * Compliance section shows: the term, the replacement and the text around the match. Example: the finding
 * "click here" of the rule {@code compliance.click-here} at 4 to 14 in "Now click here to buy".
 *
 * @param rule    the rule, as the site applies it
 * @param finding the finding
 * @param start   where the finding starts in the text, or -1 when the text does not hold it
 * @param end     where it ends, excluded, or -1
 */
record PlacedFinding(ContentRule rule, Finding finding, int start, int end) {

    /** The place that the check gives, when the text holds the finding there, else the first place of its text. */
    static PlacedFinding of(String text, ContentRule rule, Finding finding) {
        String found = finding.text();
        if (finding.values().get(Finding.START) instanceof Integer start && finding.values().get(Finding.END) instanceof Integer end
                && start >= 0 && start < end && end <= text.length()
                && (found.isEmpty() || end - start == found.length() && text.startsWith(found, start))) {
            return new PlacedFinding(rule, finding, start, end);
        }
        int at = found.isEmpty() ? -1 : text.indexOf(found);
        return at < 0 ? new PlacedFinding(rule, finding, -1, -1) : new PlacedFinding(rule, finding, at, at + found.length());
    }

    /**
     * @param language the language of the labels, such as {@code fr}
     * @return the term that the check names, else the pattern of the rule, else the label of the rule, such as
     * "Prices from the ERP" for a rule whose check finds a price
     */
    String term(String language) {
        Object term = finding.values().get(Finding.TERM);
        if (term == null) {
            term = rule.value(RuleValues.PATTERN);
        }
        if (term == null) {
            term = rule.text(RuleValues.LABEL, language);
        }
        return term == null ? rule.id() : term.toString();
    }

    /** @return what to write instead, as the check names it, else as the rule names it, or null */
    String replacement() {
        Object replacement = finding.values().get(Finding.REPLACEMENT);
        if (replacement == null) {
            replacement = rule.value(RuleValues.REPLACEMENT);
        }
        return replacement == null ? null : replacement.toString();
    }

    /** @return the text around the finding, or the text of the finding alone when the text does not hold it */
    Excerpt excerpt(String text) {
        return start < 0 ? new Excerpt("", finding.text(), "") : Excerpt.around(text, start, end);
    }

    /** @return where the finding starts, and the end of any text for a finding that the text does not hold */
    int order() {
        return start < 0 ? Integer.MAX_VALUE : start;
    }
}
