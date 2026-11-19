/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillPathsTest {

    @Test
    void theSlashesAtTheEndOfAPathGoAndTheOthersStay() {
        assertEquals("_shared", SkillPaths.withoutTrailingSlashes("_shared///"));
        assertEquals("/context/audience", SkillPaths.withoutTrailingSlashes("/context/audience/"));
        assertEquals("notes.md", SkillPaths.withoutTrailingSlashes("notes.md"));
        assertEquals("", SkillPaths.withoutTrailingSlashes("//"));
    }

    @Test
    void aLongRunOfSlashesBeforeAnotherCharacterStaysWhole() {
        String path = "/".repeat(256 * 1024) + "x";
        assertEquals(path, SkillPaths.withoutTrailingSlashes(path));
    }
}
