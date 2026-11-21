package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.check.spi.RuleSetCheck;
import org.jahia.community.contentgovernance.geo.check.GeoChecks;
import org.jahia.community.contentgovernance.html.rules.HtmlRule;
import org.jahia.community.contentgovernance.page.HealthChecks;
import org.jahia.community.contentgovernance.page.LinkChecks;
import org.jahia.community.contentgovernance.page.ReadabilityChecks;
import org.jahia.community.contentgovernance.page.SeoChecks;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CheckRegistryTest {

    private static final String PAGE = "/home";

    private static Map<String, Object> id(String checkId) {
        return Map.of(ContentCheck.ID, checkId);
    }

    /** A check of the scope VALUE that finds each "12.50 EUR" in the text. */
    private static final ContentCheck PRICE = new ContentCheck() {
        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.VALUE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            return input.text().contains("12.50 EUR") ? List.of(Finding.of(rule, "12.50 EUR")) : List.of();
        }
    };

    private static ContentRules withRule(String id, String evaluator) {
        return ContentRules.base().with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, evaluator,
                RuleValues.SEVERITY, "serious", RuleValues.DOMAIN, "writing")));
    }

    @Test
    @DisplayName("a rule whose check no module registers is inactive, and names the check and its module")
    void status_namesTheMissingCheck() {
        CheckRegistry registry = new CheckRegistry();
        ContentRule model = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.tone", RuleValues.EVALUATOR, "model", RuleValues.DOMAIN, "writing"));
        ContentRule price = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice", RuleValues.DOMAIN, "writing"));

        assertThat(registry.status(model)).isEqualTo(new CheckRegistry.Status(false, "model", "ai-assistant"));
        assertThat(registry.status(price)).isEqualTo(new CheckRegistry.Status(false, "acme.erpPrice", null));

        registry.bindCheck(PRICE, id("acme.erpPrice"));
        assertThat(registry.status(price)).isEqualTo(new CheckRegistry.Status(true, null, null));

        registry.unbindCheck(PRICE, id("acme.erpPrice"));
        assertThat(registry.status(price).active()).isFalse();
    }

    @Test
    @DisplayName("a check registered without an id is ignored")
    void bind_withoutId_isIgnored() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, Map.of());
        assertThat(registry.has("")).isFalse();
        assertThat(registry.has(null)).isFalse();
    }

    @Test
    @DisplayName("one object under two ids loses one id at a time")
    void unbind_removesOneIdOnly() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("a.one"));
        registry.bindCheck(PRICE, id("a.two"));

        registry.unbindCheck(PRICE, id("a.one"));

        assertThat(registry.has("a.one")).isFalse();
        assertThat(registry.has("a.two")).isTrue();
    }

    @Test
    @DisplayName("a run applies the check of a rule to an input of its scope, with the severity of the site")
    void run_findsWithTheRuleOfTheSite() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice").changed("acme.erpPrice", null, "critical", null, null);

        List<CheckRegistry.Checked> found = registry.run(rules, CheckInput.value(PAGE, "en", "Only 12.50 EUR"), Set.of());

        assertThat(found).extracting(CheckRegistry.Checked::finding)
                .containsExactly(new Finding("acme.erpPrice", "critical", "12.50 EUR", null));
        assertThat(registry.run(rules, CheckInput.page(PAGE, "en", "Only 12.50 EUR"), Set.of()))
                .as("a check of the scope VALUE does not run on a page").isEmpty();
    }

    @Test
    @DisplayName("a run on some rules runs those rules only")
    void runRules_runsTheNamedRulesOnly() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice");
        CheckInput input = CheckInput.value(PAGE, "en", "12.50 EUR");

        assertThat(registry.runRules(rules, input, Set.of("acme.erpPrice"))).hasSize(1);
        assertThat(registry.runRules(rules, input, Set.of("seo.title"))).isEmpty();
    }

    @Test
    @DisplayName("a run skips a rule that is off, an evaluator the caller applies, and a check of the browser")
    void run_skips() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice");
        CheckInput input = CheckInput.value(PAGE, "en", "12.50 EUR");

        assertThat(registry.run(rules.changed("acme.erpPrice", false, null, null, null), input, Set.of())).isEmpty();
        assertThat(registry.run(rules, input, Set.of("acme.erpPrice"))).isEmpty();

        CheckRegistry browser = new CheckRegistry();
        browser.bindCheck(PRICE, Map.of(ContentCheck.ID, "acme.erpPrice", CheckRegistry.RUNTIME, CheckRegistry.BROWSER));
        assertThat(browser.has("acme.erpPrice")).isTrue();
        assertThat(browser.run(rules, input, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("a rule of a check of the browser never blocks, even when its node sets blocking")
    void blocking_isNeverTrueForTheBrowser() {
        ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.vitals", RuleValues.EVALUATOR, "acme.vitals",
                RuleValues.BLOCKING, true));
        CheckRegistry server = new CheckRegistry();
        server.bindCheck(PRICE, id("acme.vitals"));
        CheckRegistry browser = new CheckRegistry();
        browser.bindCheck(PRICE, Map.of(ContentCheck.ID, "acme.vitals", CheckRegistry.RUNTIME, CheckRegistry.BROWSER));

        assertThat(server.blocking(rule)).isTrue();
        assertThat(browser.blocking(rule)).isFalse();
    }

    @Test
    @DisplayName("a check that throws gives no finding, and the other checks still run")
    void run_survivesAFailingCheck() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(new ContentCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, Rule rule) {
                throw new IllegalStateException("broken");
            }
        }, id("acme.broken"));
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.broken", "acme.broken").with(RuleValues.toRule(Map.of(
                RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice", RuleValues.DOMAIN, "writing")));

        assertThat(registry.run(rules, CheckInput.value(PAGE, "en", "12.50 EUR"), Set.of()))
                .extracting(checked -> checked.rule().id()).containsExactly("acme.erpPrice");
    }

    @Test
    @DisplayName("one check gives at most 50 findings on one input")
    void run_capsTheFindingsOfACheck() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(new ContentCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, Rule rule) {
                return IntStream.range(0, 80).mapToObj(i -> Finding.of(rule, "x" + i)).toList();
            }
        }, id("acme.many"));

        assertThat(registry.run(withRule("acme.many", "acme.many"), CheckInput.value(PAGE, "en", "x"), Set.of()))
                .hasSize(CheckRegistry.MAX_FINDINGS);
    }

    /** A check that applies its rules together: it notes each call, and finds each rule once, in reverse order. */
    private static final class Together implements RuleSetCheck {
        final List<List<String>> calls = new ArrayList<>();

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.VALUE);
        }

        @Override
        public List<Finding> check(CheckInput input, List<? extends Rule> rules) {
            calls.add(rules.stream().map(Rule::id).toList());
            List<Finding> found = new ArrayList<>();
            for (int i = rules.size() - 1; i >= 0; i--) {
                found.add(Finding.of(rules.get(i), "x"));
            }
            // A finding of a rule that the check was not given goes nowhere
            found.add(new Finding("acme.erpPrice", "minor", "stray", null));
            return found;
        }
    }

    private static ContentRules setAndPrice() {
        return withRule("acme.first", "acme.set")
                .with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                        RuleValues.DOMAIN, "writing")))
                .with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.second", RuleValues.EVALUATOR, "acme.set",
                        RuleValues.DOMAIN, "compliance")));
    }

    @Test
    @DisplayName("a check that applies its rules together runs once per input, and each finding goes to its rule")
    void run_appliesARuleSetOnce() {
        CheckRegistry registry = new CheckRegistry();
        Together together = new Together();
        registry.bindCheck(together, id("acme.set"));
        registry.bindCheck(PRICE, id("acme.erpPrice"));

        List<CheckRegistry.Checked> found = registry.run(setAndPrice(), CheckInput.value(PAGE, "en", "12.50 EUR"), Set.of());

        assertThat(together.calls).containsExactly(List.of("acme.first", "acme.second"));
        // In the order of the rules, whatever order the check gave
        assertThat(found).extracting(checked -> checked.rule().id(), checked -> checked.finding().text())
                .containsExactly(tuple("acme.first", "x"), tuple("acme.erpPrice", "12.50 EUR"), tuple("acme.second", "x"));
        assertThat(registry.runRules(setAndPrice(), CheckInput.value(PAGE, "en", "12.50 EUR"), Set.of("acme.second")))
                .extracting(checked -> checked.rule().id()).containsExactly("acme.second");
        assertThat(together.calls).last().isEqualTo(List.of("acme.second"));
    }

    @Test
    @DisplayName("a check of a rule set that throws gives nothing for its rules, and the other checks still run")
    void run_survivesAFailingRuleSet() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(new RuleSetCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, List<? extends Rule> rules) {
                throw new IllegalStateException("broken");
            }
        }, id("acme.set"));
        registry.bindCheck(PRICE, id("acme.erpPrice"));

        assertThat(registry.run(setAndPrice(), CheckInput.value(PAGE, "en", "12.50 EUR"), Set.of()))
                .extracting(checked -> checked.rule().id()).containsExactly("acme.erpPrice");
    }

    @Test
    @DisplayName("a rule runs in a scope when the site keeps it on and a started module registers its check there")
    void runs_namesTheRulesThatRunInAScope() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRule price = withRule("acme.erpPrice", "acme.erpPrice").get("acme.erpPrice");

        assertThat(registry.runs(price, ContentCheck.Scope.VALUE)).isTrue();
        assertThat(registry.runs(price, ContentCheck.Scope.PAGE)).isFalse();
        assertThat(registry.runs(withRule("acme.tone", "model").get("acme.tone"), ContentCheck.Scope.VALUE)).isFalse();
        assertThat(registry.runs(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR,
                "acme.erpPrice", RuleValues.DOMAIN, "writing", RuleValues.ENABLED, false)), ContentCheck.Scope.VALUE)).isFalse();
        assertThat(registry.runs(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR,
                "acme.erpPrice", RuleValues.DOMAIN, "acme")), ContentCheck.Scope.VALUE)).isFalse();
    }

    @Test
    @DisplayName("Content Governance registers a check for the evaluator of each rule that it ships")
    void everyShippedRule_hasACheckOfThisModule() {
        Set<String> registered = new HashSet<>(List.of(ContentRule.PATTERN, ContentRule.THRESHOLD, ContentRule.PRESENCE));
        registered.addAll(BrowserChecks.IDS);
        registered.addAll(GeoChecks.IDS);
        registered.addAll(SeoChecks.IDS);
        registered.addAll(ReadabilityChecks.IDS);
        registered.addAll(LinkChecks.IDS);
        registered.addAll(HealthChecks.IDS);
        Arrays.stream(HtmlRule.values()).map(HtmlRule::id).forEach(registered::add);

        List<String> missing = new ArrayList<>();
        for (ContentRule rule : ContentRules.base().all()) {
            if (!registered.contains(rule.evaluator())) {
                missing.add(rule.id() + " -> " + rule.evaluator());
            }
        }

        assertThat(missing).isEmpty();
        assertThat(ContentRules.base().all()).extracting(ContentRule::id)
                .containsAll(Arrays.stream(HtmlRule.values()).map(HtmlRule::id).toList());
    }

    @Test
    void aRuleOfAnUnknownDomain_isInactiveAndDoesNotRun() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRule brand = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "brand.price", RuleValues.EVALUATOR, "acme.erpPrice"));
        ContentRule other = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.price", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.DOMAIN, "acme"));

        assertThat(brand.domain()).isNull();
        assertThat(registry.status(brand)).isEqualTo(new CheckRegistry.Status(false, null, null, ""));
        assertThat(registry.status(other)).isEqualTo(new CheckRegistry.Status(false, null, null, "acme"));
        CheckInput input = CheckInput.value("/sites/digitall/home/main/intro", "en", "Only 12.50 EUR");
        assertThat(registry.run(ContentRules.base().with(brand).with(other), input, Set.of())).isEmpty();
    }

    @Test
    void theRulesThatTheModuleAndAiAssistantWrite_haveAKnownDomain() {
        // LegacyTerms and the import of ai-assistant write glossary.* rules with the domain glossary
        for (String domain : List.of("writing", "compliance", "glossary")) {
            ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "x.y", RuleValues.EVALUATOR, "pattern",
                    RuleValues.DOMAIN, domain));
            assertThat(rule.hasKnownDomain()).as(domain).isTrue();
        }
        assertThat(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "glossary.access-token", RuleValues.EVALUATOR, "pattern"))
                .domain()).isEqualTo("glossary");
    }

    @Test
    void aTermThatASiteAddsWithNoDomain_isACompliance() {
        assertThat(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "brand.seamless", RuleValues.EVALUATOR, "pattern")).domain())
                .isEqualTo("compliance");
        assertThat(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "brand.seamless")).domain()).isEqualTo("compliance");
        assertThat(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model")).domain()).isNull();
        assertThat(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "brand.seamless", RuleValues.EVALUATOR, "pattern",
                RuleValues.DOMAIN, "brand")).hasKnownDomain()).isFalse();
    }
}
