/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextsTest {

    @Test
    void trimRemovesTheCharacterAtBothEndsOnly() {
        assertEquals("a-b", Texts.trim("--a-b---", '-'));
        assertEquals("skills/content", Texts.trim("/skills/content/", '/'));
        assertEquals("plain", Texts.trim("plain", '-'));
    }

    @Test
    void trimGivesAnEmptyTextWhenEveryCharacterIsTheEdge() {
        assertEquals("", Texts.trim("----", '-'));
        assertEquals("", Texts.trim("", '-'));
    }

    @Test
    void trimKeepsTheCharacterInsideTheText() {
        assertEquals("a--b", Texts.trim("a--b", '-'));
        assertEquals("a", Texts.trim("--a", '-'));
        assertEquals("a", Texts.trim("a--", '-'));
    }
}
