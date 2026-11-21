package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
                domain: seo
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
                domain: writing
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
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\ndomain: seo\nseverity: minor\nminimun: 3\n---\n", "a.b.md"))
                .hasMessageContaining("minimun");
        assertThatThrownBy(() -> RuleFile.parse("---\nseverity: minor\n---\n", "a.b.md"))
                .hasMessageContaining("evaluator");
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\ndomain: seo\nseverity: high\n---\n", "a.b.md"))
                .hasMessageContaining("severity");
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\ndomain: seo\nseverity: minor\nminimum: 1\n---\n", "a.b.md"))
                .hasMessageContaining("unit");
        assertThatThrownBy(() -> RuleFile.parse("---\nid: a/b\nevaluator: threshold\ndomain: seo\nseverity: minor\n---\n", "x.md"))
                .hasMessageContaining("a/b");
        assertThatThrownBy(() -> RuleFile.parse("No frontmatter", "a.b.md")).hasMessageContaining("frontmatter");
    }

    @Test
    @DisplayName("a pattern rule with no pattern is refused")
    void patternRuleWithoutPattern_isRefused() {
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: pattern\ndomain: writing\nseverity: minor\n---\n", "a.b.md"))
                .hasMessageContaining("no pattern");
    }

    @Test
    @DisplayName("a file with no domain, or a domain that no tab shows, is refused")
    void domain_isRequiredAndKnown() {
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\nseverity: minor\n---\n", "seo.title.md"))
                .hasMessageContaining("names no domain");
        assertThatThrownBy(() -> RuleFile.parse("---\nevaluator: threshold\ndomain: html\nseverity: minor\n---\n", "html.x.md"))
                .hasMessageContaining("unknown domain html");
    }

    @Test
    @DisplayName("a rule gives its title with plural forms, its label and its advice in each language")
    void texts_byLanguage() {
        RuleFile rule = RuleFile.parse("""
                ---
                id: html.imageAltMissing
                evaluator: html.imageAltMissing
                domain: accessibility
                severity: critical
                label:
                  en: Text alternatives of images
                  fr: Alternatives textuelles des images
                title:
                  en:
                    one: "{{count}} image has no text alternative"
                    other: "{{count}} images have no text alternative"
                  fr: "{{count}} image(s) sans alternative textuelle"
                advice:
                  fr: Donnez à chaque image un attribut alt.
                ---
                Give every image an alt attribute.
                """, "html.imageAltMissing.md");

        assertThat(rule.values()).containsEntry(RuleValues.LABEL, "Text alternatives of images")
                .containsEntry(RuleValues.TITLE, "one: {{count}} image has no text alternative\nother: {{count}} images have no text alternative")
                .containsEntry(RuleValues.ADVICE, "Give every image an alt attribute.");
        assertThat(rule.values()).containsEntry(RuleValues.TRANSLATIONS, Map.of("fr", Map.of(
                RuleValues.LABEL, "Alternatives textuelles des images",
                RuleValues.TITLE, "{{count}} image(s) sans alternative textuelle",
                RuleValues.ADVICE, "Donnez à chaque image un attribut alt.")));
        ContentRule applied = RuleValues.toRule(rule.values());
        assertThat(applied.text(RuleValues.LABEL, "fr")).isEqualTo("Alternatives textuelles des images");
        assertThat(applied.text(RuleValues.LABEL, "de")).isEqualTo("Text alternatives of images");
        assertThat(RuleValues.pluralForms(applied.text(RuleValues.TITLE, "en")))
                .containsEntry("one", "{{count}} image has no text alternative").containsKey("other");
        assertThat(RuleValues.pluralForms(applied.text(RuleValues.TITLE, "fr")))
                .containsExactlyEntriesOf(Map.of("other", "{{count}} image(s) sans alternative textuelle"));
    }

    @Test
    @DisplayName("an English advice in the frontmatter, a plural label and an unknown plural form are refused")
    void texts_invalid() {
        String head = "---\nevaluator: threshold\ndomain: seo\nseverity: minor\n";
        assertThatThrownBy(() -> RuleFile.parse(head + "advice: Do it.\n---\n", "seo.x.md")).hasMessageContaining("body");
        assertThatThrownBy(() -> RuleFile.parse(head + "label:\n  en:\n    one: x\n---\n", "seo.x.md")).hasMessageContaining("plural");
        assertThatThrownBy(() -> RuleFile.parse(head + "title:\n  en:\n    several: x\n---\n", "seo.x.md")).hasMessageContaining("several");
    }
}
