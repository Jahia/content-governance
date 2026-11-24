package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The hierarchy of a site, its layout, and the rules that it places. */
class ContentDomainsTest {

    private static Map<String, Object> domain(String id, int order, String... sites) {
        return Map.of(DomainValues.ID, id, DomainValues.ORDER, (long) order, DomainValues.LABEL, id,
                DomainValues.SITES, sites.length == 0 ? List.of("*") : List.of(sites));
    }

    private static Map<String, Object> subdomain(String id, String parent, int order, String... sites) {
        return Map.of(DomainValues.ID, id, DomainValues.DOMAIN, parent, DomainValues.ORDER, (long) order,
                DomainValues.LABEL, id, DomainValues.SITES, sites.length == 0 ? List.of("*") : List.of(sites));
    }

    /**
     * quality (all sites) › seo, accessibility; brand (digitall only) › tone; compliance (all sites) › legal
     * (luxe only); lost › none of a declared domain.
     */
    private static ContentDomains hierarchy(String siteKey) {
        return hierarchy(siteKey, SiteLayout.NONE);
    }

    private static ContentDomains hierarchy(String siteKey, SiteLayout layout) {
        Map<String, Map<String, Object>> domains = new LinkedHashMap<>();
        domains.put("quality", domain("quality", 10));
        domains.put("brand", domain("brand", 5, "digitall"));
        domains.put("compliance", domain("compliance", 30));
        Map<String, Map<String, Object>> subdomains = new LinkedHashMap<>();
        subdomains.put("seo", subdomain("seo", "quality", 20));
        subdomains.put("accessibility", subdomain("accessibility", "quality", 10));
        subdomains.put("tone", subdomain("tone", "brand", 10));
        subdomains.put("legal", subdomain("legal", "compliance", 10, "luxe"));
        subdomains.put("lost", subdomain("lost", "nowhere", 10));
        Map<Level, Map<String, Map<String, Object>>> values = new EnumMap<>(Level.class);
        values.put(Level.DOMAIN, domains);
        values.put(Level.SUBDOMAIN, subdomains);
        return new ContentDomains(siteKey, values, layout);
    }

    private static ContentRule rule(String id, String subdomain, String... sites) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(RuleValues.RULE_ID, id);
        values.put(RuleValues.EVALUATOR, "threshold");
        if (subdomain != null) {
            values.put(RuleValues.SUBDOMAIN, subdomain);
        }
        if (sites.length > 0) {
            values.put(RuleValues.SITES, List.of(sites));
        }
        return RuleValues.toRule(values);
    }

    @Test
    @DisplayName("a site sees the domains of its sites by order, and the subdomains of those domains that include it")
    void site_seesItsHierarchy() {
        ContentDomains digitall = hierarchy("digitall");
        ContentDomains luxe = hierarchy("luxe");

        assertThat(digitall.domains()).extracting(Domain::id).containsExactly("brand", "quality", "compliance");
        assertThat(luxe.domains()).extracting(Domain::id).containsExactly("quality", "compliance");
        assertThat(digitall.subdomainsOf("quality")).extracting(Domain::id).containsExactly("accessibility", "seo");
        assertThat(digitall.subdomainsOf("compliance")).isEmpty();
        assertThat(luxe.subdomainsOf("compliance")).extracting(Domain::id).containsExactly("legal");
        assertThat(luxe.allDomains()).as("every domain, on or off").extracting(Domain::id)
                .containsExactly("brand", "quality", "compliance");
        assertThat(digitall.allSubdomainsOf("compliance")).extracting(Domain::id).containsExactly("legal");
    }

    @Test
    @DisplayName("a subdomain has a place on a site when it and its domain include the site")
    void places_needTheSubdomainAndItsDomain() {
        ContentDomains luxe = hierarchy("luxe");

        assertThat(luxe.places("seo")).isTrue();
        assertThat(luxe.places("tone")).as("its domain brand is of digitall only").isFalse();
        assertThat(luxe.places("legal")).isTrue();
        assertThat(hierarchy("digitall").places("legal")).isFalse();
        assertThat(luxe.known("tone")).isTrue();
        assertThat(luxe.known("lost")).as("its domain is not declared").isFalse();
        assertThat(luxe.known("acme.prices")).isFalse();
        assertThat(luxe.isOn("lost")).isFalse();
        assertThat(luxe.allSubdomainsOf("quality")).extracting(Domain::id).contains("seo");
        assertThat(luxe.allDomains()).flatExtracting(domain -> luxe.allSubdomainsOf(domain.id())).extracting(Domain::id).doesNotContain("acme.prices");
    }

    @Test
    @DisplayName("every rule stays, placed in its subdomain, and runs on a site when the rule, its subdomain and that subdomain's domain include the site")
    void scoped_followsTheHierarchy() {
        Map<String, ContentRule> rules = new LinkedHashMap<>();
        rules.put("seo.title", rule("seo.title", "seo"));
        rules.put("brand.voice", rule("brand.voice", "tone"));
        rules.put("legal.gdpr", rule("legal.gdpr", "legal"));
        rules.put("seo.slug", rule("seo.slug", "seo", "digitall"));
        rules.put("acme.price", rule("acme.price", "acme.prices"));
        rules.put("acme.lost", rule("acme.lost", "lost"));

        Map<String, ContentRule> luxe = ContentRules.scoped(rules, hierarchy("luxe"));

        assertThat(luxe).as("a rule that does not run on the site stays, switched off").containsOnlyKeys(rules.keySet());
        assertThat(luxe.values()).filteredOn(ContentRule::enabled).extracting(ContentRule::id)
                .containsExactly("seo.title", "legal.gdpr");
        assertThat(luxe.get("seo.title").placed()).isTrue();
        assertThat(luxe.get("brand.voice").placed()).as("its subdomain is declared, off on luxe").isTrue();
        assertThat(luxe.get("seo.slug").placed()).isTrue();
        assertThat(luxe.get("acme.price").placed()).as("nobody declares its subdomain: it stays, and never runs").isFalse();
        assertThat(luxe.get("acme.lost").placed()).isFalse();
        Map<String, ContentRule> digitall = ContentRules.scoped(rules, hierarchy("digitall"));
        assertThat(digitall.values()).filteredOn(ContentRule::enabled).extracting(ContentRule::id)
                .containsExactly("seo.title", "brand.voice", "seo.slug");
        assertThat(ContentRules.scoped(rules, hierarchy(null)).values()).as("the shipped hierarchy runs every declared one")
                .filteredOn(ContentRule::enabled).extracting(ContentRule::id)
                .containsExactly("seo.title", "brand.voice", "legal.gdpr", "seo.slug");
    }

    @Test
    @DisplayName("a rule that names no subdomain takes the one of the prefix of its id, else terms for a pattern")
    void defaultSubdomain_followsTheId() {
        ContentDomains luxe = hierarchy("luxe");

        assertThat(ContentRules.defaultSubdomain("seo.slug", "threshold", luxe)).isEqualTo("seo");
        assertThat(ContentRules.defaultSubdomain("brand.seamless", "pattern", luxe)).isEqualTo(ContentRules.TERMS);
        assertThat(ContentRules.defaultSubdomain("brand.tone", "model", luxe)).isNull();
        assertThat(ContentRules.defaultSubdomain("lost.x", "model", luxe)).as("its domain is not declared").isNull();
    }

    @Test
    @DisplayName("the layout orders the domains and the subdomains, and the items that it does not place come after, in their default order")
    void layout_ordersTheItems() {
        SiteLayout layout = SiteLayout.parse("""
                {"domains": [{"id": "compliance", "subdomains": [{"id": "legal"}]},
                             {"id": "quality", "subdomains": [{"id": "seo"}, {"id": "accessibility"}]}]}
                """);

        ContentDomains luxe = hierarchy("luxe", layout);

        assertThat(luxe.domains()).extracting(Domain::id).containsExactly("compliance", "quality");
        assertThat(luxe.subdomainsOf("quality")).extracting(Domain::id).containsExactly("seo", "accessibility");
        assertThat(luxe.allDomains()).extracting(Domain::id).containsExactly("compliance", "quality", "brand");
        assertThat(luxe.allDomains().get(2).order()).as("not placed: after the placed ones, in its default order")
                .isEqualTo(ContentDomains.UNPLACED + 5);
        assertThat(luxe.layout()).isSameAs(layout);
    }

    @Test
    @DisplayName("the layout moves a subdomain to another declared domain, and a domain that nobody declares leaves it in place")
    void layout_movesASubdomain() {
        ContentDomains moved = hierarchy("luxe", SiteLayout.parse("""
                {"domains": [{"id": "compliance", "subdomains": [{"id": "seo"}]}]}
                """));

        assertThat(moved.subdomainsOf("compliance")).extracting(Domain::id).containsExactly("seo", "legal");
        assertThat(moved.subdomainsOf("quality")).extracting(Domain::id).containsExactly("accessibility");
        assertThat(moved.allSubdomainsOf("compliance")).extracting(Domain::id).contains("seo");
        assertThat(moved.places("seo")).isTrue();

        ContentDomains nowhere = hierarchy("luxe", SiteLayout.parse("""
                {"domains": [{"id": "acme", "subdomains": [{"id": "seo"}]}]}
                """));
        assertThat(nowhere.allSubdomainsOf("quality")).extracting(Domain::id).contains("seo");
        assertThat(nowhere.subdomainsOf("quality")).extracting(Domain::id).contains("seo");
    }

    @Test
    @DisplayName("the layout switches a subdomain or a domain off: it leaves the lists of what is on, and its subdomains go off with it")
    void layout_switchesOff() {
        ContentDomains seoOff = hierarchy("luxe", SiteLayout.parse("{\"off\": [\"seo\"]}"));

        assertThat(seoOff.subdomainsOf("quality")).extracting(Domain::id).containsExactly("accessibility");
        assertThat(seoOff.allSubdomainsOf("quality")).extracting(Domain::id).containsExactly("accessibility", "seo");
        assertThat(seoOff.isOn("seo")).isFalse();
        assertThat(seoOff.places("seo")).isFalse();
        assertThat(seoOff.known("seo")).as("still declared").isTrue();

        ContentDomains qualityOff = hierarchy("luxe", SiteLayout.parse("""
                {"domains": [{"id": "quality", "subdomains": [{"id": "seo"}]}], "off": ["quality"]}
                """));
        assertThat(qualityOff.domains()).extracting(Domain::id).containsExactly("compliance");
        assertThat(qualityOff.allDomains()).extracting(Domain::id).contains("quality");
        assertThat(qualityOff.isOn("seo")).as("placed, but its domain is off").isFalse();
        assertThat(qualityOff.isOn("accessibility")).isFalse();
    }

    @Test
    @DisplayName("the layout switches on what it places, whatever the default sites, and what it does not place keeps its default switch")
    void layout_placesOverTheDefaultSites() {
        ContentDomains luxe = hierarchy("luxe", SiteLayout.parse("""
                {"domains": [{"id": "brand", "subdomains": [{"id": "tone"}]}]}
                """));

        assertThat(luxe.isOn("brand")).as("of digitall only by default").isTrue();
        assertThat(luxe.isOn("tone")).isTrue();
        assertThat(luxe.places("tone")).isTrue();
        assertThat(luxe.isOn("legal")).as("not placed: of luxe by default").isTrue();
        assertThat(luxe.domains()).extracting(Domain::id).containsExactly("brand", "quality", "compliance");

        ContentDomains digitall = hierarchy("digitall", SiteLayout.parse("""
                {"domains": [{"id": "quality", "subdomains": [{"id": "seo"}]}]}
                """));
        assertThat(digitall.isOn("legal")).as("not placed: of luxe only by default").isFalse();
        assertThat(digitall.isOn("brand")).as("not placed: of digitall by default").isTrue();
    }

    @Test
    @DisplayName("an item is new when the site saved a layout that names it neither in an array nor in off, and nothing is new without a saved layout")
    void isNew_followsTheSavedLayout() {
        ContentDomains saved = hierarchy("luxe", SiteLayout.parse("""
                {"domains": [{"id": "quality", "subdomains": [{"id": "seo"}]}], "off": ["accessibility"]}
                """));

        assertThat(saved.isNew("quality")).isFalse();
        assertThat(saved.isNew("seo")).isFalse();
        assertThat(saved.isNew("accessibility")).as("switched off: the layout names it").isFalse();
        assertThat(saved.isNew("compliance")).isTrue();
        assertThat(hierarchy("luxe").isNew("compliance")).isFalse();
        assertThat(hierarchy("luxe", SiteLayout.parse("{}")).isNew("quality")).as("a saved layout that places nothing marks nothing").isFalse();
    }

    @Test
    @DisplayName("a domain or a subdomain that is switched off by default is off on a site that does not place it")
    void enabled_isTheDefaultSwitch() {
        ContentDomains none = Hierarchies.site("digitall", null);
        ContentDomains placed = Hierarchies.site("digitall", """
                {"domains": [{"id": "legacy", "subdomains": [{"id": "old"}]}]}
                """);

        assertThat(none.isOn("legacy")).isFalse();
        assertThat(none.isOn("old")).as("its domain is off").isFalse();
        assertThat(none.switchedOn("old")).as("its own switch is on").isTrue();
        assertThat(placed.isOn("legacy")).as("the layout places it").isTrue();
        assertThat(placed.isOn("old")).isTrue();
    }

    @Test
    @DisplayName("the switch of an item itself follows the layout, else its default, and the default switch on the system site")
    void switchedOn_isTheSwitchOfTheItemItself() {
        ContentDomains geoOff = Hierarchies.site("digitall", "{\"off\": [\"geo\"]}");

        assertThat(geoOff.switchedOn("geo")).isFalse();
        assertThat(geoOff.switchedOn("seo")).as("its domain is off, its own switch is on").isTrue();
        assertThat(geoOff.isOn("seo")).isFalse();
        assertThat(geoOff.switchedOn("unknown")).isFalse();
        ContentDomains system = Hierarchies.site(ContentDomains.SYSTEM_SITE_KEY, null);
        assertThat(system.switchedOn("legacy")).isFalse();
        assertThat(system.switchedOn("quality")).isTrue();
    }

    @Test
    @DisplayName("an item is in the panel of its domain, wherever the layout places it")
    void panelOf_isThePanelOfTheDomain() {
        ContentDomains moved = Hierarchies.site("digitall", """
                {"domains": [{"id": "assist", "subdomains": [{"id": "seo"}]}]}
                """);

        assertThat(moved.panelOf("assist")).isEqualTo("chat");
        assertThat(moved.panelOf("seo")).as("placed in assist").isEqualTo("chat");
        assertThat(moved.panelOf("health")).isEqualTo("governance");
        assertThat(moved.panelOf("unknown")).isNull();
        assertThat(moved.declares("seo")).isTrue();
        assertThat(moved.declares("chat")).isFalse();
    }
}
