/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.Persona;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedFrameSkillTest {

    @Test
    void theSkillHoldsEachVoiceInFullAndItsBodyTheEssentials() {
        String voice = "# Digitall voice\n\n## Tone\n\nDirect and warm. " + "Details of the tone. ".repeat(100)
                + "\n\n## Rules\n\nNever promise a return. " + "Details of the rules. ".repeat(100);
        Frame frame = new Frame("digitall", "en",
                List.of(new FrameText("voice", "Digitall voice", "group", voice, List.of())),
                List.of(new Persona("investor", "Private investor", "Wants figures.", true, "site")),
                List.of(), "abc123def456");

        LoadedSkill skill = GeneratedFrameSkill.of(frame);

        String full = skill.files.get("voices.md");
        assertTrue(full.contains("## Digitall voice (whole group)"), full);
        assertTrue(full.endsWith("Details of the rules.\n"), full);
        assertTrue(skill.body.contains("- Rules: Never promise a return."), skill.body);
        assertFalse(skill.body.contains("Details of the rules."), skill.body);
        assertTrue(skill.body.contains("The file voices.md holds every voice in full, personas.md"), skill.body);
    }

    @Test
    void aFrameWithoutVoiceHasNoFileOfVoices() {
        Frame frame = new Frame("digitall", "en", List.of(),
                List.of(new Persona("investor", "Private investor", "Wants figures.", true, "site")),
                List.of(), "abc123def456");

        LoadedSkill skill = GeneratedFrameSkill.of(frame);

        assertNull(skill.files.get("voices.md"));
        assertTrue(skill.body.contains("The file personas.md holds every reader in full, rules.csv every rule."), skill.body);
    }
}
