package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShippedRulesTest {

    @Test
    @DisplayName("the module ships 62 rules, four of them measures, and one rule per check of axe-core, and each names its evaluator and a known domain")
    void ownRules_load() {
        ShippedRules.Shipment own = ShippedRules.own();

        assertThat(own.complete()).isTrue();
        assertThat(own.rules().keySet().stream().filter(id -> !id.startsWith("axe."))).hasSize(62);
        assertThat(own.rules().keySet().stream().filter(id -> id.startsWith("axe."))).hasSize(96);
        assertThat(own.rules().values()).allSatisfy(rule -> {
            assertThat(rule.values()).containsKey(RuleValues.EVALUATOR);
            assertThat(RuleDomains.known(rule.values().get(RuleValues.DOMAIN))).isTrue();
        });
        assertThat(own.rules().get("html.imageAltMissing").values()).containsEntry(RuleValues.DOMAIN, "accessibility");
        assertThat(own.rules().get("axe.color-contrast").values()).containsEntry(RuleValues.EVALUATOR, "axe")
                .containsEntry(RuleValues.SEVERITY, "serious");
        assertThat(own.rules().values().stream().filter(rule -> !rule.id().startsWith("axe.")))
                .allSatisfy(rule -> assertThat(rule.values()).containsKeys(RuleValues.TITLE, RuleValues.LABEL, RuleValues.TRANSLATIONS));
        // axe-core, the web vitals and eco-design run in the browser, each eco-design measure under its own evaluator
        assertThat(own.rules().values().stream().filter(rule -> rule.id().startsWith("axe.") || rule.id().startsWith("ecodesign.")))
                .hasSize(108).allSatisfy(rule -> assertThat(RuleValues.toRule(rule.values()).inBrowser()).as(rule.id()).isTrue());
        assertThat(own.rules().get("ecodesign.pageWeight").values()).containsEntry(RuleValues.EVALUATOR, "ecodesign.pageWeight");
        assertThat(own.rules().values().stream().filter(rule -> RuleValues.toRule(rule.values()).inBrowser())).hasSize(108);
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
        String rule = "---\nid: acme.price\nevaluator: erp.price\ndomain: writing\nseverity: serious\n---\nCheck the price.\n";
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

    @Test
    @DisplayName("the rules that axe-core marks experimental ship switched off, and a site can switch them on")
    void experimentalAxeRules_shipOff() {
        ContentRules base = ContentRules.base();
        for (String id : List.of("axe.css-orientation-lock", "axe.focus-order-semantics", "axe.hidden-content",
                "axe.p-as-heading", "axe.table-fake-caption", "axe.td-has-header")) {
            assertThat(base.get(id).enabled()).as(id).isFalse();
            assertThat(base.changed(id, true, null, null, null).get(id).enabled()).as(id).isTrue();
        }
        assertThat(base.get("axe.image-alt").enabled()).isTrue();
    }
}
