package org.jahia.community.contentgovernance.settings;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeaturesTest {

    @Test
    void knowsSectionsAndTheirTabsOnly() {
        assertThat(Features.isKnown("geo")).isTrue();
        assertThat(Features.isKnown("geo.citations")).isTrue();
        assertThat(Features.isKnown("audit.ai")).isTrue();
        assertThat(Features.isKnown("compliance")).isTrue();
        assertThat(Features.isKnown("geo.ai")).isFalse();
        assertThat(Features.isKnown("compliance.report")).isFalse();
        assertThat(Features.isKnown("other")).isFalse();
        assertThat(Features.isKnown(null)).isFalse();
    }

    @Test
    void sectionOfATabIsThePartBeforeTheDot() {
        assertThat(Features.sectionOf("audit.seo")).isEqualTo("audit");
        assertThat(Features.sectionOf("geo")).isEqualTo("geo");
    }

    @Test
    void aTabIsOffWhenItOrItsSectionIsOff() {
        assertThat(SiteFeatures.isOn(Set.of(), "geo.citations")).isTrue();
        assertThat(SiteFeatures.isOn(Set.of("geo.citations"), "geo.citations")).isFalse();
        assertThat(SiteFeatures.isOn(Set.of("geo.citations"), "geo")).isTrue();
        assertThat(SiteFeatures.isOn(Set.of("geo"), "geo.score")).isFalse();
        assertThat(SiteFeatures.isOn(Set.of("geo"), "audit.seo")).isTrue();
    }
}
