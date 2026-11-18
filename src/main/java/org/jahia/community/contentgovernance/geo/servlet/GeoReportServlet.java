package org.jahia.community.contentgovernance.geo.servlet;

import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.community.contentgovernance.geo.check.GeoReport;
import org.jahia.community.contentgovernance.geo.check.ScanStore;
import org.jahia.community.contentgovernance.ai.AiException;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiService;
import org.jahia.community.contentgovernance.ai.AiStatus;
import org.jahia.community.contentgovernance.geo.config.GeoReadinessConfigService;
import org.jahia.community.contentgovernance.geo.util.PublicUrls;
import org.jahia.community.contentgovernance.geo.util.SiteScope;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * The written report, generated on request and read back afterwards.
 *
 * GET  /modules/content-governance/geo/report            -> {enabled, reason, provider, model}
 * POST {"action":"read", path, language}        -> the stored report, or {"report": null}
 * POST {"action":"generate", path, language, reportLanguage} -> asks genai-connector, stores, returns
 *
 * Same gate as the dashboard's other endpoints: the caller must hold the
 * permission the dashboard route declares, on the site the path resolves to.
 * Generation is metered separately and tightly, because each call is paid for.
 */
@Component(
        service = {HttpServlet.class, Servlet.class},
        property = {
                "alias=/content-governance/geo/report",
                "service.description=GEO readiness written report",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class GeoReportServlet extends GeoServlet {

    private static final Logger logger = LoggerFactory.getLogger(GeoReportServlet.class);

    private static final int MAX_BODY = 16_000;
    /** HttpServletResponse has no constant for it. */
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String AUTH_REQUIRED = "authentication required";
    private static final String OPERATION_FAILED = "operation failed";
    private static final long RATE_WINDOW_MS = 600_000L;

    /**
     * Injected through the constructor rather than into the field, so a servlet
     * the container shares between threads holds nothing mutable.
     */
    private final transient GeoReadinessConfigService config;
    private final transient AiService ai;

    @Activate
    public GeoReportServlet(@Reference GeoReadinessConfigService config, @Reference AiService ai) {
        this.config = config;
        this.ai = ai;
    }

    /**
     * Whether a written report can be asked for at all, for a site the caller
     * may administer.
     *
     * The gate is {@link #requireDashboard}, not a guest check. The answer names
     * the AI provider and model that genai-connector serves - which third party
     * this site's content is sent to - so it is site settings, and it is read with
     * the same `publish` permission the POST below requires. Before
     * JAHIA-SEC-432 this asked only whether the caller was logged in, and any
     * account on the platform could read it.
     *
     * The caller must also hold genaiAccess on the site, as genai-connector checks it.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String sitePath = requireDashboard(req, resp);
            if (sitePath == null) {
                return;
            }
            AiStatus status = ai.status(AiFeature.GEO_REPORT, SiteScope.keyOf(sitePath));
            JSONObject out = new JSONObject();
            out.put("enabled", status.ready());
            out.put("reason", status.reason());
            out.put("provider", status.provider());
            out.put("model", status.model());
            writeJson(resp, HttpServletResponse.SC_OK, out);
        } catch (IOException | RuntimeException e) {
            // Nothing may leave a servlet method: the container would answer with
            // a stack trace instead of a response. A broken socket, or a JSON
            // library that throws unchecked, both end here.
            logger.debug("could not write the report status", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            dispatch(req, resp);
        } catch (IOException | RuntimeException e) {
            logger.debug("could not write the report response", e);
        }
    }

    private void dispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JahiaUser user = currentUser();
        if (user == null || JahiaUserManagerService.GUEST_USERNAME.equals(user.getName())) {
            deny(resp, HttpServletResponse.SC_UNAUTHORIZED, AUTH_REQUIRED);
            return;
        }
        Ask ask = read(req, resp);
        if (ask == null) {
            return;
        }
        String sitePath = resolveSite(ask.path, ask.language, resp);
        if (sitePath == null) {
            return;
        }
        run(ask, sitePath, user, req, resp);
    }

    /** What the caller asked for, or null once the refusal has been written. */
    private Ask read(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // A same-origin call with exactly application/json, like every other endpoint of the module.
        JSONObject body = jsonBody(req, resp, MAX_BODY);
        if (body == null) {
            return null;
        }

        Ask ask = new Ask(body);
        if (ask.path.isEmpty() || !ask.path.startsWith("/sites/")) {
            deny(resp, HttpServletResponse.SC_BAD_REQUEST, "path required");
            return null;
        }
        // Both become node names or prompt content; neither may be a path.
        if (!SiteScope.isLanguage(ask.language) || !SiteScope.isLanguage(ask.reportLanguage)) {
            deny(resp, HttpServletResponse.SC_BAD_REQUEST, "language required");
            return null;
        }
        return ask;
    }

    /** Reads the stored report, or asks for a new one. */
    private void run(Ask ask, String sitePath, JahiaUser user, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        try {
            if ("read".equals(ask.action)) {
                JSONObject out = new JSONObject();
                JSONObject stored = ScanStore.readReport(sitePath, ask.language);
                out.put("report", stored == null ? JSONObject.NULL : stored);
                writeJson(resp, HttpServletResponse.SC_OK, out);
            } else if ("generate".equals(ask.action)) {
                generate(sitePath, ask.language, ask.reportLanguage, user, req, resp);
            } else {
                deny(resp, HttpServletResponse.SC_BAD_REQUEST, "unknown action");
            }
        } catch (AiException e) {
            // The provider's own words stay in the log. The browser learns why there is no report.
            logger.warn("GEO report for {} [{}] gave no answer ({}): {}", sitePath, ask.language, e.reason(),
                    e.getMessage());
            denyAi(resp, e.reason());
        } catch (IOException e) {
            logger.warn("GEO report for {} [{}] failed: {}", sitePath, ask.language, e.getMessage());
            deny(resp, HttpServletResponse.SC_BAD_GATEWAY, "provider failed: " + safe(e.getMessage()));
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("GEO report {} failed for {}: {}", ask.action, sitePath, e.getMessage());
            logger.debug("GEO report failure", e);
            deny(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, OPERATION_FAILED);
        }
    }

    /** The request body, read once. */
    private static final class Ask {
        private final String action;
        private final String path;
        private final String language;
        private final String reportLanguage;

        private Ask(JSONObject body) {
            this.action = body.optString("action", "read");
            this.path = body.optString("path", "");
            this.language = body.optString("language", "en");
            this.reportLanguage = body.optString("reportLanguage", this.language);
        }
    }

    /** The site the caller may act on, or null once the refusal has been written. */
    private String resolveSite(String path, String language, HttpServletResponse resp) throws IOException {
        try {
            return SiteScope.require(path, language, SiteScope.DASHBOARD);
        } catch (PathNotFoundException | AccessDeniedException e) {
            deny(resp, HttpServletResponse.SC_FORBIDDEN, "cannot read node");
        } catch (RepositoryException e) {
            logger.warn("report gate failed for {}: {}", path, e.getMessage());
            deny(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, OPERATION_FAILED);
        }
        return null;
    }

    /** The paid path: a model must answer for the caller on the site, within their budget. */
    private void generate(String sitePath, String language, String reportLanguage, JahiaUser user,
            HttpServletRequest req, HttpServletResponse resp)
            throws IOException, RepositoryException, AiException {
        AiStatus status = ai.status(AiFeature.GEO_REPORT, SiteScope.keyOf(sitePath));
        if (!status.ready()) {
            denyAi(resp, status.availability() == AiStatus.Availability.NO_ACCESS
                    ? AiException.Reason.NO_ACCESS : AiException.Reason.NOT_CONFIGURED);
            return;
        }
        if (!rateLimitOk(user.getUserKey(), RATE_WINDOW_MS, ai.reportRateMaxCalls())) {
            deny(resp, TOO_MANY_REQUESTS, "rate limit");
            return;
        }
        String base = baseUrlFor(sitePath, language, req);
        JSONObject report = GeoReport.generate(sitePath, language, reportLanguage, base, ai);
        JSONObject out = new JSONObject();
        out.put("report", report);
        writeJson(resp, HttpServletResponse.SC_OK, out);
    }

    /** The refusal of a report that genai-connector did not write. */
    private static void denyAi(HttpServletResponse resp, AiException.Reason reason) throws IOException {
        switch (reason) {
            case NO_ACCESS:
                deny(resp, HttpServletResponse.SC_FORBIDDEN, "genaiAccess required");
                break;
            case QUOTA:
                deny(resp, TOO_MANY_REQUESTS, "rate limit");
                break;
            case NOT_CONFIGURED:
                deny(resp, HttpServletResponse.SC_CONFLICT, "no provider configured");
                break;
            case OUTPUT_LIMIT:
                deny(resp, HttpServletResponse.SC_BAD_GATEWAY, "provider failed: the answer reached its output budget");
                break;
            default:
                deny(resp, HttpServletResponse.SC_BAD_GATEWAY, "provider failed");
        }
    }

    private String baseUrlFor(String sitePath, String language, HttpServletRequest req) throws RepositoryException {
        String configured = config.getPublicBaseUrl();
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        JCRSessionWrapper live = JCRSessionFactory.getInstance()
                .getCurrentUserSession("live", Locales.of(language));
        return PublicUrls.base(live.getNode(sitePath), req, "");
    }

    /** A provider message can quote the request it rejected; a key never appears there, but be sure. */
    private static String safe(String message) {
        if (message == null) {
            return "";
        }
        String m = message.replaceAll("(?i)(bearer|x-api-key|sk-[a-z0-9_-]+)\\S*", "***");
        return m.length() > 300 ? m.substring(0, 300) + "..." : m;
    }

}
