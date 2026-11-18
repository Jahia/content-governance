/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jahia.pm.modules.editorialframe.internal.frame.Texts;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillDocument;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRUserNode;
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
import java.io.IOException;
import java.io.InputStream;

/**
 * {@code /modules/editorial-frame/voice}: the voice of the current editor. {@code GET} reads it,
 * {@code POST} with a JSON body {@code {"voice": "...", "voiceSkill": "..."}} writes it. A user only
 * ever reads or writes their own voice. The voice applies where the brand allows it.
 */
@Component(service = {HttpServlet.class, Servlet.class}, immediate = true,
        property = {"alias=/editorial-frame/voice"})
public class VoiceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(VoiceServlet.class);
    static final int MAX_VOICE = 4000;

    private final transient ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JCRUserNode user = currentUser(response);
        if (user == null) {
            return;
        }
        ObjectNode body = mapper.createObjectNode();
        try {
            body.put("voice", user.hasProperty("voice") ? user.getProperty("voice").getString() : null);
            body.put("voiceSkill", user.hasProperty("voiceSkill") ? user.getProperty("voiceSkill").getString() : null);
        } catch (RepositoryException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "The voice cannot be read");
            return;
        }
        write(response, body);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JCRUserNode user = currentUser(response);
        if (user == null) {
            return;
        }
        String contentType = request.getContentType() == null ? "" : request.getContentType().split(";")[0].trim();
        if (!"application/json".equalsIgnoreCase(contentType)) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "application/json required");
            return;
        }
        JsonNode input;
        try (InputStream in = request.getInputStream()) {
            input = mapper.readTree(in.readNBytes(64 * 1024));
        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The body is not valid JSON");
            return;
        }
        String voice = input.path("voice").isTextual() ? input.get("voice").asText().strip() : null;
        String voiceSkill = input.path("voiceSkill").isTextual() ? input.get("voiceSkill").asText().strip() : null;
        if (voice != null && (voice.length() > MAX_VOICE || Texts.hasInvisibleCharacters(voice))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "The voice is longer than " + MAX_VOICE + " characters or holds invisible characters");
            return;
        }
        if (voiceSkill != null && !voiceSkill.isEmpty() && !SkillDocument.NAME.matcher(voiceSkill).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "voiceSkill is not a skill name");
            return;
        }
        String path = user.getPath();
        try {
            // The system session writes the node of the current user only: the path comes from the
            // authenticated user, never from the request.
            JCRTemplate.getInstance().doExecuteWithSystemSession(session -> {
                JCRNodeWrapper node = session.getNode(path);
                if (!node.isNodeType("efmix:authorVoice")) {
                    node.addMixin("efmix:authorVoice");
                }
                setOrRemove(node, "voice", voice);
                setOrRemove(node, "voiceSkill", voiceSkill);
                session.save();
                return null;
            });
        } catch (RepositoryException e) {
            logger.warn("The voice of {} cannot be written: {}", user.getName(), e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "The voice cannot be written");
            return;
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("voice", voice);
        body.put("voiceSkill", voiceSkill);
        write(response, body);
    }

    private static void setOrRemove(JCRNodeWrapper node, String property, String value) throws RepositoryException {
        if (value == null || value.isEmpty()) {
            if (node.hasProperty(property)) {
                node.getProperty(property).remove();
            }
        } else {
            node.setProperty(property, value);
        }
    }

    private JCRUserNode currentUser(HttpServletResponse response) throws IOException {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return null;
        }
        JCRUserNode node = JahiaUserManagerService.getInstance().lookupUserByPath(user.getLocalPath());
        if (node == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No user node");
        }
        return node;
    }

    private void write(HttpServletResponse response, ObjectNode body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), body);
    }
}
