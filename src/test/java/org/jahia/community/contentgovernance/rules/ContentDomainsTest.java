package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The hierarchy of a site, and the rules that it places. */
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
        return new ContentDomains(siteKey, values);
    }

    private static ContentRule rule(String id, List<String> subdomains, String... sites) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(RuleValues.RULE_ID, id);
        values.put(RuleValues.EVALUATOR, "threshold");
        if (!subdomains.isEmpty()) {
            values.put(RuleValues.SUBDOMAINS, subdomains);
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
    }

    @Test
    @DisplayName("a rule applies to a site when the rule, one of its subdomains and that subdomain's domain include the site")
    void scoped_followsTheHierarchy() {
        Map<String, ContentRule> rules = new LinkedHashMap<>();
        rules.put("seo.title", rule("seo.title", List.of("seo")));
        rules.put("brand.voice", rule("brand.voice", List.of("tone")));
        rules.put("legal.gdpr", rule("legal.gdpr", List.of("legal", "seo")));
        rules.put("seo.slug", rule("seo.slug", List.of("seo"), "digitall"));
        rules.put("acme.price", rule("acme.price", List.of("acme.prices")));
        rules.put("acme.lost", rule("acme.lost", List.of("lost")));

        Map<String, ContentRule> luxe = ContentRules.scoped(rules, hierarchy("luxe"));

        assertThat(luxe).containsOnlyKeys("seo.title", "legal.gdpr", "acme.price", "acme.lost");
        assertThat(luxe.get("seo.title").placed()).isTrue();
        assertThat(luxe.get("legal.gdpr").placed()).isTrue();
        assertThat(luxe.get("acme.price").placed()).as("nobody declares its subdomain: it stays, and never runs").isFalse();
        assertThat(luxe.get("acme.lost").placed()).isFalse();
        Map<String, ContentRule> digitall = ContentRules.scoped(rules, hierarchy("digitall"));
        assertThat(digitall).containsKeys("brand.voice", "seo.slug", "legal.gdpr");
        assertThat(digitall.get("legal.gdpr").placed()).as("its subdomain seo has a place on digitall").isTrue();
        assertThat(ContentRules.scoped(rules, hierarchy(null))).as("the shipped hierarchy places every declared one")
                .hasSize(rules.size());
    }

    @Test
    @DisplayName("a rule that names no subdomain takes the one of the prefix of its id, else terms for a pattern")
    void defaultSubdomains_followTheId() {
        ContentDomains luxe = hierarchy("luxe");

        assertThat(ContentRules.defaultSubdomains("seo.slug", "threshold", luxe)).containsExactly("seo");
        assertThat(ContentRules.defaultSubdomains("brand.seamless", "pattern", luxe)).containsExactly(ContentRules.TERMS);
        assertThat(ContentRules.defaultSubdomains("brand.tone", "model", luxe)).isEmpty();
    }
}
