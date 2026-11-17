package org.jahia.community.contentgovernance.audit.servlet;

import org.jahia.community.contentgovernance.ai.AiAnswer;
import org.jahia.community.contentgovernance.ai.AiException;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiRequest;
import org.jahia.community.contentgovernance.ai.AiService;
import org.jahia.community.contentgovernance.ai.AiStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The feature, the tab and the site of each task, from the grant to the request that AiService receives. The
 * page and its site come from the repository in {@code allowed}, which these tests do not reach.
 */
class AiReviewServletTasksTest {

    private static final AiStatus READY = new AiStatus(AiStatus.Availability.READY, "mistral", "mistral-small", true);

    private final AiService ai = mock(AiService.class);

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

    @ParameterizedTest
    @CsvSource({"review, REVIEW", "seo, SEO", "alt, ALT_TEXT", "simplify, SIMPLIFY"})
    void eachTaskSendsItsFeatureAndTheKeyOfItsSite(String task, AiFeature feature) throws Exception {
        when(ai.status(feature, "acme")).thenReturn(READY);
        when(ai.complete(any())).thenReturn(new AiAnswer("{}", false, 10, 20, null, "mistral", "mistral-small"));

        AiReviewServlet.Grant grant = AiReviewServlet.grantOf(ai, task, "acme");
        servlet().run(response(new StringWriter()), new JSONObject().put("path", "/sites/acme/home"), grant);

        assertThat(grant.refusal()).isNull();
        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).complete(sent.capture());
        assertThat(sent.getValue().feature()).isEqualTo(feature);
        assertThat(sent.getValue().siteKey()).isEqualTo("acme");
        assertThat(sent.getValue().maxOutputTokens()).isEqualTo(6000);
    }

    @Test
    void aUserWithoutGenaiAccessIsRefusedAsSuch() {
        when(ai.status(AiFeature.SEO, "acme")).thenReturn(AiStatus.unavailable(AiStatus.Availability.NO_ACCESS));

        assertThat(AiReviewServlet.grantOf(ai, "seo", "acme").refusal()).isEqualTo(AiException.Reason.NO_ACCESS);
    }

    @Test
    void withoutGenaiConnectorTheTaskIsNotConfigured() {
        when(ai.status(AiFeature.REVIEW, "acme")).thenReturn(AiStatus.unavailable(AiStatus.Availability.ABSENT));

        assertThat(AiReviewServlet.grantOf(ai, "review", "acme").refusal())
                .isEqualTo(AiException.Reason.NOT_CONFIGURED);
    }

    @Test
    void aRefusedCallAnswersTheReasonTheDrawerTranslates() throws Exception {
        when(ai.complete(any())).thenThrow(new AiException(AiException.Reason.NO_ACCESS, "genaiAccess required"));
        StringWriter body = new StringWriter();
        HttpServletResponse res = response(body);

        servlet().run(res, new JSONObject(), new AiReviewServlet.Grant("seo", AiFeature.SEO, "acme", false, null));

        verify(res).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(new JSONObject(body.toString()).getString("code")).isEqualTo("genaiAccess");
    }

    private AiReviewServlet servlet() {
        return new AiReviewServlet(ai);
    }

    private static HttpServletResponse response(StringWriter body) throws Exception {
        HttpServletResponse res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(body));
        return res;
    }
}
