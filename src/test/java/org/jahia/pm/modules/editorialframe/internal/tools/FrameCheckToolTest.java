/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jahia.pm.modules.editorialframe.api.EditorialFrameService;
import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameContext;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.SkillContent;
import org.jahia.pm.modules.editorialframe.api.SkillSummary;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Kind;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Severity;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameCheckToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aMissingMandatoryMentionComesAsMissingAndNotAsAMatch() {
        Frames frames = new Frames(List.of(
                new RuleViolation("r1", Kind.MANDATORY, Severity.BLOCKING, "Capital at risk", null, null,
                        "The regulator asks for it.", null),
                new RuleViolation("r2", Kind.FORBIDDEN, Severity.WARNING, "robust", "Robust", null, null,
                        "A Robust platform")));

        JsonNode violations = new FrameCheckTool(frames).execute(input(true), new Context()).payload().path("violations");

        assertEquals(List.of(true), frames.documents);
        assertEquals("Capital at risk", violations.get(0).path("missing").asText());
        assertFalse(violations.get(0).has("match"), "A missing mention has no match");
        assertEquals("Robust", violations.get(1).path("match").asText());
        assertFalse(violations.get(1).has("missing"));
    }

    @Test
    void eachViolationNamesItsRuleAndTheFolderOfTheRule() {
        Frames frames = new Frames(List.of(new RuleViolation("compliance.guaranteed", Kind.FORBIDDEN, Severity.WARNING,
                "guaranteed", "Guaranteed", null, null, "Guaranteed results", "/sites/digitall/contents/legal")));

        JsonNode violation = new FrameCheckTool(frames).execute(input(null), new Context()).payload().path("violations").get(0);

        assertEquals("compliance.guaranteed", violation.path("ruleId").asText());
        assertEquals("/sites/digitall/contents/legal", violation.path("folder").asText());
    }

    @Test
    void aTextIsOneFieldUnlessTheCallSaysItIsAWholePage() {
        Frames frames = new Frames(List.of());

        ToolResult result = new FrameCheckTool(frames).execute(input(null), new Context());

        assertTrue(result.payload().path("ok").asBoolean());
        assertEquals(List.of(false), frames.documents);
    }

    @Test
    void aTranslationIsCheckedAgainstTheGlossaryAndAMissingRenderingComesAsMissing() {
        Frames frames = new Frames(List.of(new RuleViolation("t1", Kind.GLOSSARY, Severity.WARNING, "access token", null,
                "jeton d'accès", null, "Create an access token.")));
        var input = MAPPER.createObjectNode().put("siteKey", "digitall").put("locale", "fr")
                .put("text", "Créez un jeton d'authentification.").put("sourceText", "Create an access token.")
                .put("sourceLocale", "en");

        JsonNode violations = new FrameCheckTool(frames).execute(input, new Context()).payload().path("violations");

        assertEquals(List.of("en>fr"), frames.translations);
        assertEquals("glossary", violations.get(0).path("kind").asText());
        assertEquals("jeton d'accès", violations.get(0).path("missing").asText());
        assertFalse(violations.get(0).has("match"));
    }

    @Test
    void aForbiddenTranslationComesAsAMatchWithTheRenderingToWrite() {
        Frames frames = new Frames(List.of(new RuleViolation("t5", Kind.GLOSSARY, Severity.WARNING, "access token",
                "jeton d'authentification", "jeton d'accès", null, "Créez un jeton d'authentification.")));
        var input = MAPPER.createObjectNode().put("siteKey", "digitall").put("locale", "fr")
                .put("text", "Créez un jeton d'authentification.").put("sourceText", "Create an access token.")
                .put("sourceLocale", "en");

        JsonNode violation = new FrameCheckTool(frames).execute(input, new Context()).payload().path("violations").get(0);

        assertEquals("glossary", violation.path("kind").asText());
        assertEquals("jeton d'authentification", violation.path("match").asText());
        assertEquals("jeton d'accès", violation.path("replacement").asText());
        assertFalse(violation.has("missing"));
    }

    @Test
    void theSchemaSaysWhatAWholePageIs() {
        String document = new FrameCheckTool(new Frames(List.of())).inputSchema()
                .path("properties").path("document").path("description").asText();
        assertTrue(document.contains("content item shown as its own page"));
        assertTrue(document.contains("teaser"));
    }

    private static JsonNode input(Boolean document) {
        var input = MAPPER.createObjectNode().put("siteKey", "digitall").put("locale", "en")
                .put("text", "<p>A Robust platform.</p>");
        if (document != null) {
            input.put("document", document);
        }
        return input;
    }

    /** A frame service that returns fixed violations and records the document flag of each check. */
    private static final class Frames implements EditorialFrameService {
        final List<RuleViolation> violations;
        final List<Boolean> documents = new ArrayList<>();
        final List<String> translations = new ArrayList<>();

        Frames(List<RuleViolation> violations) {
            this.violations = violations;
        }

        @Override
        public List<RuleViolation> check(String siteKey, String language, String text, boolean document) {
            documents.add(document);
            return violations;
        }

        @Override
        public List<GlossaryTerm> glossary(String siteKey, String termLanguage, String translationLanguage) {
            return List.of();
        }

        @Override
        public List<RuleViolation> checkTranslation(String siteKey, String sourceLanguage, String sourceText,
                                                    String language, String text, boolean document) {
            translations.add(sourceLanguage + ">" + language);
            return violations;
        }

        @Override
        public Frame getFrame(String siteKey, String language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FrameContext buildContext(FrameRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> authorVoice(String siteKey, String language, String username, String contentPath) {
            return Optional.empty();
        }

        @Override
        public List<SkillSummary> listSkills(String siteKey, String consumer) {
            return List.of();
        }

        @Override
        public List<SkillContent> listModuleSkills(String consumer) {
            return List.of();
        }

        @Override
        public Optional<SkillContent> readSkill(String siteKey, String name) {
            return Optional.empty();
        }

        @Override
        public Optional<String> readSkillFile(String siteKey, String name, String relativePath) {
            return Optional.empty();
        }
    }

    /** A tool context of a user who may read the site. */
    private static final class Context implements ToolContext {

        @Override
        public String username() {
            return "editor";
        }

        @Override
        public String workspace() {
            return "default";
        }

        @Override
        public Locale locale() {
            return Locale.ENGLISH;
        }

        @Override
        public Optional<String> conversationId() {
            return Optional.empty();
        }

        @Override
        public Audit audit() {
            return (event, details) -> {
            };
        }

        @Override
        public Optional<HttpServletRequest> request() {
            return Optional.empty();
        }

        @Override
        public Optional<HttpServletResponse> response() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T withUserSession(JcrCallback<T> callback) {
            return (T) Boolean.TRUE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T withUserSession(Locale locale, JcrCallback<T> callback) {
            return (T) Boolean.TRUE;
        }
    }
}
