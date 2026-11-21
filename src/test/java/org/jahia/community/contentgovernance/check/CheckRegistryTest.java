package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
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
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("the results give one per rule that ran, passed or failed, from the default method of a check")
    void results_givePassesAndFailures() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice").changed("acme.erpPrice", null, "critical", null, null);

        assertThat(registry.results(rules, CheckInput.value(PAGE, "en", "Only 12.50 EUR"))).containsExactly(
                new CheckResult("acme.erpPrice", false, Map.of(),
                        List.of(new Finding("acme.erpPrice", "critical", "12.50 EUR", null))));
        assertThat(registry.results(rules, CheckInput.value(PAGE, "en", "Free"))).containsExactly(
                new CheckResult("acme.erpPrice", true, Map.of(), List.of()));
        assertThat(registry.results(rules.changed("acme.erpPrice", false, null, null, null),
                CheckInput.value(PAGE, "en", "Free"))).as("a rule that is off does not run").isEmpty();
    }

    @Test
    @DisplayName("a result carries the values of its check, under the id of the rule that ran, and no null")
    void results_carryTheValuesOfTheCheck() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(new ContentCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, Rule rule) {
                return List.of();
            }

            @Override
            public CheckResult result(CheckInput input, Rule rule) {
                return new CheckResult("another.id", true, Map.of("length", input.text().length()), List.of());
            }
        }, id("acme.length"));
        registry.bindCheck(new ContentCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, Rule rule) {
                return List.of();
            }

            @Override
            public CheckResult result(CheckInput input, Rule rule) {
                return null;
            }
        }, id("acme.none"));
        ContentRules rules = withRule("acme.length", "acme.length").with(RuleValues.toRule(Map.of(
                RuleValues.RULE_ID, "acme.none", RuleValues.EVALUATOR, "acme.none", RuleValues.DOMAIN, "writing")));

        assertThat(registry.results(rules, CheckInput.value(PAGE, "en", "Hello"))).containsExactly(
                new CheckResult("acme.length", true, Map.of("length", 5), List.of()));
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
    @DisplayName("a run skips a rule that is off, an evaluator the caller applies, and a rule of the browser")
    void run_skips() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice");
        CheckInput input = CheckInput.value(PAGE, "en", "12.50 EUR");

        assertThat(registry.run(rules.changed("acme.erpPrice", false, null, null, null), input, Set.of())).isEmpty();
        assertThat(registry.run(rules, input, Set.of("acme.erpPrice"))).isEmpty();

        ContentRules browser = ContentRules.base().with(inBrowser("acme.erpPrice", false));
        assertThat(registry.run(browser, input, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("a rule of the browser is active with no check on the server, and never blocks, even when its node sets blocking")
    void browserRule_isActiveAndNeverBlocks() {
        CheckRegistry registry = new CheckRegistry();
        ContentRule server = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.vitals", RuleValues.EVALUATOR, "acme.vitals",
                RuleValues.DOMAIN, "ecodesign", RuleValues.BLOCKING, true));
        ContentRule browser = inBrowser("acme.vitals", true);
        registry.bindCheck(PRICE, id("acme.vitals"));

        assertThat(registry.blocking(server)).isTrue();
        assertThat(registry.blocking(browser)).isFalse();
        assertThat(new CheckRegistry().status(browser)).isEqualTo(new CheckRegistry.Status(true, null, null));
    }

    /** A rule of the domain ecodesign that states {@code runtime: browser}, with its id as its evaluator. */
    private static ContentRule inBrowser(String id, boolean blocking) {
        return RuleValues.toRule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, id, RuleValues.DOMAIN, "ecodesign",
                RuleValues.BLOCKING, blocking, RuleValues.RUNTIME, ContentRule.BROWSER));
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
        assertThat(registry.results(withRule("acme.many", "acme.many"), CheckInput.value(PAGE, "en", "x")))
                .singleElement().satisfies(result -> assertThat(result.findings()).hasSize(CheckRegistry.MAX_FINDINGS));
    }

    /** A check of the scope VALUE whose result is the one that {@code result} builds for the rule. */
    private static ContentCheck resultCheck(Function<Rule, CheckResult> result) {
        return new ContentCheck() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.VALUE);
            }

            @Override
            public List<Finding> check(CheckInput input, Rule rule) {
                return List.of();
            }

            @Override
            public CheckResult result(CheckInput input, Rule rule) {
                return result.apply(rule);
            }
        };
    }

    @Test
    @DisplayName("a result with a finding never passes, and a result whose values JSON cannot write gives no result")
    void results_keepTheContractOfAResult() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(resultCheck(rule -> new CheckResult(rule.id(), true, Map.of(), List.of(Finding.of(rule, "x")))),
                id("acme.found"));
        registry.bindCheck(resultCheck(rule -> new CheckResult(rule.id(), true, Map.of("opaque", new Object()), List.of())),
                id("acme.opaque"));
        registry.bindCheck(resultCheck(rule -> new CheckResult(rule.id(), false, Map.of(),
                List.of(new Finding(rule.id(), "minor", "x", null, null, Map.of("opaque", new Object()))))),
                id("acme.opaqueFinding"));
        ContentRules rules = withRule("acme.found", "acme.found");
        for (String id : List.of("acme.opaque", "acme.opaqueFinding")) {
            rules = rules.with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, id,
                    RuleValues.DOMAIN, "writing")));
        }

        assertThat(registry.results(rules, CheckInput.value(PAGE, "en", "x"))).singleElement().satisfies(result -> {
            assertThat(result.ruleId()).isEqualTo("acme.found");
            assertThat(result.passed()).isFalse();
        });
    }

    @Test
    @DisplayName("Content Governance registers a check for the evaluator of each rule of the server that it ships")
    void everyShippedRule_hasACheckOfThisModule() {
        Set<String> registered = new HashSet<>(List.of(ContentRule.PATTERN, ContentRule.THRESHOLD, ContentRule.PRESENCE));
        registered.addAll(GeoChecks.IDS);
        registered.addAll(SeoChecks.IDS);
        registered.addAll(ReadabilityChecks.IDS);
        registered.addAll(LinkChecks.IDS);
        registered.addAll(HealthChecks.IDS);
        Arrays.stream(HtmlRule.values()).map(HtmlRule::id).forEach(registered::add);

        List<String> missing = new ArrayList<>();
        // The drawer registers the checks of the rules of the browser: drawerChecks.test.js tests them
        for (ContentRule rule : ContentRules.base().all()) {
            if (!rule.inBrowser() && !registered.contains(rule.evaluator())) {
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
