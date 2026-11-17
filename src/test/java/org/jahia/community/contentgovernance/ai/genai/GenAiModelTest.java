package org.jahia.community.contentgovernance.ai.genai;

import org.jahia.community.contentgovernance.ai.AiAnswer;
import org.jahia.community.contentgovernance.ai.AiException;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiImage;
import org.jahia.community.contentgovernance.ai.AiRequest;
import org.jahia.community.contentgovernance.ai.AiStatus;
import org.jahia.modules.genai.api.CompletionRequest;
import org.jahia.modules.genai.api.CompletionResult;
import org.jahia.modules.genai.api.GenAiAccessDeniedException;
import org.jahia.modules.genai.api.GenAiInfo;
import org.jahia.modules.genai.api.GenAiInvalidOutputException;
import org.jahia.modules.genai.api.GenAiNotConfiguredException;
import org.jahia.modules.genai.api.GenAiProviderException;
import org.jahia.modules.genai.api.GenAiQuotaExceededException;
import org.jahia.modules.genai.api.GenAiService;
import org.jahia.modules.genai.api.ModelTier;
import org.jahia.modules.genai.api.ResponseFormat;
import org.jahia.modules.genai.api.StopReason;
import org.jahia.modules.genai.api.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenAiModelTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final GenAiService genAi = mock(GenAiService.class);
    private final GenAiModel model = new GenAiModel(genAi);

    private static AiRequest request(AiFeature feature, List<AiImage> images) {
        return new AiRequest(feature, "acme", "Answer with JSON.", "PAGE TEXT", images, 6000);
    }

    private static CompletionResult result(StopReason stop) {
        return new CompletionResult("{\"summary\":\"ok\"}", stop, new Usage(120, 40), ModelTier.BALANCED, "mistral",
                "mistral-small", 0.0012);
    }

    private CompletionRequest sent() {
        ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(genAi).complete(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @EnumSource(AiFeature.class)
    void everyTextTaskRunsOnTheBalancedTier(AiFeature feature) {
        assertThat(GenAiModel.tierOf(feature, false)).isEqualTo(ModelTier.BALANCED);
    }

    @Test
    void altTextWithPicturesRunsOnTheVisionTier() {
        assertThat(GenAiModel.tierOf(AiFeature.ALT_TEXT, true)).isEqualTo(ModelTier.VISION);
        assertThat(GenAiModel.tierOf(AiFeature.REVIEW, true)).isEqualTo(ModelTier.BALANCED);
    }

    @Test
    void aServedTierNamesItsProviderAndModel() {
        when(genAi.isAvailable(ModelTier.BALANCED)).thenReturn(true);
        when(genAi.info(ModelTier.BALANCED)).thenReturn(Optional.of(new GenAiInfo(ModelTier.BALANCED, "mistral", "mistral-small")));
        when(genAi.isAvailable(ModelTier.VISION)).thenReturn(false);

        AiStatus status = model.status(AiFeature.GEO_REPORT);

        assertThat(status.ready()).isTrue();
        assertThat(status.provider()).isEqualTo("mistral");
        assertThat(status.model()).isEqualTo("mistral-small");
        assertThat(status.vision()).isFalse();
    }

    @Test
    void aTierWithNoModelIsNotConfigured() {
        when(genAi.isAvailable(ModelTier.BALANCED)).thenReturn(false);

        assertThat(model.status(AiFeature.REVIEW).availability()).isEqualTo(AiStatus.Availability.NOT_CONFIGURED);
    }

    @Test
    void aCallNamesItsSiteTierBudgetAndAsksForJson() throws AiException {
        when(genAi.complete(any())).thenReturn(result(StopReason.COMPLETE));

        AiAnswer answer = model.complete(request(AiFeature.SEO, List.of()));

        CompletionRequest call = sent();
        assertThat(call.site()).contains("acme");
        assertThat(call.tier()).isEqualTo(ModelTier.BALANCED);
        assertThat(call.maxOutputTokens()).contains(6000);
        assertThat(call.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(call.instructions()).contains("Answer with JSON.");
        assertThat(call.input()).isEqualTo("PAGE TEXT");
        assertThat(call.images()).isEmpty();
        assertThat(answer.text()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(answer.truncated()).isFalse();
        assertThat(answer.inputTokens()).isEqualTo(120);
        assertThat(answer.outputTokens()).isEqualTo(40);
        assertThat(answer.costUsd()).isEqualTo(0.0012);
        assertThat(answer.provider()).isEqualTo("mistral");
        assertThat(answer.model()).isEqualTo("mistral-small");
    }

    @Test
    void altTextSendsItsPicturesOnTheVisionTier() throws AiException {
        when(genAi.complete(any())).thenReturn(result(StopReason.COMPLETE));

        model.complete(request(AiFeature.ALT_TEXT, List.of(new AiImage("image/png", PNG))));

        CompletionRequest call = sent();
        assertThat(call.tier()).isEqualTo(ModelTier.VISION);
        assertThat(call.images()).hasSize(1);
        assertThat(call.images().get(0).mediaType()).isEqualTo("image/png");
        assertThat(call.images().get(0).data()).isEqualTo(PNG);
    }

    @Test
    void anAnswerCutByItsBudgetIsTruncated() throws AiException {
        when(genAi.complete(any())).thenReturn(result(StopReason.MAX_TOKENS));

        assertThat(model.complete(request(AiFeature.REVIEW, List.of())).truncated()).isTrue();
    }

    @Test
    void aRefusedSiteIsNoAccess() {
        when(genAi.complete(any())).thenThrow(new GenAiAccessDeniedException("acme", "genaiAccess required"));

        assertReason(AiException.Reason.NO_ACCESS);
    }

    @Test
    void theConnectorQuotaIsQuota() {
        when(genAi.complete(any())).thenThrow(new GenAiQuotaExceededException("too many"));

        assertReason(AiException.Reason.QUOTA);
    }

    @Test
    void aTierThatLostItsModelIsNotConfigured() {
        when(genAi.complete(any())).thenThrow(new GenAiNotConfiguredException("no balanced model"));

        assertReason(AiException.Reason.NOT_CONFIGURED);
    }

    @Test
    void anAnswerCutBeforeItWasJsonIsTheOutputLimit() {
        when(genAi.complete(any())).thenThrow(new GenAiInvalidOutputException("cut", false));

        assertReason(AiException.Reason.OUTPUT_LIMIT);
    }

    @Test
    void anAnswerThatStayedProseIsInvalidOutput() {
        when(genAi.complete(any())).thenThrow(new GenAiInvalidOutputException("prose", true));

        assertReason(AiException.Reason.INVALID_OUTPUT);
    }

    @Test
    void aProviderFailureIsProvider() {
        when(genAi.complete(any())).thenThrow(new GenAiProviderException("502 from upstream", true, 502));

        assertReason(AiException.Reason.PROVIDER);
    }

    @Test
    void aPictureTheConnectorRefusesIsProvider() {
        // genai-connector accepts jpeg, png, gif and webp only, and refuses the request before any call.
        assertThatThrownBy(() -> model.complete(request(AiFeature.ALT_TEXT, List.of(new AiImage("image/bmp", PNG)))))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).reason())
                .isEqualTo(AiException.Reason.PROVIDER);
    }

    private void assertReason(AiException.Reason reason) {
        assertThatThrownBy(() -> model.complete(request(AiFeature.REVIEW, List.of())))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).reason())
                .isEqualTo(reason);
    }
}
