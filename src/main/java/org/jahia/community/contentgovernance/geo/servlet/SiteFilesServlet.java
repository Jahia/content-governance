package org.jahia.community.contentgovernance.geo.servlet;

import org.jahia.community.contentgovernance.geo.check.AiCrawlers;
import org.jahia.community.contentgovernance.geo.check.GuestVisibility;
import org.jahia.community.contentgovernance.geo.check.RobotsEditor;
import org.jahia.community.contentgovernance.geo.check.RobotsRules;
import org.jahia.community.contentgovernance.geo.check.SiteFilesChecker;
import org.jahia.community.contentgovernance.geo.config.GeoReadinessConfigService;
import org.jahia.community.contentgovernance.geo.util.LlmsFile;
import org.jahia.community.contentgovernance.geo.util.PublicUrls;
import org.jahia.community.contentgovernance.geo.util.SitePages;
import org.jahia.community.contentgovernance.geo.util.SiteScope;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generating the two site files, and writing them back when an editor says so.
 *
 * This is the one part of the module that writes. The crawler check is safe to
 * run against anything; this is not, so the split is deliberate and visible:
 *
 *   preview -> returns the generated text AND the text currently stored,
 *              so the caller can diff them. Changes nothing.
 *   apply   -> writes exactly the text it was given, then publishes.
 *
 * "apply" never invents content. It stores what the editor saw and approved,
 * which means an editor can hand-edit the generated file before saving it and
 * we will not quietly regenerate over the top.
 *
 * POST /modules/content-governance/geo/site-files
 *   {"action":"readLlms","path":"/sites/x/home","language":"en"}
 */
@Component(
        service = {HttpServlet.class, Servlet.class},
        property = {
                "alias=/content-governance/geo/site-files",
                "allow-api-token=true",
                "service.description=GEO readiness robots.txt editing and llms.txt reading",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class SiteFilesServlet extends GeoServlet {

    private static final Logger logger = LoggerFactory.getLogger(SiteFilesServlet.class);

    private static final String LLMS_MODULE = "llms";
    private static final String ROBOTS_MIXIN = "jmix:robots";
    private static final String ROBOTS_PROP = "j:robots";
    /** The community robots module ships this as the autocreated default. */
    private static final String ROBOTS_DEFAULT = "User-agent: *";
    private static final int MAX_CONTENT = 400_000;

    /**
     * Injected through the constructor rather than into the field, so a servlet
     * the container shares between threads holds nothing mutable.
     */
    private final transient GeoReadinessConfigService config;

    @Activate
    public SiteFilesServlet(@Reference GeoReadinessConfigService config) {
        this.config = config;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            dispatch(req, resp);
        } catch (IOException | RuntimeException e) {
            // Nothing may leave a servlet method: the container would answer with
            // a stack trace instead of a response.
            logger.debug("could not write the site files response", e);
        }
    }

    private void dispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JahiaUser user = currentUser();
        if (user == null || JahiaUserManagerService.GUEST_USERNAME.equals(user.getName())) {
            deny(resp, HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
            return;
        }
        // A same-origin call with exactly application/json, like every other endpoint of the module.
        JSONObject body = jsonBody(req, resp, MAX_CONTENT);
        if (body == null) {
            return;
        }

        String action = body.optString("action", "");
        String path = body.optString("path", "");
        String language = body.optString("language", "en");
        if (path.isEmpty() || !path.startsWith("/sites/")) {
            deny(resp, HttpServletResponse.SC_BAD_REQUEST, "path required");
            return;
        }
        // Becomes a node name under a system session further down.
        if (!SiteScope.isLanguage(language)) {
            deny(resp, HttpServletResponse.SC_BAD_REQUEST, "language required");
            return;
        }

        try {
            // Both files belong to the site, both preview against live, and an
            // apply publishes. So the gate is the site and the dashboard's own
            // permission, settled before any action runs.
            try {
                SiteScope.require(path, language, SiteScope.DASHBOARD);
            } catch (javax.jcr.PathNotFoundException | javax.jcr.AccessDeniedException e) {
                deny(resp, HttpServletResponse.SC_FORBIDDEN, "not allowed");
                return;
            }

            switch (action) {
                case "readLlms":
                    writeJson(resp, HttpServletResponse.SC_OK, readLlms(path, language));
                    return;
                case "previewRobots":
                    writeJson(resp, HttpServletResponse.SC_OK,
                            previewRobots(path, language, body.optJSONObject("decisions")));
                    return;
                case "applyRobots":
                    writeJson(resp, HttpServletResponse.SC_OK, applySiteFile(path, language,
                            body.optString("content", ""), ROBOTS_MIXIN, ROBOTS_PROP));
                    return;
                default:
                    deny(resp, HttpServletResponse.SC_BAD_REQUEST, "unknown action");
            }
        } catch (javax.jcr.AccessDeniedException e) {
            deny(resp, HttpServletResponse.SC_FORBIDDEN, "not allowed");
        } catch (IllegalArgumentException e) {
            // A refusal the caller can fix, not a server fault: say so with 400
            // and the reason, rather than folding it into "operation failed".
            deny(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.warn("site-files {} failed for {}: {}", action, path, e.getMessage());
            logger.debug("site-files failure", e);
            deny(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "operation failed");
        }
    }

    /**
     * The llms.txt the site serves, read where the llms module serves it from: the site's
     * {@code j:llms} in the live workspace. Read only: the file is written in the llms module's
     * own screen (Additional, SEO, Llms.txt).
     */
    private JSONObject readLlms(String path, String language) throws RepositoryException {
        JCRSessionWrapper live = JCRSessionFactory.getInstance()
                .getCurrentUserSession("live", Locale.forLanguageTag(language));
        JCRNodeWrapper liveSite = live.getNode(path).getResolveSite();
        String content = LlmsFile.of(liveSite);

        JSONObject out = new JSONObject();
        out.put("siteKey", liveSite.getName());
        out.put("moduleEnabled", liveSite instanceof JCRSiteNode site
                && site.getInstalledModulesWithAllDependencies().contains(LLMS_MODULE));
        out.put("content", content == null ? JSONObject.NULL : content);
        // An autocreated "# Title" is the module's placeholder, not a published file.
        out.put("placeholder", content != null && LlmsFile.PLACEHOLDER.equals(content.trim()));
        return out;
    }

    /**
     * The stance the stored file takes on each AI crawler today, and what the
     * file would look like with the caller's decisions merged in.
     *
     * Nothing is written. The proposed text is returned so the caller can show
     * the editor exactly which lines change before anything is overwritten.
     */
    private JSONObject previewRobots(String path, String language, JSONObject decisions) throws Exception {
        JCRSessionWrapper edit = JCRSessionFactory.getInstance()
                .getCurrentUserSession("default", Locale.forLanguageTag(language));
        JCRNodeWrapper site = edit.getNode(path).getResolveSite();
        JCRNodeWrapper editSite = edit.getNode(site.getPath());

        String current = editSite.hasProperty(ROBOTS_PROP) ? editSite.getProperty(ROBOTS_PROP).getString() : "";

        Map<String, String> wanted = new LinkedHashMap<>();
        if (decisions != null) {
            for (String token : decisions.keySet()) {
                wanted.put(token, decisions.optString(token, ""));
            }
        }
        String proposed = RobotsEditor.apply(current, wanted);

        // Where each crawler stands in the file as it is stored right now.
        RobotsRules rules = RobotsRules.parse(current);
        JSONArray agents = new JSONArray();
        for (Map.Entry<String, String> e : SiteFilesChecker.AI_TOKENS.entrySet()) {
            RobotsRules.Verdict v = rules.evaluate(e.getValue(), "/");
            JSONObject a = new JSONObject();
            a.put("name", e.getKey());
            a.put("token", e.getValue());
            a.put("named", v.namedExplicitly);
            a.put("current", v.allowed ? RobotsEditor.ALLOW : RobotsEditor.BLOCK);
            // What blocking this one would actually cost the site.
            a.put("purpose", AiCrawlers.purposes().getOrDefault(e.getKey(), AiCrawlers.OTHER));
            agents.put(a);
        }

        // The panel runs the crawler check against one representative page to show
        // what the server actually does, beside what robots.txt says it should.
        JCRNodeWrapper home = SitePages.homeOf(site);

        JSONObject out = new JSONObject();
        out.put("siteKey", site.getName());
        out.put("homePath", home == null ? JSONObject.NULL : home.getPath());
        out.put("current", current);
        out.put("proposed", proposed);
        out.put("changed", !proposed.equals(current));
        out.put("mixinPresent", editSite.isNodeType(ROBOTS_MIXIN));
        out.put("currentIsPlaceholder", ROBOTS_DEFAULT.equals(current.trim()));
        out.put("canWrite", editSite.hasPermission("jcr:modifyProperties"));
        out.put("agents", agents);
        return out;
    }

    /**
     * Writes exactly what it was handed, then publishes so the community
     * servlet can serve it. Shared by both files: they have the same shape, one
     * textarea property on the site node behind one mixin.
     */
    private JSONObject applySiteFile(String path, String language, String content, String mixin, String prop)
            throws Exception {
        if (content == null || content.trim().isEmpty()) {
            // Answered 200 with an error object until now, which is the one
            // refusal in this module that did not carry a matching status. The
            // frontend's shared call() helper only checks res.ok, so an empty
            // write was reported to the user as a success.
            throw new IllegalArgumentException("empty content");
        }
        JCRSessionWrapper edit = JCRSessionFactory.getInstance()
                .getCurrentUserSession("default", Locale.forLanguageTag(language));
        JCRNodeWrapper site = edit.getNode(path).getResolveSite();
        JCRNodeWrapper editSite = edit.getNode(site.getPath());

        if (!editSite.isNodeType(mixin)) {
            // Without the mixin the community servlet has nothing to read and the
            // file stays a 404, however good the text is.
            editSite.addMixin(mixin);
        }
        editSite.setProperty(prop, content);
        edit.save();

        JCRPublicationService.getInstance().publishByMainId(
                editSite.getIdentifier(), "default", "live",
                Collections.singleton(language), false, null);

        JSONObject out = new JSONObject();
        out.put("written", true);
        out.put("sitePath", site.getPath());
        out.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
        return out;
    }
}
