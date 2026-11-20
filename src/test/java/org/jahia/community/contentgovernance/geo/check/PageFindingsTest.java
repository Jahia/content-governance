package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.ThresholdCheck;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jahia.community.contentgovernance.page.SeoChecks;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageFindingsTest {

    private static final String PATH = "/sites/digitall/home";

    /** The registry of the server with the checks that the HTML checks of GEO readiness apply. */
    private static CheckRegistry registry() {
        CheckRegistry registry = new CheckRegistry();
        HtmlRuleChecks.checks().forEach((id, check) -> Registries.bind(registry, check, id));
        SeoChecks.checks().forEach((id, check) -> Registries.bind(registry, check, id));
        Registries.bind(registry, new ThresholdCheck(), ContentRule.THRESHOLD);
        return registry;
    }

    @Test
    @DisplayName("a complete page breaks none of the rules of the HTML checks of GEO readiness")
    void completePage_breaksNothing() {
        String html = "<html lang='en'><head><title>Annual report of the Digitall company 2026</title>"
                + "<meta name='description' content='The results, the people and the plans of Digitall for the next year.'>"
                + "<link rel='canonical' href='/home'><script type='application/ld+json'>{\"@type\":\"Report\"}</script>"
                + "</head><body><h1>Report</h1><img src='a.png' alt='Chart'></body></html>";

        assertThat(PageFindings.of(registry(), ContentRules.base(), PATH, "en", html).toList()).isEmpty();
    }

    @Test
    @DisplayName("a bare page breaks each rule of the HTML checks of GEO readiness, and no other")
    void barePage_breaksEachRule() {
        String html = "<html><head></head><body><h2>Part</h2><img src='a.png'></body></html>";

        assertThat(PageFindings.of(registry(), ContentRules.base(), PATH, "en", html).toList())
                .containsExactlyInAnyOrderElementsOf(PageFindings.RULE_IDS.stream()
                        .filter(id -> !id.equals("html.headingH1Multiple")).toList());
    }
}
