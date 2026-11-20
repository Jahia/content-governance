package org.jahia.community.contentgovernance.geo.servlet;

import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.community.contentgovernance.geo.check.GeoReport;
import org.jahia.community.contentgovernance.geo.check.ScanStore;
import org.jahia.community.contentgovernance.geo.config.GeoReadinessConfigService;
import org.jahia.community.contentgovernance.geo.spi.GeoReportWriter;
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
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
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
 * POST {"action":"generate", path, language, reportLanguage} -> asks the writer, stores, returns
 *
 * Same gate as the dashboard's other endpoints: the caller must hold the
 * permission the dashboard route declares, on the site the path resolves to.
 * This module calls no model: another module, such as ai-assistant, registers
 * a {@link GeoReportWriter}, which checks its own permission and meters its
 * own calls. Without one, the status says {@code absent} and nothing is written.
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
    /** The reason of the status while no module registers a writer. */
    static final String ABSENT = "absent";

    /**
     * Injected through the constructor rather than into the field, so a servlet
     * the container shares between threads holds nothing mutable.
     */
    private final transient GeoReadinessConfigService config;

    /** The writer that another module registers, or null: it may come and go while this servlet runs. */
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    private volatile GeoReportWriter writer;

    @Activate
    public GeoReportServlet(@Reference GeoReadinessConfigService config) {
        this.config = config;
    }

    /** For the tests, which build the servlet with no OSGi. */
    void setWriter(GeoReportWriter writer) {
        this.writer = writer;
    }

    /**
     * Whether a written report can be asked for at all, for a site the caller
     * may administer.
     *
     * The gate is {@link #requireDashboard}, not a guest check. The answer names
     * the provider and the model that the writer uses - which third party this
     * site's content is sent to - so it is site settings, and it is read with
     * the same `publish` permission the POST below requires. Before
     * JAHIA-SEC-432 this asked only whether the caller was logged in, and any
     * account on the platform could read it.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String sitePath = requireDashboard(req, resp);
            if (sitePath == null) {
                return;
            }
            writeJson(resp, HttpServletResponse.SC_OK, statusJson(statusOf(writer, SiteScope.keyOf(sitePath))));
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
        run(ask, sitePath, req, resp);
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
    private void run(Ask ask, String sitePath, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        try {
            if ("read".equals(ask.action)) {
                JSONObject out = new JSONObject();
                JSONObject stored = ScanStore.readReport(sitePath, ask.language);
                out.put("report", stored == null ? JSONObject.NULL : stored);
                writeJson(resp, HttpServletResponse.SC_OK, out);
            } else if ("generate".equals(ask.action)) {
                generate(sitePath, ask.language, ask.reportLanguage, req, resp);
            } else {
                deny(resp, HttpServletResponse.SC_BAD_REQUEST, "unknown action");
            }
        } catch (GeoReportWriter.Failure e) {
            // The provider's own words stay in the log. The browser learns why there is no report.
            logger.warn("GEO report for {} [{}] gave no answer ({}): {}", sitePath, ask.language, e.reason(),
                    e.getMessage());
            denyWriter(resp, e.reason());
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

    /** The status of a writer for the current user on a site, or {@value #ABSENT} without a writer. */
    static GeoReportWriter.Status statusOf(GeoReportWriter writer, String siteKey) {
        return writer == null ? GeoReportWriter.Status.refused(ABSENT) : writer.status(siteKey);
    }

    /** The status as the dashboard reads it: the provider and the model only when the writer is ready. */
    static JSONObject statusJson(GeoReportWriter.Status status) {
        JSONObject out = new JSONObject();
        out.put("enabled", status.ready());
        out.put("reason", status.ready() ? "" : status.reason());
        out.put("provider", status.ready() ? status.provider() : "");
        out.put("model", status.ready() ? status.model() : "");
        return out;
    }

    /** The paid path: the writer must answer for the caller on the site; it meters its own calls. */
    private void generate(String sitePath, String language, String reportLanguage,
            HttpServletRequest req, HttpServletResponse resp)
            throws IOException, RepositoryException, GeoReportWriter.Failure {
        GeoReportWriter current = writer;
        GeoReportWriter.Status status = statusOf(current, SiteScope.keyOf(sitePath));
        if (!status.ready()) {
            denyWriter(resp, "noAccess".equals(status.reason())
                    ? GeoReportWriter.Failure.Reason.NO_ACCESS : GeoReportWriter.Failure.Reason.NOT_CONFIGURED);
            return;
        }
        String base = baseUrlFor(sitePath, language, req);
        JSONObject report = GeoReport.generate(sitePath, language, reportLanguage, base, current);
        JSONObject out = new JSONObject();
        out.put("report", report);
        writeJson(resp, HttpServletResponse.SC_OK, out);
    }

    /** The refusal of a report that the writer did not write. */
    private static void denyWriter(HttpServletResponse resp, GeoReportWriter.Failure.Reason reason) throws IOException {
        switch (reason) {
            case NO_ACCESS:
                deny(resp, HttpServletResponse.SC_FORBIDDEN, "access required");
                break;
            case QUOTA:
                deny(resp, TOO_MANY_REQUESTS, "rate limit");
                break;
            case NOT_CONFIGURED:
                deny(resp, HttpServletResponse.SC_CONFLICT, "no report writer");
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
