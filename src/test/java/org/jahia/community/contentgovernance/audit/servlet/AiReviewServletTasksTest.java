package org.jahia.community.contentgovernance.audit.servlet;

import org.jahia.community.contentgovernance.ai.AiFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feature and the tab of each task. The site key that a call sends is not covered here: {@code allowed}
 * and {@code run} read the repository through {@code JCRSessionFactory}, which these tests cannot mock, because
 * the pom excludes every dependency of jahia-impl (Mockito fails on {@code javax.validation.ValidatorFactory}).
 * {@code run} sends {@code site.getSiteKey()} of the page, the key that {@code allowed} asked the status for, and
 * genai-connector refuses a key on which the user lacks genaiAccess.
 */
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
