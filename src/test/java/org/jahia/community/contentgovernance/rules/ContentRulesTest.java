package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The order of the rules of a site: the shipped rules, then the nodes of systemsite, then the nodes of the site. */
class ContentRulesTest {

    private static final String PLATFORM = "/sites/systemsite/contents/content-insights/brand/rules";
    private static final String SITE = "/sites/digitall/contents/content-insights/site/rules";

    /** A module that the site may enable, and that ships one rule. */
    private static final ShippedRules.Shipment ACME = new ShippedRules.Shipment("acme", Map.of("acme.erpPrice",
            new RuleFile("acme.erpPrice", Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                    RuleValues.DOMAIN, "writing"))), true);

    private static final List<ContentRules.RuleNode> PLATFORM_NODES = List.of(
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "brand.noClickHere", RuleValues.PATTERN, "click here"), PLATFORM),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.MAXIMUM, 50.0), PLATFORM),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.metaDescription", RuleValues.MAXIMUM, 140.0), PLATFORM),
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.SEVERITY, "critical"), PLATFORM));

    private static final List<ContentRules.RuleNode> SITE_NODES = List.of(
            new ContentRules.RuleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.MAXIMUM, 70.0), SITE));

    private static ContentRules merge(ShippedRules.Shipment... enabled) {
        Set<String> shippedIds = new HashSet<>(ShippedRules.own().rules().keySet());
        shippedIds.addAll(ACME.rules().keySet());
        List<ShippedRules.Shipment> shipments = new ArrayList<>(List.of(ShippedRules.own()));
        shipments.addAll(List.of(enabled));
        return ContentRules.merge(shipments, shippedIds, PLATFORM_NODES, SITE_NODES);
    }

    @Test
    void aRuleOfSystemsite_appliesToEverySite() {
        ContentRule noClickHere = merge().get("brand.noClickHere");

        assertThat(noClickHere.folder()).isEqualTo(PLATFORM);
        assertThat(noClickHere.evaluator()).isEqualTo(ContentRule.PATTERN);
    }

    @Test
    void aNodeOfSystemsite_replacesTheShippedRule_andKeepsTheValuesItLacks() {
        ContentRule description = merge().get("seo.metaDescription");

        assertThat(description.maximum()).isEqualTo(140.0);
        assertThat(description.folder()).isEqualTo(PLATFORM);
        assertThat(description.evaluator()).isEqualTo(ContentRules.base().get("seo.metaDescription").evaluator());
    }

    @Test
    void aNodeOfTheSite_replacesTheNodeOfSystemsite() {
        ContentRule title = merge().get("seo.title");

        assertThat(title.maximum()).isEqualTo(70.0);
        assertThat(title.folder()).isEqualTo(SITE);
    }

    @Test
    void aNodeOfSystemsite_ofARuleThatAModuleShips_appliesOnlyWhereTheSiteEnablesTheModule() {
        assertThat(merge().all()).extracting(ContentRule::id).doesNotContain("acme.erpPrice");

        ContentRule price = merge(ACME).get("acme.erpPrice");
        assertThat(price.severity()).isEqualTo("critical");
        assertThat(price.folder()).isEqualTo(PLATFORM);
    }
}
