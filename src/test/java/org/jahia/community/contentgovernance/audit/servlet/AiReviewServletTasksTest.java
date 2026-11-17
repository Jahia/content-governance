package org.jahia.community.contentgovernance.audit.servlet;

import org.jahia.community.contentgovernance.ai.AiFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewServletTasksTest {

    @ParameterizedTest
    @CsvSource({
            "seo, SEO, seo",
            "alt, ALT_TEXT, accessibility",
            "simplify, SIMPLIFY, readability",
            "review, REVIEW, ai",
            "anything else, REVIEW, ai"
    })
    void eachTaskNamesItsFeatureAndTheTabThatSwitchesItOff(String task, AiFeature feature, String tab) {
        assertThat(AiReviewServlet.featureOf(task)).isEqualTo(feature);
        assertThat(AiReviewServlet.tabOf(task)).isEqualTo(tab);
    }
}
