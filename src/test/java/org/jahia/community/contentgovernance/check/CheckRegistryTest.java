package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
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
import java.util.function.Function;
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
                RuleValues.SEVERITY, "serious", RuleValues.SUBDOMAIN, "writing")));
    }

    /** The rule of the values, placed in the shipped hierarchy as a site places it. */
    private static ContentRule placed(Map<String, Object> values) {
        ContentRule rule = RuleValues.toRule(values);
        return ContentRules.base().with(rule).get(rule.id());
    }

    @Test
    @DisplayName("a rule whose check no module registers is inactive, and names the check and its module")
    void status_namesTheMissingCheck() {
        CheckRegistry registry = new CheckRegistry();
        ContentRule model = placed(Map.of(RuleValues.RULE_ID, "acme.tone", RuleValues.EVALUATOR, "model",
                RuleValues.SUBDOMAIN, "writing"));
        ContentRule price = placed(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.SUBDOMAIN, "writing"));

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
                RuleValues.RULE_ID, "acme.none", RuleValues.EVALUATOR, "acme.none", RuleValues.SUBDOMAIN, "writing")));

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
                RuleValues.SUBDOMAIN, "ecodesign", RuleValues.BLOCKING, true));
        ContentRule browser = inBrowser("acme.vitals", true);
        registry.bindCheck(PRICE, id("acme.vitals"));

        assertThat(registry.blocking(server)).isTrue();
        assertThat(registry.blocking(browser)).isFalse();
        assertThat(new CheckRegistry().status(browser)).isEqualTo(new CheckRegistry.Status(true, null, null));
    }

    @Test
    @DisplayName("a rule on demand runs only in a run on demand, which runs no other rule, and never blocks")
    void manualRule_runsOnDemandOnly() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRules rules = withRule("acme.erpPrice", "acme.erpPrice").with(RuleValues.toRule(Map.of(
                RuleValues.RULE_ID, "acme.tone", RuleValues.EVALUATOR, "acme.erpPrice", RuleValues.SEVERITY, "minor",
                RuleValues.SUBDOMAIN, "writing", RuleValues.TRIGGER, ContentRule.MANUAL, RuleValues.BLOCKING, true)));
        CheckInput input = CheckInput.value(PAGE, "en", "Only 12.50 EUR");

        assertThat(registry.results(rules, input)).extracting(CheckResult::ruleId).containsExactly("acme.erpPrice");
        assertThat(registry.runs(rules.get("acme.tone"), ContentCheck.Scope.VALUE)).isFalse();
        assertThat(registry.manualResults(rules, input, Set.of("acme.tone", "acme.erpPrice")))
                .containsExactly(new CheckResult("acme.tone", false, Map.of(),
                        List.of(new Finding("acme.tone", "minor", "12.50 EUR", null))));
        assertThat(registry.blocking(rules.get("acme.tone"))).isFalse();
    }

    /** A rule of the subdomain ecodesign that states {@code runtime: browser}, with its id as its evaluator. */
    private static ContentRule inBrowser(String id, boolean blocking) {
        return placed(Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, id, RuleValues.SUBDOMAIN, "ecodesign",
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
                RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice", RuleValues.SUBDOMAIN, "writing")));

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
                        RuleValues.SUBDOMAIN, "writing")))
                .with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.second", RuleValues.EVALUATOR, "acme.set",
                        RuleValues.SUBDOMAIN, "terms")));
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
        assertThat(registry.runs(placed(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR,
                "acme.erpPrice", RuleValues.SUBDOMAIN, "writing", RuleValues.ENABLED, false)), ContentCheck.Scope.VALUE))
                .isFalse();
        // A rule whose subdomain nobody declares has no place, so it runs nowhere
        assertThat(registry.runs(placed(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR,
                "acme.erpPrice", RuleValues.SUBDOMAIN, "acme")), ContentCheck.Scope.VALUE)).isFalse();
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
                    RuleValues.SUBDOMAIN, "writing")));
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
    @DisplayName("a rule whose subdomain nobody declares is inactive, names it, and does not run")
    void aRuleOfAnUnknownSubdomain_isInactiveAndDoesNotRun() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("acme.erpPrice"));
        ContentRule brand = placed(Map.of(RuleValues.RULE_ID, "brand.price", RuleValues.EVALUATOR, "acme.erpPrice"));
        ContentRule other = placed(Map.of(RuleValues.RULE_ID, "acme.price", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.SUBDOMAIN, "acme"));

        assertThat(brand.subdomain()).isNull();
        assertThat(registry.status(brand)).isEqualTo(new CheckRegistry.Status(false, null, null, ""));
        assertThat(registry.status(other)).isEqualTo(new CheckRegistry.Status(false, null, null, "acme"));
        CheckInput input = CheckInput.value("/sites/digitall/home/main/intro", "en", "Only 12.50 EUR");
        assertThat(registry.run(ContentRules.base().with(brand).with(other), input, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("a rule that names an unknown subdomain does not fall back to the subdomain of its id, and does not run")
    void aRuleOfAnUnknownSubdomain_keepsIt() {
        CheckRegistry registry = new CheckRegistry();
        registry.bindCheck(PRICE, id("writing.erpPrice"));
        ContentRules rules = ContentRules.base().with(RuleValues.toRule(Map.of(RuleValues.RULE_ID, "writing.erpPrice",
                RuleValues.EVALUATOR, "writing.erpPrice", RuleValues.SUBDOMAIN, "acme.gone")));

        assertThat(rules.get("writing.erpPrice").subdomain()).isEqualTo("acme.gone");
        assertThat(registry.status(rules.get("writing.erpPrice"))).isEqualTo(new CheckRegistry.Status(false, null, null, "acme.gone"));
        assertThat(registry.run(rules, CheckInput.value(PAGE, "en", "12.50 EUR"), Set.of())).isEmpty();
    }

    @Test
    @DisplayName("the rules that the module and ai-assistant write are placed in the subdomains writing, terms and glossary")
    void theRulesThatTheModuleAndAiAssistantWrite_arePlaced() {
        // LegacyTerms and the import of ai-assistant write glossary.* rules in the subdomain glossary
        for (String subdomain : List.of("writing", "terms", "glossary")) {
            ContentRule rule = placed(Map.of(RuleValues.RULE_ID, "x.y", RuleValues.EVALUATOR, "pattern",
                    RuleValues.SUBDOMAIN, subdomain));
            assertThat(rule.placed()).as(subdomain).isTrue();
        }
        assertThat(placed(Map.of(RuleValues.RULE_ID, "glossary.access-token", RuleValues.EVALUATOR, "pattern"))
                .subdomain()).isEqualTo("glossary");
    }

    @Test
    @DisplayName("a term that a site adds with no subdomain goes to terms, and another rule with none has no place")
    void aTermThatASiteAddsWithNoSubdomain_goesToTerms() {
        assertThat(placed(Map.of(RuleValues.RULE_ID, "brand.seamless", RuleValues.EVALUATOR, "pattern")).subdomain())
                .isEqualTo("terms");
        assertThat(placed(Map.of(RuleValues.RULE_ID, "brand.seamless")).subdomain()).isEqualTo("terms");
        assertThat(placed(Map.of(RuleValues.RULE_ID, "seo.slug", RuleValues.EVALUATOR, "pattern")).subdomain())
                .isEqualTo("seo");
        ContentRule tone = placed(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model"));
        assertThat(tone.subdomain()).isNull();
        assertThat(tone.placed()).isFalse();
        assertThat(placed(Map.of(RuleValues.RULE_ID, "brand.seamless", RuleValues.EVALUATOR, "pattern",
                RuleValues.SUBDOMAIN, "brand")).placed()).isFalse();
    }
}
