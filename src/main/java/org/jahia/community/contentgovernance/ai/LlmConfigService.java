package org.jahia.community.contentgovernance.ai;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import java.util.Collections;
import java.util.Map;

/**
 * The one model connection of the module, shared by every feature that calls a model: the page
 * review and its SEO, alt text and plain-language assists, and the GEO written site report.
 *
 * <p>Read from {@code karaf/etc/org.jahia.community.contentgovernance.llm.cfg}; an edit applies at
 * once through {@code @Modified}. Nothing here is required: without a provider, a model and a key,
 * {@link #isEnabled()} is false, every AI feature is shown greyed out, and nothing leaves the
 * server.</p>
 *
 * <p>Thread safety: the settings are one immutable record swapped in one volatile write, so a
 * reader sees the whole configuration as it was or as it now is, never a mixture.</p>
 */
@Component(
        service = LlmConfigService.class,
        configurationPid = LlmConfigService.PID,
        property = {
                "service.description=Content Governance - shared LLM configuration",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class LlmConfigService {

    /** PID of the configuration file. */
    public static final String PID = "org.jahia.community.contentgovernance.llm";

    private volatile Snapshot config = Snapshot.from(Collections.emptyMap());

    @Activate
    @Modified
    protected void activate(Map<String, Object> properties) {
        this.config = Snapshot.from(properties);
    }

    /** True when the configuration names a provider this module knows, with a model and a key. */
    public boolean isEnabled() {
        Snapshot s = config;
        return Providers.forName(s.provider()) != null && !s.model().isEmpty() && !s.apiKey().isEmpty();
    }

    /** The configured provider, or null when AI is not configured. */
    public LlmProvider provider() {
        return isEnabled() ? Providers.forName(config.provider()) : null;
    }

    /** The provider's configuration name, empty when AI is not configured. Never the key. */
    public String providerName() {
        return isEnabled() ? config.provider().toLowerCase(java.util.Locale.ROOT) : "";
    }

    /** The model, empty when AI is not configured. */
    public String model() {
        return isEnabled() ? config.model() : "";
    }

    /** Whether the configured provider reads the pictures sent with a prompt (alt text assist). */
    public boolean supportsImages() {
        LlmProvider p = provider();
        return p != null && p.supportsImages();
    }

    /**
     * The connection settings for one call.
     *
     * @param maxTokens the output budget of the call: {@link #maxTokens()} for a page task,
     *                  {@link #reportMaxTokens()} for a site report
     * @return the settings, or null when AI is not configured
     */
    public LlmSettings settings(int maxTokens) {
        LlmProvider p = provider();
        if (p == null) {
            return null;
        }
        Snapshot s = config;
        String baseUrl = s.baseUrl().isEmpty() ? p.defaultBaseUrl() : s.baseUrl();
        return new LlmSettings(p.name(), s.apiKey(), baseUrl, s.model(), maxTokens);
    }

    /** {@code system} followed by the administrator's instructions, when there are any. */
    public String withAppendix(String system) {
        String appendix = config.promptAppendix();
        return appendix.isEmpty() ? system : system + "\n\nAdditional instructions from this site's administrator:\n" + appendix;
    }

    /** Output budget of a page review or assist. */
    public int maxTokens() {
        return config.maxTokens();
    }

    /** Output budget of a GEO site report, which is a long answer. */
    public int reportMaxTokens() {
        return config.reportMaxTokens();
    }

    /** Page reviews and assists allowed per user per ten minutes. */
    public int rateMaxCalls() {
        return config.rateMaxCalls();
    }

    /** Site reports allowed per user per ten minutes. */
    public int reportRateMaxCalls() {
        return config.reportRateMaxCalls();
    }

    /** USD per million input tokens; 0 when unknown, and no cost is then shown. */
    public double costInputPerMTokens() {
        return config.costInput();
    }

    /** USD per million output tokens; 0 when unknown, and no cost is then shown. */
    public double costOutputPerMTokens() {
        return config.costOutput();
    }

    /** Every key reads its own value or its default, so the defaults are an empty map and not a second list. */
    private record Snapshot(String provider, String model, String apiKey, String baseUrl, int maxTokens,
                            int reportMaxTokens, int rateMaxCalls, int reportRateMaxCalls, double costInput,
                            double costOutput, String promptAppendix) {

        static Snapshot from(Map<String, Object> p) {
            return new Snapshot(
                    str(p, "AI_PROVIDER"),
                    str(p, "AI_MODEL"),
                    str(p, "AI_API_KEY"),
                    str(p, "AI_BASE_URL"),
                    Math.max(256, intVal(p, "AI_MAX_TOKENS", 4096)),
                    Math.max(256, intVal(p, "AI_REPORT_MAX_TOKENS", 6000)),
                    Math.max(1, intVal(p, "AI_RATE_MAX_CALLS", 30)),
                    Math.max(1, intVal(p, "AI_REPORT_RATE_MAX_CALLS", 5)),
                    Math.max(0, dblVal(p, "AI_COST_INPUT_PER_MTOKENS")),
                    Math.max(0, dblVal(p, "AI_COST_OUTPUT_PER_MTOKENS")),
                    str(p, "AI_PROMPT_APPENDIX"));
        }

        private static String str(Map<String, Object> m, String key) {
            Object v = m.get(key);
            return v instanceof String ? ((String) v).trim() : "";
        }

        private static int intVal(Map<String, Object> m, String key, int def) {
            try {
                return Integer.parseInt(String.valueOf(m.get(key)).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        private static double dblVal(Map<String, Object> m, String key) {
            try {
                return Double.parseDouble(String.valueOf(m.get(key)).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public String toString() {
            return "Snapshot[provider=" + provider + ", model=" + model + ", apiKey=" + (apiKey.isEmpty() ? "<empty>" : "***") + "]";
        }
    }
}
