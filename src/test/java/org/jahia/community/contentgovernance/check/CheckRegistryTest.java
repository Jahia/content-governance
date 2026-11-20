package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.geo.check.GeoChecks;
import org.jahia.community.contentgovernance.html.rules.HtmlRule;
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
                RuleValues.SEVERITY, "serious")));
    }

    @Test
    @DisplayName("a rule whose check no module registers is inactive, and names the check and its module")
    void status_namesTheMissingCheck() {
        CheckRegistry registry = new CheckRegistry();
        ContentRule model = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.tone", RuleValues.EVALUATOR, "model"));
        ContentRule price = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice"));

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
                RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice")));

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

    @Test
    @DisplayName("Content Governance registers a check for the evaluator of each rule that it ships")
    void everyShippedRule_hasACheckOfThisModule() {
        Set<String> registered = new HashSet<>(List.of(ContentRule.PATTERN, ContentRule.THRESHOLD, ContentRule.PRESENCE));
        registered.addAll(BrowserChecks.IDS);
        registered.addAll(GeoChecks.IDS);
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
}
