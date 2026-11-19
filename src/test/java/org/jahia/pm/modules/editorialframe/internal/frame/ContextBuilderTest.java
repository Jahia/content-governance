/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameContext;
import org.jahia.pm.modules.editorialframe.api.FrameRequest;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.Persona;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBuilderTest {

    private static Frame frame(List<WritingRule> rules) {
        return new Frame("digitall", "en",
                List.of(new FrameText("voice", "Digitall voice", "site", "Direct and warm. Never familiar.", List.of())),
                List.of(new Persona("investor", "Private investor", "Wants figures, no jargon.", true, "site"),
                        new Persona("advisor", "Wealth advisor", "Knows the products well.", false, "site")),
                rules, "abc123def456");
    }

    @Test
    void theDefaultPersonaComesFirstAndTheOthersAreListed() {
        FrameContext context = ContextBuilder.build(frame(List.of()), FrameRequest.of("digitall", "en", null));
        assertEquals("investor", context.persona());
        assertTrue(context.text().contains("## Reader: Private investor"));
        assertTrue(context.text().contains("- Wealth advisor (advisor): Knows the products well."));
        assertTrue(context.text().contains("Direct and warm"));
        assertEquals("abc123def456", context.fingerprint());
    }

    @Test
    void aNamedPersonaReplacesTheDefault() {
        FrameContext context = ContextBuilder.build(frame(List.of()), FrameRequest.of("digitall", "en", "Wealth advisor"));
        assertEquals("advisor", context.persona());
        assertTrue(context.text().contains("## Reader: Wealth advisor"));
    }

    @Test
    void theRulesThatTheTextBreaksComeFirstWhenTheBudgetIsShort() {
        List<WritingRule> rules = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rules.add(new WritingRule("r" + i, WritingRule.Kind.FORBIDDEN, "term" + i, WritingRule.MatchMode.TERM, null,
                    null, WritingRule.Severity.WARNING, null, "site", List.of(), List.of(), null, null, null, null));
        }
        rules.add(new WritingRule("leader", WritingRule.Kind.FORBIDDEN, "leader mondial", WritingRule.MatchMode.TERM, null,
                null, WritingRule.Severity.WARNING, "Not provable.", "site", List.of(), List.of(), null, null, null, null));
        FrameContext context = ContextBuilder.build(frame(rules),
                new FrameRequest("digitall", "en", null, 450, "Le leader mondial du CMS"));
        assertTrue(context.truncated());
        assertTrue(context.text().contains("Avoid \"leader mondial\". Why: Not provable."));
        assertFalse(context.text().contains("term199"));
    }

    @Test
    void theModelSeesTheFirstExamplesOfARuleEachOneCut() {
        String longExample = "A long right example. ".repeat(20);
        WritingRule rule = new WritingRule("leader", WritingRule.Kind.FORBIDDEN, "leading DXP", WritingRule.MatchMode.TERM,
                null, null, WritingRule.Severity.BLOCKING, "Unsupported superiority claim.", "site",
                List.of("Jahia is the leading DXP.", "The leading DXP\nfor all.", "A third wrong one."),
                List.of(longExample), null, null, null, null);

        String text = ContextBuilder.build(frame(List.of(rule)), FrameRequest.of("digitall", "en", null)).text();

        assertTrue(text.contains("A Wrong example breaks its rule"), text);
        assertTrue(text.contains("Avoid \"leading DXP\". (required by the brand) Why: Unsupported superiority claim. "
                + "Wrong: \"Jahia is the leading DXP.\"; \"The leading DXP for all.\". Right: \"A long right"), text);
        assertFalse(text.contains("A third wrong one"));
        assertTrue(text.contains(longExample.strip().substring(0, ContextBuilder.MAX_EXAMPLE_CHARS) + "...\"."));
    }

    @Test
    void aFrameWithNoExampleSaysNothingOfExamples() {
        WritingRule rule = new WritingRule("leader", WritingRule.Kind.FORBIDDEN, "leading DXP", WritingRule.MatchMode.TERM,
                null, null, WritingRule.Severity.WARNING, null, "site", List.of(), List.of(), null, null, null, null);
        String text = ContextBuilder.build(frame(List.of(rule)), FrameRequest.of("digitall", "en", null)).text();
        assertFalse(text.contains("Wrong"), text);
    }

    @Test
    void anEmptyFrameGivesAnEmptyContext() {
        Frame empty = new Frame("digitall", "en", List.of(), List.of(), List.of(), null);
        assertTrue(ContextBuilder.build(empty, FrameRequest.of("digitall", "en", null)).isEmpty());
    }

    private static Frame longFrame(List<WritingRule> rules, List<GlossaryTerm> glossary) {
        return new Frame("digitall", "en",
                List.of(new FrameText("voice", "Digitall voice", "site", EssentialsTest.longVoice(), List.of())),
                List.of(new Persona("investor", "Private investor", "Wants figures. " + "More about the investor. ".repeat(80),
                        true, "site", "Wants figures, no jargon.")),
                rules, glossary, "abc123def456");
    }

    private static String section(String text, String heading) {
        int start = text.indexOf(heading);
        int end = text.indexOf("\n## ", start + heading.length());
        return text.substring(start, end < 0 ? text.length() : end);
    }

    @Test
    void aLongVoiceGivesItsEssentialsAndNamesTheFileOfItsFullText() {
        FrameContext context = ContextBuilder.build(longFrame(List.of(), List.of()), FrameRequest.of("digitall", "en", null));
        String text = context.text();
        String voice = section(text, "## Voice of the brand");
        assertTrue(voice.contains("### Digitall voice: essentials"), text);
        assertTrue(voice.contains("- Rules: Never promise a return."), text);
        assertFalse(voice.contains("guideline 1 "), text);
        assertTrue(Texts.tokens(voice) <= Essentials.TOKENS + 20, voice);
        assertTrue(text.contains("## Reader: Private investor, essentials\n"), text);
        assertTrue(text.contains("Wants figures, no jargon."), text);
        assertFalse(text.contains("More about the investor."), text);
        String note = section(text, "## Full texts");
        assertTrue(note.contains("the skill brand-frame of the site \"digitall\""), note);
        assertTrue(note.contains("voices.md holds each voice in full"), note);
        assertTrue(note.contains("personas.md holds each reader in full"), note);
        assertFalse(note.contains("glossary.csv"), note);
        assertFalse(context.truncated());
    }

    @Test
    void theEssentialsGoInWhateverTheBudgetAndTheRulesTakeWhatRemains() {
        List<WritingRule> rules = List.of(new WritingRule("r", WritingRule.Kind.FORBIDDEN, "leader", WritingRule.MatchMode.TERM,
                null, null, WritingRule.Severity.BLOCKING, null, "site", List.of(), List.of(), null, null, null, null));
        FrameContext context = ContextBuilder.build(longFrame(rules, List.of()),
                new FrameRequest("digitall", "en", null, 200, "A text"));
        String text = context.text();
        assertTrue(text.contains("- Rules: Never promise a return."), text);
        assertTrue(text.contains("Wants figures, no jargon."), text);
        assertFalse(text.contains("## Writing rules"), "A section with no line that fits is left out: " + text);
        assertTrue(context.truncated());

        FrameContext large = ContextBuilder.build(longFrame(rules, List.of()),
                new FrameRequest("digitall", "en", null, 3000, "A text"));
        assertTrue(large.text().contains("Avoid \"leader\". (required by the brand)"), large.text());
        assertEquals(section(text, "## Voice of the brand"), section(large.text(), "## Voice of the brand"),
                "The budget never changes the essentials");
        assertFalse(large.truncated());
    }

    @Test
    void aShortFrameGoesInWholeWithoutANoteOfTheFullTexts() {
        FrameContext context = ContextBuilder.build(frame(List.of()), FrameRequest.of("digitall", "en", null));
        assertTrue(context.text().contains("### Digitall voice\nDirect and warm. Never familiar.\n"), context.text());
        assertTrue(context.text().contains("## Reader: Private investor\n"), context.text());
        assertFalse(context.text().contains("## Full texts"), context.text());
    }

    private static final GlossaryTerm JAHIA = new GlossaryTerm("t1", "Jahia", "en", "Jahia", "fr", null, "site");
    private static final GlossaryTerm TOKEN = new GlossaryTerm("t2", "access token", "en", "jeton d'accès", "fr", null, "site");

    @Test
    void onlyTheGlossaryTermsOfTheTextGoIn() {
        Frame frame = new Frame("digitall", "fr", List.of(), List.of(), List.of(), List.of(JAHIA, TOKEN), "f1");

        String text = ContextBuilder.build(frame, new FrameRequest("digitall", "fr", null, 1500,
                "<p>Create an <b>access token</b>.</p>")).text();

        assertTrue(text.contains("- \"access token\" (en): write \"jeton d'accès\" in fr."), text);
        assertFalse(text.contains("\"Jahia\""), text);
        assertTrue(section(text, "## Full texts").contains("glossary.csv holds every glossary term"), text);
    }

    @Test
    void withoutATextNoGlossaryTermGoesIn() {
        Frame frame = new Frame("digitall", "fr", List.of(), List.of(), List.of(), List.of(JAHIA, TOKEN), "f1");

        String text = ContextBuilder.build(frame, FrameRequest.of("digitall", "fr", null)).text();

        assertFalse(text.contains("## Glossary"), text);
        assertTrue(text.contains("glossary.csv holds every glossary term"), text);
    }
}
