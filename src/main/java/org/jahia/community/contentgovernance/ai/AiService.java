package org.jahia.community.contentgovernance.ai;

import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The one entry point of every feature that calls a model: the page review and its SEO, alt text and
 * plain-language assists, and the GEO written report.
 *
 * <p>genai-connector serves the model: it holds the provider, the key and the model of each tier. This
 * service answers whether a feature can run for the current user on a site, and greys it out when
 * genai-connector is absent, serves no model on its tier, or the user lacks {@value #GENAI_ACCESS} on the
 * site. It also reads what the module still configures itself, in
 * {@code karaf/etc/org.jahia.community.contentgovernance.llm.cfg}: the rate limits and the administrator's
 * instructions appended to every prompt.</p>
 */
@Component(
        service = AiService.class,
        configurationPid = AiService.PID,
        property = {
                "service.description=Content Governance - the AI features, served by genai-connector",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class AiService {

    /** PID of the configuration file. */
    public static final String PID = "org.jahia.community.contentgovernance.llm";

    /** The permission of genai-connector that a user needs on a site to call a model for it. */
    static final String GENAI_ACCESS = "genaiAccess";

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    /** The site keys genai-connector accepts on a request; it refuses any other. */
    private static final Pattern SITE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    /** Keys of the provider client this module had before genai-connector served its model. */
    private static final List<String> RETIRED_KEYS = List.of("AI_PROVIDER", "AI_MODEL", "AI_API_KEY", "AI_BASE_URL",
            "AI_MAX_TOKENS", "AI_REPORT_MAX_TOKENS", "AI_COST_INPUT_PER_MTOKENS", "AI_COST_OUTPUT_PER_MTOKENS");

    private final AtomicReference<AiModel> model = new AtomicReference<>();
    private final Predicate<String> access;
    private volatile Settings settings = Settings.from(Collections.emptyMap());

    public AiService() {
        this(AiService::currentUserMayUse);
    }

    /** @param access whether the current user holds {@value #GENAI_ACCESS} on the site of a key */
    AiService(Predicate<String> access) {
        this.access = access;
    }

    @Activate
    @Modified
    protected void activate(Map<String, Object> properties) {
        this.settings = Settings.from(properties);
        List<String> retired = RETIRED_KEYS.stream()
                .filter(key -> properties.get(key) instanceof String value && !value.isBlank())
                .toList();
        if (!retired.isEmpty()) {
            logger.warn("{}.cfg still sets {}: genai-connector serves the model of the AI features now, and this "
                    + "module ignores these keys. Remove them from the file, the key first.", PID, retired);
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY, unbind = "unbindModel")
    protected void bindModel(AiModel served) {
        model.set(served);
    }

    protected void unbindModel(AiModel served) {
        model.compareAndSet(served, null);
    }

    /**
     * Whether a feature can call its model for the current user on a site.
     *
     * @param siteKey the key of the site; null or blank never has access
     * @return the status; it names the provider and the model only when they answer for this user
     */
    public AiStatus status(AiFeature feature, String siteKey) {
        AiModel served = model.get();
        if (served == null) {
            return AiStatus.unavailable(AiStatus.Availability.ABSENT);
        }
        AiStatus status = served.status(feature);
        if (!status.ready()) {
            return status;
        }
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches() || !access.test(siteKey)) {
            return AiStatus.unavailable(AiStatus.Availability.NO_ACCESS);
        }
        return status;
    }

    /**
     * Sends one call, with the administrator's instructions after the feature's own.
     *
     * @throws AiException when the call gives no answer, {@link AiException.Reason#NOT_CONFIGURED} when
     *                     genai-connector is absent
     */
    public AiAnswer complete(AiRequest request) throws AiException {
        AiModel served = model.get();
        if (served == null) {
            throw new AiException(AiException.Reason.NOT_CONFIGURED, "genai-connector is not running");
        }
        return served.complete(request.withInstructions(withAppendix(request.instructions())));
    }

    /** Page reviews and assists allowed per user per ten minutes. */
    public int rateMaxCalls() {
        return settings.rateMaxCalls();
    }

    /** Site reports allowed per user per ten minutes. */
    public int reportRateMaxCalls() {
        return settings.reportRateMaxCalls();
    }

    private String withAppendix(String system) {
        String appendix = settings.promptAppendix();
        return appendix.isEmpty() ? system
                : system + "\n\nAdditional instructions from this site's administrator:\n" + appendix;
    }

    /**
     * Whether the current user holds {@value #GENAI_ACCESS} on the site of {@code siteKey}, read as
     * genai-connector reads it. No user, a site that does not exist and a site the user cannot read
     * all give false.
     */
    @SuppressWarnings("java:S1181") // LinkageError: JCRSessionFactory is absent outside a Jahia runtime
    static boolean currentUserMayUse(String siteKey) {
        try {
            if (JCRSessionFactory.getInstance().getCurrentUser() == null) {
                return false;
            }
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession();
            String path = "/sites/" + siteKey;
            return session.nodeExists(path) && session.getNode(path).hasPermission(GENAI_ACCESS);
        } catch (RepositoryException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Every key reads its own value or its default, so the defaults are an empty map and not a second list. */
    private record Settings(int rateMaxCalls, int reportRateMaxCalls, String promptAppendix) {

        static Settings from(Map<String, Object> p) {
            return new Settings(
                    Math.max(1, intVal(p, "AI_RATE_MAX_CALLS", 30)),
                    Math.max(1, intVal(p, "AI_REPORT_RATE_MAX_CALLS", 5)),
                    p.get("AI_PROMPT_APPENDIX") instanceof String s ? s.trim() : "");
        }

        private static int intVal(Map<String, Object> m, String key, int def) {
            try {
                return Integer.parseInt(String.valueOf(m.get(key)).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }
}
