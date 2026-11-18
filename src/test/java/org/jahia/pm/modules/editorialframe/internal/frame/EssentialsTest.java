/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EssentialsTest {

    /** A voice of several pages, whose last section holds the rules. */
    static String longVoice() {
        StringBuilder voice = new StringBuilder("# Digitall voice\n\nWe speak to investors as equals. ")
                .append("We explain before we sell.\n");
        for (String section : new String[]{"Tone", "Words", "Figures", "Examples"}) {
            voice.append("\n## ").append(section).append("\n\n");
            for (int i = 0; i < 12; i++) {
                voice.append("- The ").append(section.toLowerCase()).append(" guideline ").append(i)
                        .append(" fills the page with details that only a long text needs. It goes on.\n");
            }
        }
        voice.append("\n## Rules\n\nNever promise a return. Always name the risk.\n");
        return voice.toString();
    }

    @Test
    void aShortTextIsItsOwnEssentials() {
        Essentials essentials = Essentials.of(null, "# Voice\nDirect and warm. Never familiar.");
        assertEquals("Direct and warm. Never familiar.", essentials.text);
        assertFalse(essentials.abridged);
    }

    @Test
    void theWrittenEssentialsComeFirst() {
        Essentials essentials = Essentials.of("- Direct.\n- Never promise a return.", longVoice());
        assertEquals("- Direct.\n- Never promise a return.", essentials.text);
        assertTrue(essentials.abridged);
    }

    @Test
    void writtenEssentialsLongerThanTheLimitKeepTheirFirstWholeLines() {
        StringBuilder written = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            written.append("- Essential line number ").append(i).append(" of the brand.\n");
        }
        Essentials essentials = Essentials.of(written.toString(), longVoice());
        assertTrue(essentials.text.length() <= Essentials.MAX_CHARS, essentials.text);
        assertTrue(essentials.text.endsWith("of the brand."), essentials.text);
        assertTrue(essentials.text.startsWith("- Essential line number 0 "));
    }

    @Test
    void aLongTextGivesAnOutlineThatKeepsItsLastSection() {
        Essentials essentials = Essentials.of(null, longVoice());
        assertTrue(essentials.abridged);
        assertTrue(Texts.tokens(essentials.text) <= Essentials.TOKENS, essentials.text);
        assertTrue(essentials.text.startsWith("- We speak to investors as equals.\n"), essentials.text);
        assertTrue(essentials.text.contains("- Tone: The tone guideline 0 fills the page"), essentials.text);
        assertTrue(essentials.text.endsWith("- Rules: Never promise a return."), essentials.text);
        assertFalse(essentials.text.contains("guideline 1 "), essentials.text);
    }

    @Test
    void aLongTextWithManySectionsSharesTheLimitAndKeepsEachHeading() {
        StringBuilder voice = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            voice.append("## Section ").append(i).append("\n\n");
            voice.append("This sentence of the section ").append(i).append(" is long enough to need a cut, because it ")
                    .append("gives many details about the brand, its readers, its words and its tone of voice.\n\n")
                    .append("x".repeat(400)).append("\n\n");
        }
        Essentials essentials = Essentials.of(null, voice.toString());
        assertTrue(essentials.text.length() <= Essentials.MAX_CHARS, essentials.text);
        assertTrue(essentials.text.contains("- Section 0: This sentence"), essentials.text);
        assertTrue(essentials.text.contains("- Section 11: This sentence"), essentials.text);
        assertTrue(essentials.text.contains("…"), essentials.text);
    }

    @Test
    void aLongTextWithoutHeadingsKeepsTheFirstSentenceOfEachParagraph() {
        StringBuilder voice = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            voice.append("Paragraph ").append(i).append(" says one thing. ").append("Then it says more. ".repeat(20))
                    .append("\n\n");
        }
        Essentials essentials = Essentials.of(null, voice.toString());
        assertTrue(essentials.abridged);
        assertTrue(essentials.text.contains("- Paragraph 0 says one thing."), essentials.text);
        assertTrue(essentials.text.endsWith("- Paragraph 5 says one thing."), essentials.text);
    }
}
