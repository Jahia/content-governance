/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Kind;
import org.jahia.pm.modules.editorialframe.api.WritingRule.MatchMode;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCheckerTest {

    private static WritingRule rule(Kind kind, String pattern, MatchMode mode, String replacement, String language) {
        return new WritingRule("id-" + pattern, kind, pattern, mode, replacement, language, Severity.BLOCKING,
                "Because.", "site");
    }

    @Test
    void aTermMatchesAWholeWordIgnoringCase() {
        List<RuleViolation> found = RuleChecker.check(List.of(rule(Kind.FORBIDDEN, "robust", MatchMode.TERM, null, null)),
                "en", "<p>A <b>Robust</b> platform, with robustness.</p>", false);
        assertEquals(1, found.size());
        assertEquals("Robust", found.get(0).match());
        assertTrue(found.get(0).excerpt().contains("Robust platform"));
    }

    @Test
    void aPhraseMatchesAcrossANonBreakingSpace() {
        List<RuleViolation> found = RuleChecker.check(List.of(rule(Kind.REPLACE, "en nuage", MatchMode.TERM, "cloud", "fr")),
                "fr", "Une offre en&nbsp;nuage souveraine.", false);
        assertEquals(1, found.size());
        assertEquals("cloud", found.get(0).replacement());
    }

    @Test
    void aRuleOfAnotherLanguageDoesNotApply() {
        assertTrue(RuleChecker.check(List.of(rule(Kind.REPLACE, "en nuage", MatchMode.TERM, "cloud", "fr")),
                "en", "en nuage", false).isEmpty());
        assertEquals(1, RuleChecker.check(List.of(rule(Kind.REPLACE, "en nuage", MatchMode.TERM, "cloud", "fr")),
                "fr-FR", "en nuage", false).size());
    }

    @Test
    void aMandatoryRuleIsCheckedOnADocumentOnly() {
        WritingRule mandatory = rule(Kind.MANDATORY, "Capital at risk", MatchMode.TERM, null, null);
        assertTrue(RuleChecker.check(List.of(mandatory), "en", "Invest now.", false).isEmpty());
        List<RuleViolation> found = RuleChecker.check(List.of(mandatory), "en", "Invest now.", true);
        assertEquals(1, found.size());
        assertNull(found.get(0).match());
        assertTrue(RuleChecker.check(List.of(mandatory), "en", "Invest now. Capital at risk.", true).isEmpty());
    }

    @Test
    void aCutDocumentNeverLacksAMandatoryTerm() {
        WritingRule mandatory = rule(Kind.MANDATORY, "Capital at risk", MatchMode.TERM, null, null);
        String longPage = "Invest now. ".repeat(RuleChecker.MAX_TEXT / 12 + 1) + "Capital at risk.";
        assertTrue(RuleChecker.check(List.of(mandatory), "en", longPage, true).isEmpty(),
                "The term may be in the part after the cut");
        WritingRule forbidden = rule(Kind.FORBIDDEN, "now", MatchMode.TERM, null, null);
        assertEquals(3, RuleChecker.check(List.of(forbidden), "en", longPage, true).size(),
                "The other rules still read the part before the cut");
    }

    @Test
    void aRegexRuleMatchesAStylePattern() {
        List<RuleViolation> found = RuleChecker.check(List.of(rule(Kind.STYLE, "\\s+,", MatchMode.REGEX, ",", null)),
                "en", "Fast , simple", false);
        assertEquals(1, found.size());
    }

    @Test
    void aRegexThatDoesNotCompileIsSkipped() {
        assertTrue(RuleChecker.check(List.of(rule(Kind.STYLE, "([", MatchMode.REGEX, null, null)), "en", "([", false).isEmpty());
    }

    @Test
    void aMatchInsideTheReplacementIsTheRightTerm() {
        WritingRule mail = rule(Kind.REPLACE, "mail", MatchMode.TERM, "e-mail", "fr");
        assertTrue(RuleChecker.check(List.of(mail), "fr", "Contactez-nous par e-mail", false).isEmpty());
        assertEquals(1, RuleChecker.check(List.of(mail), "fr", "Un e-mail ou un mail", false).size());
    }

    @Test
    void aTermThatEndsWithAStarIsAStem() {
        WritingRule seamless = rule(Kind.FORBIDDEN, "seamless*", MatchMode.TERM, null, null);
        assertEquals(2, RuleChecker.check(List.of(seamless), "en", "Seamless. It integrates seamlessly.", false).size());
        assertTrue(RuleChecker.check(List.of(rule(Kind.FORBIDDEN, "seamless", MatchMode.TERM, null, null)), "en",
                "It integrates seamlessly.", false).isEmpty());
    }

    @Test
    void aCatastrophicRegexStopsOnItsDeadline() {
        WritingRule slow = rule(Kind.STYLE, "(a|aa)+$", MatchMode.REGEX, null, null);
        String text = "a".repeat(5000) + "b";
        long start = System.nanoTime();
        assertTrue(RuleChecker.check(List.of(slow), "en", text, false).isEmpty());
        assertTrue(System.nanoTime() - start < 5_000_000_000L, "the deadline stops the regex");
    }
}
