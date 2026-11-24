package org.jahia.community.contentgovernance.settings;

import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.Hierarchies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The gate of a feature, read from the layout of the site. */
class SiteFeaturesTest {

    private static ContentDomains site(String layout) {
        return Hierarchies.site("digitall", layout);
    }

    @Test
    @DisplayName("a site with no layout keeps every domain and subdomain at its default")
    void noLayout_keepsTheDefaults() {
        ContentDomains site = site(null);

        assertThat(SiteFeatures.isOn(site, "quality")).isTrue();
        assertThat(SiteFeatures.isOn(site, "geo.citations")).isTrue();
        assertThat(SiteFeatures.isOn(site, "legacy")).as("off by default").isFalse();
        assertThat(SiteFeatures.isOn(site, "old")).as("in a domain that is off").isFalse();
    }

    @Test
    @DisplayName("a subdomain is off when the layout switches it or its domain off")
    void off_switchesAnItemAndItsSubdomains() {
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"geo.citations\"]}"), "geo.citations")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"geo.citations\"]}"), "geo")).isTrue();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"geo\"]}"), "seo")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"geo\"]}"), "health")).isTrue();
    }

    @Test
    @DisplayName("a panel that is off switches off the domains it shows, and only those")
    void panelOff_switchesItsDomainsOff() {
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"governance\"]}"), "quality")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"governance\"]}"), "seo")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"governance\"]}"), "answers")).isTrue();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"chat\"]}"), "answers")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"chat\"]}"), "quality")).isTrue();
    }

    @Test
    @DisplayName("a tab that another module adds is off with the governance panel, itself or its section")
    void otherTab_followsTheGovernancePanel() {
        assertThat(SiteFeatures.isOn(site(null), "aiAssistantReview")).isTrue();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"governance\"]}"), "aiAssistantReview")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"aiAssistantReview\"]}"), "aiAssistantReview.part")).isFalse();
        assertThat(SiteFeatures.isOn(site("{\"off\":[\"chat\"]}"), "aiAssistantReview")).isTrue();
    }

    @Test
    @DisplayName("the drawer reads the ids that the layout switches off and every domain and subdomain that is off")
    void off_listsWhatIsOff() {
        assertThat(SiteFeatures.off(site("{\"off\":[\"chat\",\"geo\"]}")))
                .containsExactly("chat", "geo", "seo", "geo.citations", "assist", "answers", "legacy", "old");
    }
}
