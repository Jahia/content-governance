/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JahiaSkillKeysTest {

    @Test
    void aJahiaToolNameHasLowerCaseWordsJoinedByDots() {
        for (String name : List.of("content.get", "page.list", "a.b.c", "frame_v2.check_1")) {
            assertTrue(JahiaSkillKeys.TOOL.matcher(name).matches(), name);
        }
        for (String name : List.of("Read", "content", "content.", ".get", "content..get", "Content.get", "content.get-x",
                "1content.get")) {
            assertFalse(JahiaSkillKeys.TOOL.matcher(name).matches(), name);
        }
    }

    @Test
    void aLongNameThatEndsBadlyIsRefusedAtOnce() {
        String name = "a.".repeat(100_000) + "-";
        assertFalse(JahiaSkillKeys.TOOL.matcher(name).matches());
    }
}
