/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.SkillCoverage;
import org.jahia.pm.modules.editorialframe.api.SkillExample;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDocumentTest {

    /** The declaration of the forms skill of the assistant, written as a SKILL.md. */
    private static final String FORMS = "---\n"
            + "name: forms\n"
            + "description: Create or change a form of the site. Use when an editor asks for a form.\n"
            + "allowed-tools: page.outline content.get content.create\n"
            + "metadata:\n"
            + "  jahia-label: Forms\n"
            + "  jahia-scope: create or change a form of the site.\n"
            + "  jahia-action: create\n"
            + "  jahia-in-scope:\n"
            + "    - Add a contact form to the Contact page.\n"
            + "    - message: Put this image above the form.\n"
            + "      attached-images: 1\n"
            + "      note: an attached image in a form\n"
            + "  jahia-out-of-scope:\n"
            + "    - Write me a survey about pizza.\n"
            + "---\n"
            + "Create a form with content.create, as a child of the area that the user names.\n";

    @Test
    void aNameThatBreaksTheRuleIsFixedAndCutWithoutADashAtItsEnd() {
        SkillDocument cut = SkillDocument.parse("---\nname: " + "a".repeat(63) + " x\ndescription: D.\n---\nBody", "folder");
        SkillDocument fromFolder = SkillDocument.parse("---\nname: \"%%%\"\ndescription: D.\n---\nBody",
                "b".repeat(63) + "_c");
        SkillDocument long64 = SkillDocument.parse("---\nname: " + "a".repeat(65) + "\ndescription: D.\n---\nBody", null);

        assertEquals("a".repeat(63), cut.name);
        assertEquals(List.of("The name '" + "a".repeat(63) + " x' breaks the naming rule: '" + "a".repeat(63) + "' is used",
                "The name '" + "a".repeat(63) + "' differs from the folder 'folder'"), cut.warnings);
        assertEquals("b".repeat(63), fromFolder.name);
        assertEquals("a".repeat(64), long64.name);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> SkillDocument.parse("---\nname: \"%%%\"\ndescription: D.\n---\nBody", null));
        assertEquals("The name '%%%' gives no valid skill name", refused.getMessage());
    }

    @Test
    void theJahiaKeysGiveTheLabelTheCoverageAndTheTools() {
        SkillDocument document = SkillDocument.parse(FORMS, "forms");

        assertEquals("Forms", document.label);
        assertEquals(List.of("page.outline", "content.get", "content.create"), document.tools);
        SkillCoverage coverage = document.coverage;
        assertEquals("create or change a form of the site.", coverage.sentence());
        assertEquals(2, coverage.inScope().size());
        assertEquals("Add a contact form to the Contact page.", coverage.inScope().get(0).message());
        SkillExample withImage = coverage.inScope().get(1);
        assertEquals(1, withImage.attachedImages());
        assertEquals("an attached image in a form", withImage.note());
        assertEquals("Write me a survey about pizza.", coverage.outOfScope().get(0).message());
        assertEquals("Create a form with content.create, as a child of the area that the user names.", document.body);
        assertTrue(document.warnings.isEmpty(), document.warnings.toString());
    }

    @Test
    void theSkillsOfThisModuleParseWithNoWarning() throws IOException {
        for (String name : List.of("article-brief", "seo-review")) {
            try (InputStream in = getClass().getResourceAsStream("/skills/" + name + "/SKILL.md")) {
                SkillDocument document = SkillDocument.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), name);

                assertTrue(document.warnings.isEmpty(), name + ": " + document.warnings);
                assertEquals(2, document.coverage.inScope().size(), name);
                assertNotNull(document.label, name);
            }
        }
    }

    @Test
    void aSkillWithNoJahiaKeysIsCoveredByItsDescription() {
        SkillDocument document = SkillDocument.parse("---\nname: brief\ndescription: |\n  Write a brief.\n  Use it for articles.\n---\nBody", "brief");

        assertNull(document.label);
        assertEquals("Write a brief. Use it for articles.", document.coverage.sentence());
        assertTrue(document.coverage.inScope().isEmpty());
        assertTrue(document.tools.isEmpty());
    }

    @Test
    void theToolsOfAnotherAgentAreLeftOut() {
        SkillDocument document = SkillDocument.parse("---\nname: review\ndescription: Review.\n"
                + "allowed-tools: Read Bash(git:*), content.get\n---\nBody", "review");

        assertEquals(List.of("content.get"), document.tools);
        assertEquals(2, document.warnings.size(), document.warnings.toString());
    }

    @Test
    void aValueOverALimitIsCutOrLeftOut() {
        StringBuilder front = new StringBuilder("---\nname: many\ndescription: Many.\nmetadata:\n  jahia-in-scope:\n");
        for (int i = 0; i < 7; i++) {
            front.append("    - Message ").append(i).append('\n');
        }
        front.append("    - message: \"\"\n");
        front.append("  jahia-out-of-scope:\n    - message: An image too many.\n      attached-images: 40\n");
        front.append("  jahia-label: ").append("x".repeat(80)).append("\n---\nBody");
        SkillDocument document = SkillDocument.parse(front.toString(), "many");

        assertEquals(JahiaSkillKeys.MAX_EXAMPLES, document.coverage.inScope().size());
        assertEquals(0, document.coverage.outOfScope().get(0).attachedImages());
        assertEquals(JahiaSkillKeys.MAX_LABEL_CHARS, document.label.length());
        assertEquals(3, document.warnings.size(), document.warnings.toString());
    }

    @Test
    void aDeclarationOfTheAssistantReadsItsKeyAttachedImages() {
        List<String> warnings = new ArrayList<>();
        List<SkillExample> examples = JahiaSkillKeys.examples(
                List.of(Map.of("message", "Crop this.", "attachedImages", 2)), JahiaSkillKeys.IN_SCOPE, warnings);

        assertEquals(2, examples.get(0).attachedImages());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void theMetadataOfANodeGivesBackTheExamples() {
        Map<String, Object> metadata = JcrSkills.metadata(List.of(
                "jahia-label=Forms",
                "jahia-in-scope=[\"Add a contact form.\",{\"message\":\"Put this image above.\",\"attached-images\":1}]",
                "jahia-scope=a=b sentence",
                "broken"));
        List<String> warnings = new ArrayList<>();
        SkillCoverage coverage = JahiaSkillKeys.coverage(metadata, "Forms.", warnings);

        assertEquals("Forms", JahiaSkillKeys.label(metadata, warnings));
        assertEquals("a=b sentence", coverage.sentence());
        assertEquals(2, coverage.inScope().size());
        assertEquals(1, coverage.inScope().get(1).attachedImages());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
