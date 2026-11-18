/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.pm.modules.editorialframe.internal.importer.ArchiveReader;
import org.jahia.pm.modules.editorialframe.internal.importer.ImportPlan;
import org.jahia.pm.modules.editorialframe.internal.importer.ImportWriter;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code POST /modules/editorial-frame/import?site=<key>&...}: imports an archive of skills, such
 * as the zip of a GitHub repository, as drafts of the site. The options map one skill to the voice
 * of the brand, one to the personas, YAML files to rules and CSV files to the glossary.
 * {@code dryRun=true} only reports.
 *
 * <p>The user needs {@code siteAdministrationAccess} on the site. The body is {@code application/zip},
 * which a cross-site form cannot send without a CORS preflight.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, immediate = true,
        property = {"alias=/editorial-frame/import"})
public class ImportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ImportServlet.class);
    static final String PERMISSION = "siteAdministrationAccess";
    static final int MAX_BODY_BYTES = 60 * 1024 * 1024;

    private final transient ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            importArchive(request, response);
        } catch (IOException e) {
            // Nothing may leave a servlet method. A broken socket ends here.
            logger.debug("The answer of the import could not be written", e);
        }
    }

    private void importArchive(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        String contentType = request.getContentType() == null ? "" : request.getContentType().split(";")[0].trim();
        if (!"application/zip".equalsIgnoreCase(contentType)) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "application/zip required");
            return;
        }
        String siteKey = request.getParameter("site");
        if (siteKey == null || !FrameReader.SITE_KEY.matcher(siteKey).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter site is missing or invalid");
            return;
        }
        try {
            JCRSessionWrapper probe = JCRSessionFactory.getInstance().getCurrentUserSession("default");
            if (!probe.nodeExists("/sites/" + siteKey)
                    || !probe.getNode("/sites/" + siteKey).hasPermission(PERMISSION)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "No site administration access on this site");
                return;
            }
            String language = request.getParameter("language");
            if (language == null || language.isBlank()) {
                language = FrameReader.defaultLanguage(probe, siteKey);
            }
            if (language == null || !language.matches("[a-zA-Z]{2,3}([_-][A-Za-z0-9]{2,8})?")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter language is invalid");
                return;
            }
            ArchiveReader archive;
            try (InputStream in = request.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "The archive is larger than 60 MB");
                    return;
                }
                archive = ArchiveReader.read(new ByteArrayInputStream(bytes));
            }
            ImportPlan plan = ImportPlan.of(archive.files, options(request, language));
            plan.skipped.addAll(0, archive.skipped);
            ObjectNode report;
            if ("true".equals(request.getParameter("dryRun"))) {
                report = mapper.createObjectNode();
                report.put("dryRun", true);
                report.put("files", archive.files.size());
                report.put("skills", plan.skills.size());
                report.set("skillNames", mapper.valueToTree(plan.skills.stream().map(s -> s.document.name).toArray()));
                report.put("personas", plan.personas.size());
                report.set("personaNames", mapper.valueToTree(plan.personas.stream().map(p -> p.title).toArray()));
                report.put("rules", plan.rules.size());
                report.put("glossary", plan.glossary.size());
                report.put("voice", plan.voice == null ? null : plan.voice.name);
                report.set("warnings", mapper.valueToTree(plan.warnings));
                report.set("skipped", mapper.valueToTree(plan.skipped));
            } else {
                Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
                JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("default", locale);
                report = ImportWriter.apply(session, siteKey, plan, request.getParameter("ref"), mapper);
                logger.info("{} imported {} skills, {} personas, {} rules and {} glossary terms into the site {}",
                        user.getName(), plan.skills.size(), plan.personas.size(), plan.rules.size(), plan.glossary.size(), siteKey);
            }
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writerWithDefaultPrettyPrinter().writeValue(response.getWriter(), report);
        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The archive cannot be read: " + e.getMessage());
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The import into the site {} failed", siteKey, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "The import failed: " + e.getMessage());
        }
    }

    private static ImportPlan.Options options(HttpServletRequest request, String language) {
        ImportPlan.Options options = new ImportPlan.Options();
        options.language = language;
        options.skillRoots = list(request.getParameter("skills"));
        options.voice = request.getParameter("voice");
        options.personas = request.getParameter("personas");
        if (request.getParameter("personasHeading") != null) {
            options.personasHeading = request.getParameter("personasHeading");
        }
        options.defaultPersona = request.getParameter("defaultPersona");
        options.rules = list(request.getParameter("rules"));
        options.glossary = list(request.getParameter("glossary"));
        if (request.getParameter("rulesSeverity") != null) {
            options.rulesSeverity = request.getParameter("rulesSeverity");
        }
        String activation = request.getParameter("activation");
        if (activation != null && List.of("auto", "manual", "pinned-only").contains(activation)) {
            options.activation = activation;
        }
        String action = request.getParameter("action");
        if (action != null && action.matches("[a-z]{2,20}")) {
            options.action = action;
        }
        List<String> consumers = list(request.getParameter("consumers"));
        if (!consumers.isEmpty()) {
            options.consumers = consumers;
        }
        return options;
    }

    private static List<String> list(String value) {
        List<String> items = new ArrayList<>();
        if (value != null) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    items.add(item.trim());
                }
            }
        }
        return items;
    }
}
