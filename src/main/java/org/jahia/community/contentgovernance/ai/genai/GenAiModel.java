package org.jahia.community.contentgovernance.ai.genai;

import org.jahia.community.contentgovernance.ai.AiAnswer;
import org.jahia.community.contentgovernance.ai.AiException;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiModel;
import org.jahia.community.contentgovernance.ai.AiRequest;
import org.jahia.community.contentgovernance.ai.AiStatus;
import org.jahia.modules.genai.api.CompletionRequest;
import org.jahia.modules.genai.api.CompletionResult;
import org.jahia.modules.genai.api.GenAiAccessDeniedException;
import org.jahia.modules.genai.api.GenAiException;
import org.jahia.modules.genai.api.GenAiInfo;
import org.jahia.modules.genai.api.GenAiInvalidOutputException;
import org.jahia.modules.genai.api.GenAiNotConfiguredException;
import org.jahia.modules.genai.api.GenAiQuotaExceededException;
import org.jahia.modules.genai.api.GenAiService;
import org.jahia.modules.genai.api.ImageInput;
import org.jahia.modules.genai.api.ModelTier;
import org.jahia.modules.genai.api.ResponseFormat;
import org.jahia.modules.genai.api.StopReason;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Optional;

/**
 * The model of the AI features, served by genai-connector. The only class of the module that names a
 * type of genai-connector: it starts when genai-connector's service is there, and the module loads it
 * through a dynamic import, so the module starts without genai-connector.
 */
@Component(service = AiModel.class, immediate = true)
public class GenAiModel implements AiModel {

    private final GenAiService genAi;

    @Activate
    public GenAiModel(@Reference GenAiService genAi) {
        this.genAi = genAi;
    }

    /**
     * The tier of a feature: balanced for every text task, vision for the alternative text when
     * pictures go with it. The alternative text of an image the browser could not read goes on
     * balanced, from its context alone.
     */
    static ModelTier tierOf(AiFeature feature, boolean withImages) {
        return feature == AiFeature.ALT_TEXT && withImages ? ModelTier.VISION : ModelTier.BALANCED;
    }

    @Override
    public AiStatus status(AiFeature feature) {
        ModelTier tier = tierOf(feature, false);
        Optional<GenAiInfo> info = genAi.isAvailable(tier) ? genAi.info(tier) : Optional.empty();
        if (info.isEmpty()) {
            return AiStatus.unavailable(AiStatus.Availability.NOT_CONFIGURED);
        }
        return new AiStatus(AiStatus.Availability.READY, info.get().provider(), info.get().model(),
                genAi.isAvailable(ModelTier.VISION));
    }

    @Override
    public AiAnswer complete(AiRequest request) throws AiException {
        CompletionResult result;
        try {
            result = genAi.complete(CompletionRequest.builder()
                    .tier(tierOf(request.feature(), !request.images().isEmpty()))
                    .site(request.siteKey())
                    .instructions(request.instructions())
                    .input(request.input())
                    .images(request.images().stream().map(image -> ImageInput.of(image.data(), image.mediaType()))
                            .toList())
                    .maxOutputTokens(request.maxOutputTokens())
                    .responseFormat(ResponseFormat.JSON)
                    .build());
        } catch (GenAiAccessDeniedException e) {
            throw new AiException(AiException.Reason.NO_ACCESS, e.getMessage(), e);
        } catch (GenAiQuotaExceededException e) {
            throw new AiException(AiException.Reason.QUOTA, e.getMessage(), e);
        } catch (GenAiNotConfiguredException e) {
            throw new AiException(AiException.Reason.NOT_CONFIGURED, e.getMessage(), e);
        } catch (GenAiInvalidOutputException e) {
            // genai-connector retries an answer that is not JSON; it does not retry one cut by its budget.
            throw new AiException(e.isRetryable() ? AiException.Reason.INVALID_OUTPUT
                    : AiException.Reason.OUTPUT_LIMIT, e.getMessage(), e);
        } catch (GenAiException | IllegalArgumentException e) {
            throw new AiException(AiException.Reason.PROVIDER, e.getMessage(), e);
        }
        return new AiAnswer(result.text(), result.stopReason() == StopReason.MAX_TOKENS,
                result.usage().inputTokens(), result.usage().outputTokens(), result.costUsd().orElse(null),
                result.provider(), result.model());
    }
}
