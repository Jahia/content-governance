package org.jahia.community.contentgovernance.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeaturesTest {

    @Test
    void acceptsTheIdOfAnyRegisteredTabAndOfItsParts() {
        assertThat(Features.isValid("geo")).isTrue();
        assertThat(Features.isValid("geo.citations")).isTrue();
        assertThat(Features.isValid("aiAssistantReview")).isTrue();
        assertThat(Features.isValid("my-module_tab.part-2")).isTrue();
        assertThat(Features.isValid("a".repeat(64))).isTrue();
    }

    @Test
    void refusesAnIdOfAnotherForm() {
        assertThat(Features.isValid(null)).isFalse();
        assertThat(Features.isValid("")).isFalse();
        assertThat(Features.isValid("a".repeat(65))).isFalse();
        assertThat(Features.isValid("geo.score.detail")).isFalse();
        assertThat(Features.isValid("geo.")).isFalse();
        assertThat(Features.isValid("1tab")).isFalse();
        assertThat(Features.isValid("my module")).isFalse();
        assertThat(Features.isValid("../audits")).isFalse();
        assertThat(Features.isValid("tab:review")).isFalse();
    }

    @Test
    void aTabHasNoPart() {
        assertThat(Features.isTab("aiAssistantReview")).isTrue();
        assertThat(Features.isTab("geo.citations")).isFalse();
        assertThat(Features.isTab("run/../x")).isFalse();
    }

    @Test
    void sectionOfATabIsThePartBeforeTheDot() {
        assertThat(Features.sectionOf("audit.seo")).isEqualTo("audit");
        assertThat(Features.sectionOf("geo")).isEqualTo("geo");
    }
}
