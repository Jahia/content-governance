package org.jahia.community.contentgovernance.graphql;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.Domain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GqlContentDomainTest {

    @Test
    @DisplayName("a domain gives its texts in the language of the call and its subdomains by order")
    void domain_inALanguage() {
        ContentDomains hierarchy = ContentDomains.base();
        GqlContentDomain geo = new GqlContentDomain(hierarchy.domains().get(1), hierarchy, "fr", false);

        assertThat(geo.getId()).isEqualTo("geo");
        assertThat(geo.getLabel()).isEqualTo("SEO et GEO");
        assertThat(geo.getPanel()).isEqualTo("governance");
        assertThat(geo.getSites()).containsExactly("*");
        assertThat(geo.isOn()).isTrue();
        assertThat(geo.isNew()).as("the shipped hierarchy has no saved layout").isFalse();
        List<GqlContentSubdomain> subdomains = geo.getSubdomains();
        assertThat(subdomains).extracting(GqlContentSubdomain::getId).startsWith("geo.citations", "seo", "geo.score");
        assertThat(subdomains.get(0).getLabel()).isEqualTo("Citations IA");
        assertThat(subdomains.get(0).getDomain()).isEqualTo("geo");
        assertThat(subdomains.get(0).isOn()).isTrue();
        assertThat(subdomains.get(0).getCounts()).isEmpty();
        assertThat(subdomains.get(2).getCounts()).as("the rules that the GEO score counts")
                .contains("seo.title", "seo.metaDescription", "html.imageAltMissing");
        assertThat(new GqlContentDomain(hierarchy.domains().get(1), hierarchy, null, false).getLabel()).isEqualTo("SEO & GEO");
    }

    @Test
    @DisplayName("a domain lists the subdomains that are off for the site only with includeOff, each with its switch")
    void domain_listsTheSubdomainsThatAreOffWithIncludeOff() {
        ContentDomains base = ContentDomains.base();
        Domain geo = base.domains().get(1);
        Domain citations = base.allSubdomainsOf("geo").get(0);
        Domain seo = base.allSubdomainsOf("geo").get(1);
        ContentDomains hierarchy = mock(ContentDomains.class);
        when(hierarchy.subdomainsOf("geo")).thenReturn(List.of(seo));
        when(hierarchy.allSubdomainsOf("geo")).thenReturn(List.of(citations, seo));
        when(hierarchy.isOn("seo")).thenReturn(true);
        when(hierarchy.isNew("geo.citations")).thenReturn(true);

        assertThat(new GqlContentDomain(geo, hierarchy, null, false).getSubdomains())
                .extracting(GqlContentSubdomain::getId).containsExactly("seo");
        assertThat(new GqlContentDomain(geo, hierarchy, null, true).getSubdomains())
                .extracting(GqlContentSubdomain::getId, GqlContentSubdomain::isOn, GqlContentSubdomain::isNew)
                .containsExactly(tuple("geo.citations", false, true),
                        tuple("seo", true, false));
    }

    @Test
    @DisplayName("a rule gives its subdomain, its place and its sites, and no unknown subdomain when it is placed")
    void rule_givesItsSubdomainAndSites() {
        ContentRule title = ContentRules.base().get("seo.title");
        GqlContentRule rule = new GqlContentRule(title, new CheckRegistry().status(title), false);

        assertThat(rule.getSubdomain()).isEqualTo("seo");
        assertThat(rule.getOrder()).isEqualTo(title.order());
        assertThat(rule.getSites()).containsExactly("*");
        assertThat(rule.getUnknownSubdomain()).isNull();
        assertThat(rule.isNew()).isFalse();
        assertThat(new GqlContentRule(title, new CheckRegistry().status(title), false, "fr", true, true).isNew()).isTrue();
    }
}
