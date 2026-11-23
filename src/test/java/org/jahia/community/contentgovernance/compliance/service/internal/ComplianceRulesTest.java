package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.PatternCheck;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ComplianceRulesTest {

    private static final String LEGAL = "/sites/acme/contents/compliance-glossaries/legal";
    private static final String BRAND = "/sites/acme/contents/compliance-glossaries/brand";
    private static final String ACME = "/sites/acme/contents/content-insights/acme/rules";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    /** A check of another module, of the scope VALUE, which finds nothing. */
    private static final ContentCheck PRICE = new ContentCheck() {
        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.VALUE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            return List.of();
        }
    };

    private static ContentRule rule(String id, String folder, Map<String, Object> more) {
        Map<String, Object> values = new HashMap<>(Map.of(RuleValues.RULE_ID, id, RuleValues.KIND, "forbidden",
                RuleValues.PATTERN, id.substring(id.indexOf('.') + 1)));
        values.putAll(more);
        // Placed as a site places it: a term with no subdomain goes to terms.
        return ContentRules.base().with(RuleValues.toRule(values)).get(id).inFolder(folder);
    }

    /** A rule of the check acme.erpPrice, which another module registers. */
    private static ContentRule price(String id, String evaluator) {
        return RuleValues.toRule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, evaluator,
                RuleValues.SUBDOMAIN, "writing")).inFolder(ACME);
    }

    private static ComplianceRules of(String language, ContentRule... rules) {
        CheckRegistry checks = new CheckRegistry();
        Registries.bind(checks, new PatternCheck(), ContentRule.PATTERN);
        Registries.bind(checks, PRICE, "acme.erpPrice");
        ContentRules site = ContentRules.base();
        for (ContentRule rule : rules) {
            site = site.with(rule);
        }
        return ComplianceRules.of(site, rule -> checks.runs(rule, ContentCheck.Scope.VALUE), language, DAY,
                path -> new Glossary(path, "uuid-" + path.substring(path.lastIndexOf('/') + 1), "Title", 0));
    }

    @Test
    void keepsTheRulesOfComplianceThatTheRegistryRunsOnAValue() {
        ComplianceRules applied = of("en",
                rule("compliance.free", LEGAL, Map.of(RuleValues.RULE_LANGUAGE, "en")),
                rule("brand.seamless", BRAND, Map.of()),
                rule("glossary.jeton", BRAND, Map.of(RuleValues.RULE_LANGUAGE, "fr")),
                rule("legal.risk", LEGAL, Map.of(RuleValues.KIND, "mandatory")),
                rule("seo.title", null, Map.of(RuleValues.EVALUATOR, "threshold")),
                rule("seo.keyword", BRAND, Map.of()),
                rule("brand.off", BRAND, Map.of(RuleValues.ENABLED, false)),
                rule("brand.later", BRAND, Map.of(RuleValues.VALID_FROM, DAY.plusDays(1))),
                price("acme.erpPrice", "acme.erpPrice"),
                price("acme.tone", "model"));

        // A rule of another language stays: a glossary rule reports a form of its term in any language.
        // A pattern rule of SEO shows in SEO, and a check that no module registers runs nowhere.
        assertThat(applied.ids()).containsExactlyInAnyOrder("compliance.free", "brand.seamless", "glossary.jeton",
                "acme.erpPrice");
        assertThat(applied.applies()).isTrue();
        assertThat(applied.folders()).extracting(Glossary::path, Glossary::termCount)
                .containsExactly(tuple(BRAND, 1), tuple(LEGAL, 1), tuple(ACME, 1));
        assertThat(BRAND).as("compliance-glossaries comes before content-insights").isLessThan(ACME);
    }

    @Test
    void namesEachFolderOnceWithItsRuleCount() {
        ComplianceRules applied = of("en",
                rule("compliance.free", LEGAL, Map.of()),
                rule("compliance.guaranteed", LEGAL, Map.of()),
                rule("brand.seamless", BRAND, Map.of()));

        assertThat(applied.folders()).extracting(Glossary::path, Glossary::uuid, Glossary::termCount)
                .containsExactly(
                        tuple(BRAND, "uuid-brand", 1),
                        tuple(LEGAL, "uuid-legal", 2));
    }

    @Test
    void appliesToNoTextWhenNoRuleOfTheLanguageFits() {
        ComplianceRules applied = of("de", rule("compliance.free", LEGAL, Map.of(RuleValues.RULE_LANGUAGE, "en")));

        assertThat(applied.folders()).isEmpty();
        assertThat(applied.applies()).isFalse();
    }

    @Test
    void appliesWhereAGlossaryRuleOfAnotherLanguageHasAForm() {
        ContentRule frodon = rule("glossary.frodo", BRAND, Map.of(RuleValues.KIND, "replace", RuleValues.PATTERN, "Frodo",
                RuleValues.MATCH_MODE, "term", RuleValues.PATTERN_LANGUAGE, "en", RuleValues.REPLACEMENT, "Frodon",
                RuleValues.RULE_LANGUAGE, "fr"));

        // An English text that holds "Frodon" breaks the rule, but the rule counts in the French folders only
        assertThat(of("en", frodon).applies()).isTrue();
        assertThat(of("en", frodon).folders()).isEmpty();
        assertThat(of("de", frodon).applies()).isFalse();
    }

    @Test
    void takesTheSubdomainsOfCompliance_butReadability() {
        // Readability measures a whole page: the Readability tab of the page shows its rules
        assertThat(ComplianceRules.subdomains(ContentDomains.base())).containsExactlyInAnyOrder("terms", "glossary", "writing");
    }

    @Test
    void appliesWhenACheckOfAnotherModuleRuns() {
        assertThat(of("de", price("acme.erpPrice", "acme.erpPrice")).applies()).isTrue();
        assertThat(of("de", price("acme.tone", "model")).applies()).isFalse();
    }
}
