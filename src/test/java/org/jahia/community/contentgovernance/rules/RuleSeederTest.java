package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSeederTest {

    private static final Map<String, Object> TITLE = Map.of(RuleValues.RULE_ID, "seo.title",
            RuleValues.EVALUATOR, "threshold", RuleValues.SEVERITY, "serious", RuleValues.UNIT, "characters",
            RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0);

    @Test
    @DisplayName("a node with the defaults of its type has the fingerprint of a file that leaves them out")
    void digest_countsTheDefaults() {
        Map<String, Object> node = new HashMap<>(TITLE);
        node.put(RuleValues.ENABLED, true);
        node.put(RuleValues.BLOCKING, false);
        node.put(RuleValues.FIXER, "editor");
        node.put(RuleValues.DOMAIN, "seo");

        assertThat(RuleValues.digest(node)).isEqualTo(RuleValues.digest(TITLE));
    }

    @Test
    @DisplayName("a site that changes one value changes the fingerprint")
    void digest_seesAChange() {
        Map<String, Object> changed = new HashMap<>(TITLE);
        changed.put(RuleValues.MINIMUM, 40.0);

        assertThat(RuleValues.digest(changed)).isNotEqualTo(RuleValues.digest(TITLE));
    }

    @Test
    @DisplayName("a new version overwrites a rule that nobody changed, and only fills a rule that the site changed")
    void update_followsTheFingerprint() {
        assertThat(RuleSeeder.updateOf("a", "a", "a")).isEqualTo(RuleSeeder.Update.NONE);
        assertThat(RuleSeeder.updateOf("a", "a", "b")).isEqualTo(RuleSeeder.Update.OVERWRITE);
        assertThat(RuleSeeder.updateOf("a", "c", "b")).isEqualTo(RuleSeeder.Update.FILL);
        assertThat(RuleSeeder.updateOf(null, "a", "a")).isEqualTo(RuleSeeder.Update.FILL);
    }

    @Test
    @DisplayName("a rule that the module no longer ships goes if nobody changed it, and stays marked if the site did")
    void retirement_followsTheFingerprint() {
        assertThat(RuleSeeder.retirementOf("a", "a")).isEqualTo(RuleSeeder.Retirement.REMOVE);
        assertThat(RuleSeeder.retirementOf("a", "b")).isEqualTo(RuleSeeder.Retirement.MARK);
        assertThat(RuleSeeder.retirementOf(null, "b")).isEqualTo(RuleSeeder.Retirement.KEEP);
    }

    @Test
    @DisplayName("a writing rule gets the evaluator pattern, blocking as a flag next to its severity, and a style rule is minor")
    void writingRule_mapsToAContentRule() {
        assertThat(LegacyRules.ruleOf("writing.a", "forbidden", "warning"))
                .containsEntry(RuleValues.EVALUATOR, "pattern").containsEntry(RuleValues.SEVERITY, "moderate")
                .containsEntry(RuleValues.BLOCKING, false).containsEntry(RuleValues.DOMAIN, "writing");
        assertThat(LegacyRules.ruleOf("writing.b", "replace", "blocking"))
                .containsEntry(RuleValues.SEVERITY, "serious").containsEntry(RuleValues.BLOCKING, true);
        assertThat(LegacyRules.ruleOf("writing.c", "style", "warning"))
                .containsEntry(RuleValues.EVALUATOR, "pattern").containsEntry(RuleValues.SEVERITY, "minor");
    }

    @Test
    @DisplayName("only a change of the installed modules of a site triggers a seed")
    void sitePathOf_readsTheInstalledModules() {
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/j:installedModules")).isEqualTo("/sites/digitall");
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/home/j:installedModules")).isNull();
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/j:title")).isNull();
    }
}
