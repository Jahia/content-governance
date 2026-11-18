package org.jahia.community.contentgovernance.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private static final AiStatus READY = new AiStatus(AiStatus.Availability.READY, "mistral", "mistral-medium", true);

    private static AiRequest request() {
        return new AiRequest(AiFeature.REVIEW, "acme", "Review the page.", "PAGE TEXT", List.of(), 6000);
    }

    @Test
    void withoutGenaiConnectorEveryFeatureIsAbsent() {
        AiService ai = new AiService(site -> true);

        assertThat(ai.status(AiFeature.REVIEW, "acme").availability()).isEqualTo(AiStatus.Availability.ABSENT);
        assertThat(ai.status(AiFeature.GEO_REPORT, "acme").provider()).isEmpty();
    }

    @Test
    void aTierWithNoModelIsNotConfiguredWhateverTheAccess() {
        AiService ai = new AiService(site -> false);
        AiModel model = mock(AiModel.class);
        when(model.status(AiFeature.SEO)).thenReturn(AiStatus.unavailable(AiStatus.Availability.NOT_CONFIGURED));
        ai.bindModel(model);

        assertThat(ai.status(AiFeature.SEO, "acme").availability()).isEqualTo(AiStatus.Availability.NOT_CONFIGURED);
    }

    @Test
    void aUserWithoutGenaiAccessSeesNoModel() {
        AiService ai = new AiService(site -> !"acme".equals(site));
        AiModel model = mock(AiModel.class);
        when(model.status(AiFeature.REVIEW)).thenReturn(READY);
        ai.bindModel(model);

        AiStatus status = ai.status(AiFeature.REVIEW, "acme");

        assertThat(status.availability()).isEqualTo(AiStatus.Availability.NO_ACCESS);
        assertThat(status.reason()).isEqualTo("noAccess");
        assertThat(status.provider()).isEmpty();
        assertThat(status.model()).isEmpty();
        assertThat(ai.status(AiFeature.REVIEW, "other").ready()).isTrue();
    }

    @Test
    void aSiteKeyThatGenaiConnectorRefusesHasNoAccess() {
        AiService ai = new AiService(site -> true);
        AiModel model = mock(AiModel.class);
        when(model.status(AiFeature.REVIEW)).thenReturn(READY);
        ai.bindModel(model);

        assertThat(ai.status(AiFeature.REVIEW, "../acme").availability()).isEqualTo(AiStatus.Availability.NO_ACCESS);
        assertThat(ai.status(AiFeature.REVIEW, null).availability()).isEqualTo(AiStatus.Availability.NO_ACCESS);
    }

    @Test
    void aReadyFeatureNamesTheModelThatAnswers() {
        AiService ai = new AiService(site -> true);
        AiModel model = mock(AiModel.class);
        when(model.status(AiFeature.ALT_TEXT)).thenReturn(READY);
        ai.bindModel(model);

        AiStatus status = ai.status(AiFeature.ALT_TEXT, "acme");

        assertThat(status.ready()).isTrue();
        assertThat(status.reason()).isEmpty();
        assertThat(status.model()).isEqualTo("mistral-medium");
        assertThat(status.vision()).isTrue();
    }

    @Test
    void unbindingAnotherModelKeepsTheBoundOne() {
        AiService ai = new AiService(site -> true);
        AiModel bound = mock(AiModel.class);
        when(bound.status(AiFeature.REVIEW)).thenReturn(READY);
        ai.bindModel(bound);

        ai.unbindModel(mock(AiModel.class));
        assertThat(ai.status(AiFeature.REVIEW, "acme").ready()).isTrue();

        ai.unbindModel(bound);
        assertThat(ai.status(AiFeature.REVIEW, "acme").availability()).isEqualTo(AiStatus.Availability.ABSENT);
    }

    @Test
    void aCallWithoutGenaiConnectorIsNotConfigured() {
        AiService ai = new AiService(site -> true);

        assertThatThrownBy(() -> ai.complete(request()))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).reason())
                .isEqualTo(AiException.Reason.NOT_CONFIGURED);
    }

    @Test
    void theAdministratorsInstructionsFollowTheFeatureOnes() throws AiException {
        AiService ai = new AiService(site -> true);
        ai.activate(Map.of("AI_PROMPT_APPENDIX", "  Tone is formal.  "));
        AiModel model = mock(AiModel.class);
        when(model.complete(any())).thenReturn(new AiAnswer("{}", false, 1, 2, null, "p", "m"));
        ai.bindModel(model);

        ai.complete(request());

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(model).complete(sent.capture());
        assertThat(sent.getValue().instructions())
                .isEqualTo("Review the page.\n\nAdditional instructions from this site's administrator:\nTone is formal.");
        assertThat(sent.getValue().siteKey()).isEqualTo("acme");
        assertThat(sent.getValue().maxOutputTokens()).isEqualTo(6000);
    }

    @Test
    void withNoAppendixTheInstructionsAreSentAsTheyAre() throws AiException {
        AiService ai = new AiService(site -> true);
        ai.activate(Map.of());
        AiModel model = mock(AiModel.class);
        when(model.complete(any())).thenReturn(new AiAnswer("{}", false, 1, 2, null, "p", "m"));
        ai.bindModel(model);

        ai.complete(request());

        ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
        verify(model).complete(sent.capture());
        assertThat(sent.getValue().instructions()).isEqualTo("Review the page.");
    }

    @Test
    void rateLimitsHaveDefaultsAndAFloorOfOne() {
        AiService ai = new AiService(site -> true);
        ai.activate(Map.of());
        assertThat(ai.rateMaxCalls()).isEqualTo(30);
        assertThat(ai.reportRateMaxCalls()).isEqualTo(5);

        ai.activate(Map.of("AI_RATE_MAX_CALLS", "0", "AI_REPORT_RATE_MAX_CALLS", " 12 "));
        assertThat(ai.rateMaxCalls()).isEqualTo(1);
        assertThat(ai.reportRateMaxCalls()).isEqualTo(12);
    }

    @Test
    void retiredProviderKeysAreIgnored() {
        AiService ai = new AiService(site -> true);

        ai.activate(Map.of("AI_PROVIDER", "anthropic", "AI_API_KEY", "not-a-real-key", "AI_MODEL", "x"));

        assertThat(ai.status(AiFeature.REVIEW, "acme").availability()).isEqualTo(AiStatus.Availability.ABSENT);
    }
}
