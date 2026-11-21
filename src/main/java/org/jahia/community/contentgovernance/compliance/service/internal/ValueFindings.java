package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The findings of the rules of Compliance on one value of a property, through the check registry, placed in
 * the text of the value. Example: the rich text {@code <p>Click <b>here</b></p>} gives the text "Click here"
 * and the finding of the rule {@code compliance.click-here} at 0 to 10.
 *
 * @param text     the value as a reader reads it
 * @param findings the findings, in the order of the text; a finding that the text does not hold comes last
 * @param capped   true when a rule gave as many findings as the registry keeps: the value may break it in more
 *                 places
 */
record ValueFindings(String text, List<PlacedFinding> findings, boolean capped) {

    /**
     * @param checks   the checks that apply the rules
     * @param rules    the rules of Compliance of the site
     * @param path     the path of the node that holds the value
     * @param language the language of the value, such as {@code fr}
     * @param value    the value
     * @param rich     true for a rich text, whose value is HTML
     * @return the findings of the value
     */
    static ValueFindings of(CheckRegistry checks, ComplianceRules rules, String path, String language, String value,
                            boolean rich) {
        String text = rich ? PlainText.fromHtml(value) : PlainText.collapse(value);
        // The checks read the value as HTML, which the check pattern reads back as this text
        CheckInput input = CheckInput.value(path, language, rich ? value : PlainText.toHtml(value));
        List<CheckRegistry.Checked> checked = checks.runRules(rules.site(), input, rules.ids());
        List<PlacedFinding> placed = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        boolean capped = false;
        for (CheckRegistry.Checked one : checked) {
            placed.add(PlacedFinding.of(text, one.rule(), one.finding()));
            capped |= counts.merge(one.rule().id(), 1, Integer::sum) >= CheckRegistry.MAX_FINDINGS;
        }
        placed.sort(Comparator.comparingInt(PlacedFinding::order).thenComparingInt(PlacedFinding::end));
        return new ValueFindings(text, List.copyOf(placed), capped);
    }
}
