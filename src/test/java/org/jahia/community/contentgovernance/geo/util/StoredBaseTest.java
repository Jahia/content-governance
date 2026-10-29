package org.jahia.community.contentgovernance.geo.util;

import org.jahia.services.content.decorator.JCRSiteNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which bases a scheduled scan may fetch from, and which scopes lie inside a site. */
class StoredBaseTest {

    private JCRSiteNode site;

    @BeforeEach
    void site() {
        site = mock(JCRSiteNode.class);
        when(site.getServerName()).thenReturn("www.example.com");
        when(site.getServerNameAliases()).thenReturn(Collections.singletonList("example.com"));
        FetchGuard.trustBase("");
    }

    @AfterEach
    void reset() {
        FetchGuard.trustBase("");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.example.com", "http://www.example.com:8080/", "https://EXAMPLE.com",
            PublicUrls.LOCAL_FALLBACK})
    void acceptsTheSiteOwnNamesAndTheLocalFallback(String base) {
        assertThat(PublicUrls.acceptsStoredBase(site, base)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.example.org", "ftp://www.example.com", "https://user@www.example.com",
            "https://www.example.com.other.org", "not a url", " "})
    void refusesEverythingElse(String base) {
        assertThat(PublicUrls.acceptsStoredBase(site, base)).isFalse();
    }

    @Test
    void acceptsTheConfiguredPublicBase() {
        FetchGuard.trustBase("http://host.docker.internal:9090/");
        assertThat(PublicUrls.acceptsStoredBase(site, "http://host.docker.internal:9090")).isTrue();
    }

    @Test
    void scopeInsideTheSiteOnly() {
        assertThat(SiteScope.isWithin("/sites/ab", "/sites/ab")).isTrue();
        assertThat(SiteScope.isWithin("/sites/ab", "/sites/ab/home/news")).isTrue();
        assertThat(SiteScope.isWithin("/sites/ab", "/sites/abc")).isFalse();
        assertThat(SiteScope.isWithin("/sites/ab", "/sites")).isFalse();
        assertThat(SiteScope.isWithin("/sites/ab", null)).isFalse();
    }
}
