package org.jahia.community.contentgovernance.graphql;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GqlContentDomainTest {

    @Test
    @DisplayName("a domain gives its texts in the language of the call and its subdomains by order")
    void domain_inALanguage() {
        ContentDomains hierarchy = ContentDomains.base();
        GqlContentDomain geo = new GqlContentDomain(hierarchy.domains().get(1), hierarchy, "fr");

        assertThat(geo.getId()).isEqualTo("geo");
        assertThat(geo.getLabel()).isEqualTo("SEO et GEO");
        assertThat(geo.getPanel()).isEqualTo("governance");
        assertThat(geo.getSites()).containsExactly("*");
        List<GqlContentSubdomain> subdomains = geo.getSubdomains();
        assertThat(subdomains).extracting(GqlContentSubdomain::getId).startsWith("geo.citations", "seo", "geo.score");
        assertThat(subdomains.get(0).getLabel()).isEqualTo("Citations IA");
        assertThat(subdomains.get(0).getDomain()).isEqualTo("geo");
        assertThat(new GqlContentDomain(hierarchy.domains().get(1), hierarchy, null).getLabel()).isEqualTo("SEO & GEO");
    }

    @Test
    @DisplayName("a rule gives its subdomains and its sites, and no unknown subdomain when it is placed")
    void rule_givesItsSubdomainsAndSites() {
        ContentRule title = ContentRules.base().get("seo.title");
        GqlContentRule rule = new GqlContentRule(title, new CheckRegistry().status(title), false);

        assertThat(rule.getSubdomains()).containsExactly("seo", "geo.score");
        assertThat(rule.getSites()).containsExactly("*");
        assertThat(rule.getUnknownSubdomains()).isNull();
    }
}
