package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class BuiltInChecksTest {

    private static final String PATH = "/sites/digitall/home";

    private static ContentRule rule(Map<String, Object> values) {
        return RuleValues.toRule(values);
    }

    @Test
    @DisplayName("threshold judges the value measured under the id of the rule against its limits")
    void threshold_judgesTheMeasuredFact() {
        ContentRule title = rule(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.EVALUATOR, "threshold",
                RuleValues.SEVERITY, "serious", RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0));
        ThresholdCheck check = new ThresholdCheck();
        CheckInput page = CheckInput.page(PATH, "en", "<html></html>");

        assertThat(check.check(page.withFact("seo.title", 72), title))
                .containsExactly(new Finding("seo.title", "serious", "72", null));
        assertThat(check.check(page.withFact("seo.title", 45), title)).isEmpty();
        assertThat(check.check(page, title)).as("nothing measured, nothing found").isEmpty();
        assertThat(ThresholdCheck.text(0.5)).isEqualTo("0.5");
    }

    @Test
    @DisplayName("threshold gives one finding per place outside the limits when the caller measured the places")
    void threshold_givesAFindingPerPlace() {
        ContentRule sentences = rule(Map.of(RuleValues.RULE_ID, "readability.longSentences", RuleValues.EVALUATOR,
                "threshold", RuleValues.SEVERITY, "minor", RuleValues.MAXIMUM, 25.0));
        ThresholdCheck check = new ThresholdCheck();
        CheckInput page = CheckInput.page(PATH, "en", "<html></html>").withFact("readability.longSentences", 40);
        List<ThresholdCheck.Place> places = List.of(
                new ThresholdCheck.Place(40, "A long one.", null, Map.of("sentence", "A long one.", "words", 40)),
                new ThresholdCheck.Place(10, "A short one.", null, Map.of("sentence", "A short one.", "words", 10)),
                new ThresholdCheck.Place(30, "Another.", "main > p", Map.of("sentence", "Another.", "words", 30)));

        assertThat(check.check(page.withFact(ThresholdCheck.placesOf("readability.longSentences"), places), sentences))
                .containsExactly(
                        new Finding("readability.longSentences", "minor", "40", "A long one.", null,
                                Map.of("sentence", "A long one.", "words", 40)),
                        new Finding("readability.longSentences", "minor", "30", "Another.", "main > p",
                                Map.of("sentence", "Another.", "words", 30)));
        assertThat(check.check(page, sentences)).as("no place measured: one finding for the value")
                .containsExactly(new Finding("readability.longSentences", "minor", "40", null));
    }

    @Test
    @DisplayName("a threshold result carries the values that the caller measured, else the measured number")
    void threshold_resultCarriesTheValues() {
        ContentRule title = rule(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.EVALUATOR, "threshold",
                RuleValues.SEVERITY, "serious", RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0));
        ThresholdCheck check = new ThresholdCheck();
        CheckInput page = CheckInput.page(PATH, "en", "<html></html>").withFact("seo.title", 72);

        CheckResult measured = check.result(page.withValues("seo.title", Map.of("length", 72, "text", "Annual report")), title);
        assertThat(measured.passed()).isFalse();
        assertThat(measured.values()).containsExactlyInAnyOrderEntriesOf(Map.of("length", 72, "text", "Annual report"));
        assertThat(measured.findings()).extracting(Finding::text).containsExactly("72");
        assertThat(check.result(page, title).values()).containsExactlyEntriesOf(Map.of("value", 72));
        assertThat(check.result(CheckInput.page(PATH, "en", ""), title))
                .as("nothing measured: the rule passes with no value")
                .isEqualTo(new CheckResult("seo.title", true, Map.of(), List.of()));
    }

    @Test
    @DisplayName("presence gives a finding for false, 0, a blank text or an empty list, and none for an unmeasured fact")
    void presence_findsWhatIsAbsent() {
        ContentRule canonical = rule(Map.of(RuleValues.RULE_ID, "acme.canonical", RuleValues.EVALUATOR, "presence"));
        PresenceCheck check = new PresenceCheck();
        CheckInput page = CheckInput.page(PATH, "en", "");

        for (Object absent : List.of(false, 0, " ", List.of())) {
            assertThat(check.check(page.withFact("acme.canonical", absent), canonical)).as(String.valueOf(absent)).hasSize(1);
        }
        for (Object present : List.of(true, 2, "https://example.com/", List.of("a"))) {
            assertThat(check.check(page.withFact("acme.canonical", present), canonical)).as(String.valueOf(present)).isEmpty();
        }
        assertThat(check.check(page, canonical)).isEmpty();
    }

    @Test
    @DisplayName("pattern checks the writing rule of the rule node on a value")
    void pattern_checksTheWritingRule() {
        ContentRule robust = rule(Map.of(RuleValues.RULE_ID, "writing.robust", RuleValues.EVALUATOR, "pattern",
                RuleValues.SEVERITY, "moderate", RuleValues.KIND, "forbidden", RuleValues.PATTERN, "robust"));
        PatternCheck check = new PatternCheck();

        List<Finding> found = check.check(CheckInput.value(PATH, "en", "A robust and Robust tool, robustness aside."), robust);

        assertThat(found).extracting(Finding::text).containsExactly("robust", "Robust");
        assertThat(found.get(0).ruleId()).isEqualTo("writing.robust");
        assertThat(found.get(0).context()).contains("A robust and");
    }

    @Test
    @DisplayName("pattern skips a rule with no pattern, an unknown kind, or a validity that ended")
    void pattern_skipsWhatItCannotCheck() {
        PatternCheck check = new PatternCheck();
        CheckInput value = CheckInput.value(PATH, "en", "A robust tool");

        assertThat(check.check(value, rule(Map.of(RuleValues.RULE_ID, "w.none", RuleValues.EVALUATOR, "pattern")))).isEmpty();
        assertThat(check.check(value, rule(Map.of(RuleValues.RULE_ID, "w.kind", RuleValues.EVALUATOR, "pattern",
                RuleValues.PATTERN, "robust", RuleValues.KIND, "bogus")))).isEmpty();
        assertThat(check.check(value, rule(Map.of(RuleValues.RULE_ID, "w.old", RuleValues.EVALUATOR, "pattern",
                RuleValues.PATTERN, "robust", RuleValues.VALID_UNTIL, LocalDate.of(2020, 1, 1))))).isEmpty();
    }

    private static ContentRule term(String id, String pattern) {
        return rule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, "pattern", RuleValues.KIND, "forbidden",
                RuleValues.PATTERN, pattern));
    }

    /** A glossary rule that writes an English term in another language. */
    private static ContentRule glossary(String id, String pattern, String replacement, String language) {
        return rule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, "pattern", RuleValues.KIND, "replace",
                RuleValues.MATCH_MODE, "term", RuleValues.PATTERN, pattern, RuleValues.PATTERN_LANGUAGE, "en",
                RuleValues.REPLACEMENT, replacement, RuleValues.RULE_LANGUAGE, language));
    }

    @Test
    @DisplayName("pattern applies its rules together: a longer term hides a shorter one, a glossary term has forms in other languages")
    void pattern_appliesItsRulesTogether() {
        List<ContentRule> rules = List.of(term("compliance.baggins", "Baggins"),
                term("compliance.sackville", "Sackville-Baggins"),
                glossary("glossary.frodo-fr", "Frodo", "Frodon", "fr"),
                glossary("glossary.frodo-es", "Frodo", "Frodo", "es"));

        List<Finding> found = new PatternCheck().check(CheckInput.value(PATH, "es", "<p>Frodon y <b>Sackville-Baggins</b></p>"),
                rules);

        // In the order of the text, whatever the order of the rules
        assertThat(found).extracting(Finding::ruleId, Finding::text).containsExactly(
                tuple("glossary.frodo-fr", "Frodon"), tuple("compliance.sackville", "Sackville-Baggins"));
        // The place is in the text that a reader reads, and a form of another language names the form to write
        assertThat(found.get(0).values()).containsEntry(Finding.START, 0).containsEntry(Finding.END, 6)
                .containsEntry(Finding.TERM, "Frodon").containsEntry(Finding.REPLACEMENT, "Frodo");
        assertThat(found.get(0).context()).isEqualTo("Frodon y Sackville-Baggins");
        assertThat(found.get(1).values()).containsEntry(Finding.START, 9).containsEntry(Finding.END, 26)
                .containsEntry(Finding.TERM, "Sackville-Baggins").doesNotContainKey(Finding.REPLACEMENT);
    }

    @Test
    @DisplayName("pattern reads inline tags as one word, and gives each place up to the most findings of a rule")
    void pattern_readsTheTextAsAReaderDoes() {
        PatternCheck check = new PatternCheck();
        List<ContentRule> free = List.of(term("compliance.free", "gratuit"));

        assertThat(check.check(CheckInput.value(PATH, "fr", "<p>Livraison <b>gra</b>tuit</p>"), free))
                .extracting(Finding::text).containsExactly("gratuit");
        assertThat(check.check(CheckInput.value(PATH, "fr", "gratuit ".repeat(80)), free))
                .hasSize(CheckRegistry.MAX_FINDINGS);
        // Other rules on the same check give their own findings, not those of the rules before them
        assertThat(check.check(CheckInput.value(PATH, "fr", "gratuit et offert"), List.of(term("compliance.offered", "offert"))))
                .extracting(Finding::ruleId).containsExactly("compliance.offered");
    }

    @Test
    @DisplayName("an input names the site of its path")
    void input_namesItsSite() {
        assertThat(CheckInput.value("/sites/digitall/home/main", "en", "x").siteKey()).isEqualTo("digitall");
        assertThat(CheckInput.site("digitall").siteKey()).isEqualTo("digitall");
        assertThat(CheckInput.value("/modules/x", "en", "x").siteKey()).isNull();
        assertThat(CheckInput.value(null, "en", "x").siteKey()).isNull();
    }
}
