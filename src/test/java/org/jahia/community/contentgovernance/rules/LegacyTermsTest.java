package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.LegacyTerms.FrameTerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTermsTest {

    private static final FrameTerm ACCESS_TOKEN = new FrameTerm("access token", "en", List.of("access tokens"),
            "jeton d'accès", "fr", List.of("jetons d'accès"), false);
    private static final FrameTerm AUTH_TOKEN = new FrameTerm("access token", "en", List.of(),
            "jeton d'authentification", "fr", List.of("jetons d'authentification"), true);

    @Test
    void aComplianceTermIsASeriousRuleOfTheLanguageOfItsGlossary() {
        Map<String, Object> values = LegacyTerms.complianceRule("compliance.free", "free", "word", null, "en");

        assertThat(values).containsEntry(RuleValues.RULE_ID, "compliance.free")
                .containsEntry(RuleValues.EVALUATOR, ContentRule.PATTERN)
                .containsEntry(RuleValues.SUBDOMAINS, List.of("terms"))
                .containsEntry(RuleValues.SEVERITY, "serious")
                .containsEntry(RuleValues.KIND, "forbidden")
                .containsEntry(RuleValues.PATTERN, "free")
                .containsEntry(RuleValues.MATCH_MODE, "term")
                .containsEntry(RuleValues.RULE_LANGUAGE, "en")
                .doesNotContainKey(RuleValues.REPLACEMENT);
        assertThat(RuleValues.toRule(values).enabled()).isTrue();
    }

    @Test
    void aComplianceTermWithAnAlternativeIsAReplaceRule() {
        Map<String, Object> values = LegacyTerms.complianceRule("compliance.click-here", "click here", "word",
                "Read the report", "en");

        assertThat(values).containsEntry(RuleValues.KIND, "replace").containsEntry(RuleValues.REPLACEMENT, "Read the report");
    }

    @Test
    void theMatchModeOfAComplianceTermBecomesTheStarsOfTheTerm() {
        assertThat(LegacyTerms.complianceRule("c.a", "guarant", "prefix", null, "en")).containsEntry(RuleValues.PATTERN, "guarant*");
        assertThat(LegacyTerms.complianceRule("c.b", "free", "anywhere", null, "en")).containsEntry(RuleValues.PATTERN, "*free*");
        assertThat(LegacyTerms.complianceRule("c.c", " free ", "unknown", null, null))
                .containsEntry(RuleValues.PATTERN, "free").doesNotContainKey(RuleValues.RULE_LANGUAGE);
    }

    @Test
    void anApprovedTranslationIsAReplaceRuleOfTheLanguageOfTheTranslation() {
        Map<String, Object> values = LegacyTerms.glossaryRule("glossary.access-token", ACCESS_TOKEN, null);

        assertThat(values).containsEntry(RuleValues.SUBDOMAINS, List.of("glossary"))
                .containsEntry(RuleValues.SEVERITY, "moderate")
                .containsEntry(RuleValues.KIND, "replace")
                .containsEntry(RuleValues.RULE_LANGUAGE, "fr")
                .containsEntry(RuleValues.PATTERN, "access token")
                .containsEntry(RuleValues.PATTERN_FORMS, List.of("access tokens"))
                .containsEntry(RuleValues.PATTERN_LANGUAGE, "en")
                .containsEntry(RuleValues.REPLACEMENT, "jeton d'accès")
                .containsEntry(RuleValues.REPLACEMENT_FORMS, List.of("jetons d'accès"));
    }

    @Test
    void aForbiddenTranslationIsReplacedWithTheApprovedOneOfItsTerm() {
        Map<String, Object> values = LegacyTerms.glossaryRule("glossary.auth", AUTH_TOKEN, ACCESS_TOKEN);

        assertThat(values).containsEntry(RuleValues.KIND, "replace")
                .containsEntry(RuleValues.RULE_LANGUAGE, "fr")
                .containsEntry(RuleValues.PATTERN, "jeton d'authentification")
                .containsEntry(RuleValues.PATTERN_FORMS, List.of("jetons d'authentification"))
                .containsEntry(RuleValues.REPLACEMENT, "jeton d'accès")
                .containsEntry(RuleValues.REPLACEMENT_FORMS, List.of("jetons d'accès"))
                .doesNotContainKey(RuleValues.PATTERN_LANGUAGE);
    }

    @Test
    void aForbiddenTranslationWithNoApprovedOneIsAForbiddenRule() {
        Map<String, Object> values = LegacyTerms.glossaryRule("glossary.auth", AUTH_TOKEN, null);

        assertThat(values).containsEntry(RuleValues.KIND, "forbidden")
                .containsEntry(RuleValues.PATTERN, "jeton d'authentification")
                .doesNotContainKey(RuleValues.REPLACEMENT);
    }

    @Test
    void aTermIsTheSameForAnyCaseAndAnyRegionOfItsLanguages() {
        FrameTerm upper = new FrameTerm("Access Token", "en_US", List.of(), "jeton", "fr-FR", List.of(), false);

        assertThat(upper.key()).isEqualTo(ACCESS_TOKEN.key());
    }

    @Test
    void theNewPropertiesComeLastSoThatTheDigestOfARuleThatLacksThemStaysTheSame() {
        // The digest reads the keys in order and skips an absent one: a seeded rule keeps its fingerprint.
        assertThat(RuleValues.KEYS.subList(RuleValues.KEYS.size() - 9, RuleValues.KEYS.size()))
                .containsExactly(RuleValues.PATTERN_FORMS, RuleValues.REPLACEMENT_FORMS, RuleValues.PATTERN_LANGUAGE,
                        RuleValues.TITLE, RuleValues.LABEL, RuleValues.SUBDOMAINS, RuleValues.SITES, RuleValues.RUNTIME,
                        RuleValues.CHECK_TYPE);
    }

    @Test
    void aTermThatAlreadyHasStarsGetsTheStarsOfItsMatchModeOnce() {
        assertThat(LegacyTerms.complianceRule("c.a", "guarant*", "prefix", null, "en")).containsEntry(RuleValues.PATTERN, "guarant*");
        assertThat(LegacyTerms.complianceRule("c.b", "*free*", "anywhere", null, "en")).containsEntry(RuleValues.PATTERN, "*free*");
    }
}
