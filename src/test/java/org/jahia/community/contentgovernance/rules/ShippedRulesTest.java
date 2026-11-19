package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippedRulesTest {

    @Test
    @DisplayName("the module ships 52 rules, and each names its evaluator")
    void ownRules_load() {
        ShippedRules.Shipment own = ShippedRules.own();

        assertThat(own.complete()).isTrue();
        assertThat(own.rules()).hasSize(52);
        assertThat(own.rules().values()).allSatisfy(rule -> assertThat(rule.values()).containsKey(RuleValues.EVALUATOR));
        ContentRule title = RuleValues.toRule(own.rules().get("seo.title").values());
        assertThat(title.evaluator()).isEqualTo(ContentRule.THRESHOLD);
        assertThat(title.domain()).isEqualTo("seo");
        assertThat(title.min()).isEqualTo(30);
        assertThat(title.max()).isEqualTo(60);
        assertThat(title.enabled()).isTrue();
    }

    @Test
    @DisplayName("the folder of a header loses its slashes at both ends")
    void headerFolder_losesItsSlashes() {
        assertThat(ShippedRules.withoutSlashes("/content-rules/")).isEqualTo("content-rules");
        assertThat(ShippedRules.withoutSlashes("rules/seo")).isEqualTo("rules/seo");
        assertThat(ShippedRules.withoutSlashes("//")).isEmpty();
    }

    @Test
    @DisplayName("a folder with a file that fails, or two files with one id, is not complete")
    void badFile_makesTheShipmentIncomplete(@TempDir Path folder) throws IOException {
        String rule = "---\nid: acme.price\nevaluator: erp.price\nseverity: serious\n---\nCheck the price.\n";
        Files.writeString(folder.resolve("price.md"), rule);
        Files.writeString(folder.resolve("price-again.md"), rule);
        Files.writeString(folder.resolve("broken.md"), "---\nseverity: serious\n---\n");

        ShippedRules.Shipment shipment = ShippedRules.load("acme-rules", folder);

        assertThat(shipment.rules()).containsOnlyKeys("acme.price");
        assertThat(shipment.complete()).isFalse();
    }

    @Test
    @DisplayName("the rules of a site that has no node are the shipped ones, and an unknown id is an error")
    void base_appliesTheShippedRules() {
        ContentRules base = ContentRules.base();

        assertThat(base.get("seo.indexable").evaluator()).isEqualTo("seo.indexable");
        assertThatThrownBy(() -> base.get("no.such")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a site change keeps what it does not name")
    void siteChange_keepsTheRest() {
        ContentRules site = ContentRules.base()
                .changed("seo.title", null, "bogus", 40.0, null)
                .changed("seo.canonical", false, "minor", null, null)
                .changed("no.such", false, null, null, null);

        ContentRule title = site.get("seo.title");
        assertThat(title.min()).isEqualTo(40);
        assertThat(title.max()).isEqualTo(60);
        assertThat(title.severity()).isEqualTo("serious");
        ContentRule canonical = site.get("seo.canonical");
        assertThat(canonical.enabled()).isFalse();
        assertThat(canonical.severity()).isEqualTo("minor");
    }
}
