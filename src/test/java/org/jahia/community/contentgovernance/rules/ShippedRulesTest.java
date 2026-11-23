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
    @DisplayName("the module ships 62 rules, four of them measures, and one rule per check of axe-core, and each names its evaluator, the check type CODE and one declared subdomain")
    void ownRules_load() {
        ShippedRules.Shipment own = ShippedRules.own();

        assertThat(own.complete()).isTrue();
        assertThat(own.rules().keySet().stream().filter(id -> !id.startsWith("axe."))).hasSize(62);
        assertThat(own.rules().keySet().stream().filter(id -> id.startsWith("axe."))).hasSize(96);
        assertThat(own.rules().values()).allSatisfy(rule -> {
            assertThat(rule.values()).containsKey(RuleValues.EVALUATOR).containsEntry(RuleValues.CHECK_TYPE, "CODE");
            assertThat(rule.values().get(RuleValues.SUBDOMAIN)).as(rule.id()).isInstanceOf(String.class);
            assertThat(own.subdomains()).containsKey((String) rule.values().get(RuleValues.SUBDOMAIN));
        });
        assertThat(own.rules().get("html.imageAltMissing").values()).containsEntry(RuleValues.SUBDOMAIN, "accessibility");
        // The GEO score counts rules of other subdomains, and each is a shipped rule
        List<String> counts = RuleValues.strings(own.subdomains().get("geo.score").values(), DomainValues.COUNTS);
        assertThat(counts).contains("html.imageAltMissing", "seo.title");
        assertThat(own.rules()).containsKeys(counts.toArray(String[]::new));
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
        assertThat(title.subdomain()).isEqualTo("seo");
        assertThat(title.sites()).containsExactly("*");
        assertThat(title.min()).isEqualTo(30);
        assertThat(title.max()).isEqualTo(60);
        assertThat(title.enabled()).isTrue();
    }

    @Test
    @DisplayName("the module ships the layout of #146: three domains of the governance panel and their subdomains")
    void ownDomains_load() {
        ShippedRules.Shipment own = ShippedRules.own();
        ContentDomains hierarchy = ContentDomains.base();

        assertThat(own.domains()).containsOnlyKeys("quality", "geo", "compliance");
        assertThat(hierarchy.domains()).extracting(Domain::id).containsExactly("quality", "geo", "compliance");
        assertThat(hierarchy.domains()).allSatisfy(domain -> assertThat(domain.panel()).isEqualTo("governance"));
        assertThat(hierarchy.subdomainsOf("quality")).extracting(Domain::id)
                .containsExactly("health", "links", "accessibility", "vitals", "ecodesign");
        assertThat(hierarchy.subdomainsOf("geo")).extracting(Domain::id)
                .containsExactly("geo.citations", "seo", "geo.score", "geo.content", "geo.files", "geo.schema", "geo.crawlers");
        assertThat(hierarchy.subdomainsOf("compliance")).extracting(Domain::id)
                .containsExactly("readability", "terms", "glossary", "writing");
        Domain geo = hierarchy.domains().get(1);
        assertThat(geo.text(DomainValues.LABEL, null)).isEqualTo("SEO & GEO");
        assertThat(geo.text(DomainValues.LABEL, "fr")).isEqualTo("SEO et GEO");
        assertThat(geo.text(DomainValues.HELP, "de")).startsWith("How search engines");
        assertThat(geo.sites()).containsExactly("*");
    }

    @Test
    @DisplayName("the subfolders domains and subdomains hold domain files, and any other file is a rule")
    void levelOf_readsTheFolder() {
        assertThat(ShippedRules.levelOf("domains/quality.md")).isEqualTo(DomainValues.Level.DOMAIN);
        assertThat(ShippedRules.levelOf("subdomains/seo.md")).isEqualTo(DomainValues.Level.SUBDOMAIN);
        assertThat(ShippedRules.levelOf("seo.title.md")).isNull();
        assertThat(ShippedRules.levelOf("seo/subdomains.md")).isNull();
    }

    @Test
    @DisplayName("a module adds a subdomain under a domain of Content Governance, and its rules may name it")
    void moduleSubdomain_isDeclared(@TempDir Path folder) throws IOException {
        Files.createDirectories(folder.resolve("subdomains"));
        Files.writeString(folder.resolve("subdomains/acme.prices.md"),
                "---\ndomain: compliance\norder: 50\nlabel: Prices\n---\nThe prices that the ERP gives.\n");
        Files.writeString(folder.resolve("acme.price.md"),
                "---\nevaluator: erp.price\ncheckType: CODE\nsubdomain: acme.prices\nseverity: serious\n---\nCheck the price.\n");

        ShippedRules.Shipment shipment = ShippedRules.load("acme-rules", folder);

        assertThat(shipment.complete()).isTrue();
        assertThat(shipment.subdomains()).containsOnlyKeys("acme.prices");
        assertThat(shipment.subdomains().get("acme.prices").values()).containsEntry(DomainValues.ORDER, 50L)
                .containsEntry(DomainValues.HELP, "The prices that the ERP gives.");
        assertThat(shipment.rules()).containsOnlyKeys("acme.price");
    }

    @Test
    @DisplayName("a subdomain that takes the id of a domain is skipped, because a layout names both in one list")
    void subdomainWithTheIdOfADomain_isSkipped(@TempDir Path folder) throws IOException {
        Files.createDirectories(folder.resolve("domains"));
        Files.createDirectories(folder.resolve("subdomains"));
        Files.writeString(folder.resolve("domains/acme.md"), "---\nlabel: Acme\n---\n");
        Files.writeString(folder.resolve("subdomains/acme.md"), "---\ndomain: acme\nlabel: Acme\n---\n");
        Files.writeString(folder.resolve("subdomains/quality.md"), "---\ndomain: acme\nlabel: Quality\n---\n");
        Files.writeString(folder.resolve("subdomains/acme.prices.md"), "---\ndomain: acme\nlabel: Prices\n---\n");

        ShippedRules.Shipment shipment = ShippedRules.load("acme-rules", folder);

        assertThat(shipment.domains()).containsOnlyKeys("acme");
        assertThat(shipment.subdomains()).as("acme is the id of a domain of this module, quality of Content Governance")
                .containsOnlyKeys("acme.prices");
        assertThat(shipment.complete()).isFalse();
    }

    @Test
    @DisplayName("a rule of an undeclared subdomain, and a subdomain of an undeclared domain, are skipped")
    void undeclared_isSkipped(@TempDir Path folder) throws IOException {
        Files.createDirectories(folder.resolve("subdomains"));
        Files.writeString(folder.resolve("subdomains/acme.lost.md"), "---\ndomain: acme\nlabel: Lost\n---\n");
        Files.writeString(folder.resolve("acme.price.md"),
                "---\nevaluator: erp.price\ncheckType: CODE\nsubdomain: acme.prices\nseverity: serious\n---\n");
        Files.writeString(folder.resolve("acme.tone.md"),
                "---\nevaluator: erp.tone\ncheckType: CODE\nsubdomain: acme.lost\nseverity: serious\n---\n");
        Files.writeString(folder.resolve("acme.ok.md"),
                "---\nevaluator: erp.ok\ncheckType: CODE\nsubdomain: writing\nseverity: serious\n---\n");

        ShippedRules.Shipment shipment = ShippedRules.load("acme-rules", folder);

        assertThat(shipment.subdomains()).isEmpty();
        assertThat(shipment.rules()).containsOnlyKeys("acme.ok");
        assertThat(shipment.complete()).isFalse();
    }

    @Test
    @DisplayName("the folder of a header loses its slashes at both ends")
    void headerFolder_losesItsSlashes() {
        assertThat(ShippedRules.withoutSlashes("/content-rules/")).isEqualTo("content-rules");
        assertThat(ShippedRules.withoutSlashes("rules/seo")).isEqualTo("rules/seo");
        assertThat(ShippedRules.withoutSlashes("//")).isEmpty();
    }

    @Test
    @DisplayName("a folder with a file that fails, a file with the subdomains of a snapshot before 2.0.0, or two files with one id, is not complete")
    void badFile_makesTheShipmentIncomplete(@TempDir Path folder) throws IOException {
        String rule = "---\nid: acme.price\nevaluator: erp.price\ncheckType: CODE\nsubdomain: writing\nseverity: serious\n---\nCheck the price.\n";
        Files.writeString(folder.resolve("price.md"), rule);
        Files.writeString(folder.resolve("price-again.md"), rule);
        Files.writeString(folder.resolve("broken.md"), "---\nseverity: serious\n---\n");
        Files.writeString(folder.resolve("acme.old.md"),
                "---\nevaluator: erp.price\ncheckType: CODE\nsubdomains: [writing]\nseverity: serious\n---\n");

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
