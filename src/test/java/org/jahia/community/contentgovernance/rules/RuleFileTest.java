package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleFileTest {

    @Test
    @DisplayName("a threshold rule gives its limits as numbers and its body as the advice")
    void thresholdRule() {
        RuleFile rule = RuleFile.parse("""
                ---
                id: seo.title
                evaluator: threshold
                severity: serious
                unit: characters
                minimum: 30
                maximum: 60
                ---
                Give the page a title.
                """, "seo.title.md");

        assertThat(rule.id()).isEqualTo("seo.title");
        assertThat(rule.values()).containsEntry(RuleValues.MINIMUM, 30.0).containsEntry(RuleValues.MAXIMUM, 60.0)
                .containsEntry(RuleValues.ADVICE, "Give the page a title.")
                .containsEntry(RuleValues.EVALUATOR, "threshold");
    }

    @Test
    @DisplayName("a pattern rule keeps its lists and its days, and takes its id from the file name")
    void patternRule() {
        RuleFile rule = RuleFile.parse("""
                ---
                evaluator: pattern
                severity: moderate
                blocking: true
                kind: replace
                pattern: mail
                replacement: e-mail
                wrongExamples: [Send a mail.]
                validFrom: 2026-11-19
                ---
                Write e-mail.
                """, "acme.mail.md");

        assertThat(rule.id()).isEqualTo("acme.mail");
        assertThat(rule.values()).containsEntry(RuleValues.BLOCKING, true)
                .containsEntry(RuleValues.WRONG_EXAMPLES, List.of("Send a mail."))
                .containsEntry(RuleValues.VALID_FROM, LocalDate.of(2026, 11, 19));
    }

    @Test
    @DisplayName("a file with an unknown key, no evaluator, a bad severity, a limit and no unit, or a bad id is refused")
    void invalidFile_isRefused() {
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\nseverity: minor\nminimun: 3\n---\n", "a.b.md"))
                .hasMessageContaining("minimun");
        assertThatThrownBy(() -> RuleFile.parse("---\nseverity: minor\n---\n", "a.b.md"))
                .hasMessageContaining("evaluator");
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\nseverity: high\n---\n", "a.b.md"))
                .hasMessageContaining("severity");
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\nseverity: minor\nminimum: 1\n---\n", "a.b.md"))
                .hasMessageContaining("unit");
        assertThatThrownBy(() -> RuleFile.parse("---\nid: a/b\nevaluator: threshold\nseverity: minor\n---\n", "x.md"))
                .hasMessageContaining("a/b");
        assertThatThrownBy(() -> RuleFile.parse("No frontmatter", "a.b.md")).hasMessageContaining("frontmatter");
    }

    @Test
    @DisplayName("a pattern rule with no pattern is refused")
    void patternRuleWithoutPattern_isRefused() {
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: pattern\nseverity: minor\n---\n", "a.b.md"))
                .hasMessageContaining("no pattern");
    }
}
