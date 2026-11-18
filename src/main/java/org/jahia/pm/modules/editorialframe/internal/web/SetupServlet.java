/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.pm.modules.editorialframe.internal.importer.ImportWriter;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code POST /modules/editorial-frame/setup?site=<key>}: creates the folder
 * {@code contents/ai-assistant} of a site and its sub-folders {@code voice}, {@code personas},
 * {@code rules}, {@code glossary} and {@code skills} when they are missing, and sets the one type that each folder
 * accepts. The folders are drafts in the edit workspace. The user needs
 * {@code siteAdministrationAccess} on the site, and the body type is {@code application/json}.
 */
@Component(service = {HttpServlet.class, Servlet.class}, immediate = true,
        property = {"alias=/editorial-frame/setup"})
public class SetupServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SetupServlet.class);

    private final transient ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        String contentType = request.getContentType() == null ? "" : request.getContentType().split(";")[0].trim();
        if (!"application/json".equalsIgnoreCase(contentType)) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "application/json required");
            return;
        }
        String siteKey = request.getParameter("site");
        if (siteKey == null || !FrameReader.SITE_KEY.matcher(siteKey).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The parameter site is missing or invalid");
            return;
        }
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("default");
            if (!session.nodeExists("/sites/" + siteKey + "/contents")
                    || !session.getNode("/sites/" + siteKey).hasPermission(ImportServlet.PERMISSION)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "No site administration access on this site");
                return;
            }
            JCRNodeWrapper base = ImportWriter.ensureStructure(session, siteKey);
            ObjectNode body = mapper.createObjectNode();
            body.put("folder", base.getPath());
            body.set("accepts", accepts(base));
            ObjectNode folders = body.putObject("subFolders");
            JCRNodeIteratorWrapper children = base.getNodes();
            while (children.hasNext()) {
                JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
                folders.set(child.getName(), accepts(child));
            }
            logger.info("{} set up the editorial frame folders of the site {}", user.getName(), siteKey);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writerWithDefaultPrettyPrinter().writeValue(response.getWriter(), body);
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The setup of the site {} failed", siteKey, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "The setup failed: " + e.getMessage());
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode accepts(JCRNodeWrapper folder) throws RepositoryException {
        com.fasterxml.jackson.databind.node.ArrayNode types = mapper.createArrayNode();
        if (folder.hasProperty("j:contributeTypes")) {
            for (Value value : folder.getProperty("j:contributeTypes").getValues()) {
                types.add(value.getString());
            }
        }
        return types;
    }
}
