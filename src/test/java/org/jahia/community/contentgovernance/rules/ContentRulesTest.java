package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules of a site: the shipped rules, then the nodes of systemsite, then the nodes of the site, each placed
 * in its subdomain by the layout of the site.
 */
class ContentRulesTest {

    /** The subdomain seo in the tree of the system site. */
    private static final String TREE_SEO = ContentDomains.TREE + "/geo/seo";
    /** A folder of the system site outside the tree, such as the rules of a customer. */
    private static final String PLATFORM = "/sites/systemsite/contents/brand/rules";
    private static final String SITE = "/sites/digitall/contents/content-governance/ai-assistant/rules";

    /** A module that the site may enable, and that ships one rule. */
    private static final ShippedRules.Shipment ACME = new ShippedRules.Shipment("acme", Map.of("acme.erpPrice",
            new RuleFile("acme.erpPrice", Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                    RuleValues.SUBDOMAIN, "writing"))), true);

    private static final List<ContentRules.RuleNode> PLATFORM_NODES = List.of(
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "brand.noClickHere", RuleValues.PATTERN, "click here"), PLATFORM),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.metaDescription", RuleValues.SUBDOMAIN, "seo",
                    RuleValues.MAXIMUM, 140.0), TREE_SEO, 0),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.SUBDOMAIN, "seo",
                    RuleValues.MAXIMUM, 50.0), TREE_SEO, 1),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.SEVERITY, "critical"), PLATFORM));

    private static final List<ContentRules.RuleNode> SITE_NODES = List.of(
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.MAXIMUM, 70.0), SITE),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "brand.luxeOnly", RuleValues.PATTERN, "maison",
                    RuleValues.SITES, List.of("luxe")), SITE));

    private static ContentRules merge(ShippedRules.Shipment... enabled) {
        return merge(SITE_NODES, ContentDomains.base(), enabled);
    }

    private static ContentRules merge(List<ContentRules.RuleNode> siteNodes, ShippedRules.Shipment... enabled) {
        return merge(siteNodes, ContentDomains.base(), enabled);
    }

    private static ContentRules merge(List<ContentRules.RuleNode> siteNodes, ContentDomains hierarchy,
                                      ShippedRules.Shipment... enabled) {
        Set<String> shippedIds = new HashSet<>(ShippedRules.own().rules().keySet());
        shippedIds.addAll(ACME.rules().keySet());
        List<ShippedRules.Shipment> shipments = new ArrayList<>(List.of(ShippedRules.own()));
        shipments.addAll(List.of(enabled));
        return ContentRules.merge(shipments, shippedIds, PLATFORM_NODES, siteNodes, hierarchy);
    }

    /** The rules of a site with this layout, over the domains and the subdomains that this module ships. */
    private static ContentRules onSite(String siteKey, String layout) {
        Map<Level, Map<String, Map<String, Object>>> values = new EnumMap<>(Level.class);
        for (Level level : Level.values()) {
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            ShippedRules.own().of(level).forEach((id, file) -> byId.put(id, file.values()));
            values.put(level, byId);
        }
        return merge(SITE_NODES, new ContentDomains(siteKey, values, SiteLayout.parse(layout)));
    }

    @Test
    void aRuleOfSystemsite_appliesToEverySite() {
        ContentRule noClickHere = merge().get("brand.noClickHere");

        assertThat(noClickHere.folder()).isEqualTo(PLATFORM);
        assertThat(noClickHere.evaluator()).isEqualTo(ContentRule.PATTERN);
        assertThat(noClickHere.subdomain()).as("a pattern that names no subdomain").isEqualTo(ContentRules.TERMS);
        assertThat(noClickHere.enabled()).isTrue();
    }

    @Test
    void aNodeOfSystemsite_replacesTheShippedRule_andKeepsTheValuesItLacks() {
        ContentRule description = merge().get("seo.metaDescription");

        assertThat(description.maximum()).isEqualTo(140.0);
        assertThat(description.folder()).isEqualTo(TREE_SEO);
        assertThat(description.subdomain()).isEqualTo("seo");
        assertThat(description.evaluator()).isEqualTo(ContentRules.base().get("seo.metaDescription").evaluator());
    }

    @Test
    @DisplayName("a node of the site with the id of a rule of the system site changes nothing: a site does not change the values of a rule")
    void aNodeOfTheSite_doesNotReplaceTheNodeOfSystemsite() {
        ContentRule title = merge().get("seo.title");

        assertThat(title.maximum()).as("the maximum of the node of the system site, not 70").isNotEqualTo(70.0);
        assertThat(title.folder()).as("the node of the system site").isNotEqualTo(SITE);
        assertThat(title.subdomain()).as("the shipped subdomain").isEqualTo("seo");
        assertThat(merge().get("brand.luxeOnly").folder()).as("a rule of its own id still applies").isEqualTo(SITE);
    }

    @Test
    void aNodeOfSystemsite_ofARuleThatAModuleShips_appliesOnlyWhereTheSiteEnablesTheModule() {
        assertThat(merge().all()).extracting(ContentRule::id).doesNotContain("acme.erpPrice");

        ContentRule price = merge(ACME).get("acme.erpPrice");
        assertThat(price.severity()).isEqualTo("critical");
        assertThat(price.folder()).isEqualTo(PLATFORM);
        assertThat(price.subdomain()).isEqualTo("writing");
    }

    @Test
    @DisplayName("a node of the site with the id of a rule that a module ships changes nothing, with or without the module")
    void aNodeOfTheSite_ofARuleThatAModuleShips_changesNothing() {
        List<ContentRules.RuleNode> seeded = List.of(
                new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.SEVERITY, "minor"), SITE));

        assertThat(merge(seeded).all()).extracting(ContentRule::id).doesNotContain("acme.erpPrice");
        assertThat(merge(seeded, ACME).get("acme.erpPrice").severity()).as("the value of the system site").isEqualTo("critical");
    }

    @Test
    @DisplayName("with no layout, a rule of the tree takes its place among its siblings, and the other rules come after")
    void order_followsTheTree() {
        ContentRules rules = onSite("digitall", null);

        assertThat(rules.get("seo.metaDescription").order()).isEqualTo(ContentDomains.UNPLACED);
        assertThat(rules.get("seo.canonical").order()).as("shipped, with no node")
                .isEqualTo(ContentDomains.UNPLACED + ContentRules.OUTSIDE_TREE);
        assertThat(rules.get("brand.noClickHere").order()).isEqualTo(ContentDomains.UNPLACED + ContentRules.OUTSIDE_TREE);
    }

    @Test
    @DisplayName("the layout orders the rules that it places before the others")
    void layout_ordersTheRules() {
        ContentRules rules = onSite("digitall", """
                {"domains": [{"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title", "seo.metaDescription"]}]}]}
                """);

        assertThat(rules.get("seo.title").order()).isZero();
        assertThat(rules.get("seo.metaDescription").order()).isEqualTo(1);
        assertThat(rules.get("seo.canonical").order()).isGreaterThan(ContentDomains.UNPLACED);
        assertThat(rules.get("seo.canonical").subdomain()).isEqualTo("seo");
    }

    @Test
    @DisplayName("the layout moves a rule to another declared subdomain, and a subdomain that nobody declares leaves it in its own")
    void layout_movesARule() {
        ContentRule moved = onSite("digitall", """
                {"domains": [{"id": "quality", "subdomains": [{"id": "accessibility", "rules": ["seo.metaDescription"]}]}]}
                """).get("seo.metaDescription");

        assertThat(moved.subdomain()).isEqualTo("accessibility");
        assertThat(moved.placed()).isTrue();
        assertThat(moved.enabled()).isTrue();
        assertThat(moved.maximum()).as("the values stay those of the tree").isEqualTo(140.0);

        ContentRule kept = onSite("digitall", """
                {"domains": [{"id": "geo", "subdomains": [{"id": "acme.gone", "rules": ["seo.metaDescription"]}]}]}
                """).get("seo.metaDescription");
        assertThat(kept.subdomain()).isEqualTo("seo");
        assertThat(kept.enabled()).isTrue();
    }

    @Test
    @DisplayName("the layout switches a rule off, even one that it places, and the rule stays")
    void layout_switchesARuleOff() {
        ContentRules rules = onSite("digitall", """
                {"domains": [{"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title"]}]}],
                 "off": ["seo.title", "seo.metaDescription"]}
                """);

        assertThat(rules.get("seo.title").enabled()).isFalse();
        assertThat(rules.get("seo.metaDescription").enabled()).isFalse();
        assertThat(rules.get("seo.metaDescription").placed()).isTrue();
        assertThat(rules.get("seo.canonical").enabled()).isTrue();
    }

    @Test
    @DisplayName("a rule stays, switched off, when the layout switches its subdomain or its domain off, or its sites leave the site out")
    void aRuleThatDoesNotRun_staysSwitchedOff() {
        ContentRules seoOff = onSite("digitall", "{\"off\": [\"seo\"]}");
        ContentRules geoOff = onSite("digitall", "{\"off\": [\"geo\"]}");

        for (ContentRules rules : List.of(seoOff, geoOff)) {
            assertThat(rules.get("seo.title").enabled()).isFalse();
            assertThat(rules.get("seo.title").subdomain()).isEqualTo("seo");
            assertThat(rules.get("seo.title").placed()).isTrue();
        }
        assertThat(seoOff.get("geo.fetched").enabled()).as("another subdomain of geo").isTrue();
        assertThat(geoOff.get("geo.fetched").enabled()).isFalse();
        assertThat(seoOff.get("html.imageAltMissing").enabled()).isTrue();

        assertThat(onSite("digitall", null).get("brand.luxeOnly").enabled()).as("its sites name luxe only").isFalse();
        assertThat(onSite("luxe", null).get("brand.luxeOnly").enabled()).isTrue();
        assertThat(onSite("digitall", null).all()).extracting(ContentRule::id).contains("brand.luxeOnly");
    }

    @Test
    @DisplayName("a rule that the layout places runs, even when it is off by default or its sites leave the site out")
    void layout_placesOverTheDefaultSwitch() {
        ContentRules base = onSite("digitall", null);
        ContentRules placed = onSite("digitall", """
                {"domains": [{"id": "quality", "subdomains": [{"id": "accessibility", "rules": ["axe.css-orientation-lock"]}]},
                             {"id": "compliance", "subdomains": [{"id": "terms", "rules": ["brand.luxeOnly"]}]}]}
                """);

        assertThat(base.get("axe.css-orientation-lock").enabled()).as("experimental: off by default").isFalse();
        assertThat(placed.get("axe.css-orientation-lock").enabled()).isTrue();
        assertThat(placed.get("brand.luxeOnly").enabled()).isTrue();
        assertThat(placed.get("brand.luxeOnly").subdomain()).isEqualTo(ContentRules.TERMS);
    }
}
