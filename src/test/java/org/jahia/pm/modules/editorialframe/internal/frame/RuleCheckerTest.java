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

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCheckerTest {

    private static WritingRule rule(Kind kind, String pattern, MatchMode mode, String replacement, String language) {
        return new WritingRule("id-" + pattern, kind, pattern, mode, replacement, language, Severity.BLOCKING,
                "Because.", "site", List.of(), List.of(), null, null, null, null);
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

    private static WritingRule glossary(String term, List<String> termForms, String translation,
                                        List<String> translationForms) {
        return new WritingRule("glossary.access-token", Kind.REPLACE, term, MatchMode.TERM, translation, "fr",
                Severity.WARNING, null, "site", List.of(), List.of(), null, null, null, null, termForms,
                translationForms, "en", "/sites/digitall/contents/brand");
    }

    @Test
    void aTermMatchesInAnyOfItsFormsAndNamesItsFolder() {
        WritingRule rule = glossary("access token", List.of("access tokens"), "jeton d'accès", List.of());
        List<RuleViolation> found = RuleChecker.check(List.of(rule), "fr", "Créez des access tokens.", false);
        assertEquals(1, found.size());
        assertEquals("access tokens", found.get(0).match());
        assertEquals("jeton d'accès", found.get(0).replacement());
        assertEquals("glossary.access-token", found.get(0).ruleId());
        assertEquals("/sites/digitall/contents/brand", found.get(0).folder());
    }

    @Test
    void aTermIgnoresAccentsAndCase() {
        WritingRule gratuit = rule(Kind.FORBIDDEN, "gratuité", MatchMode.TERM, null, "fr");
        List<RuleViolation> found = RuleChecker.check(List.of(gratuit), "fr", "LIVRAISON GRATUITE.", false);
        assertEquals(1, found.size());
        assertEquals("GRATUITE", found.get(0).match());
    }

    @Test
    void aMatchInsideAnyFormOfTheReplacementIsTheRightTerm() {
        WritingRule rule = glossary("jeton d'authentification", List.of(), "jeton d'accès", List.of("jetons d'accès"));
        WritingRule role = rule(Kind.REPLACE, "role", MatchMode.TERM, "rôle", "fr");
        assertTrue(RuleChecker.check(List.of(role), "fr", "Un rôle clé.", false).isEmpty(),
                "The right term, with its accent, is not wrong");
        assertEquals(1, RuleChecker.check(List.of(role), "fr", "Un role clé.", false).size(),
                "The term without its accent is wrong");
        assertTrue(RuleChecker.check(List.of(rule), "fr", "Des jetons d’accès.", false).isEmpty());
    }

    @Test
    void aFinderLeavesOutMandatoryRulesAndOtherLanguagesAndGivesEachPlace() {
        RuleChecker.Finder finder = new RuleChecker.Finder(List.of(
                rule(Kind.FORBIDDEN, "free trial", MatchMode.TERM, null, null),
                rule(Kind.MANDATORY, "Capital at risk", MatchMode.TERM, null, null),
                rule(Kind.REPLACE, "en nuage", MatchMode.TERM, "cloud", "fr")), "en", 10);
        String text = "Start your Free Trial today, or a free trial later.";
        List<RuleChecker.Match> found = finder.find(text);
        assertEquals(2, found.size());
        assertEquals("Free Trial", text.substring(found.get(0).start(), found.get(0).end()));
        assertEquals("id-free trial", found.get(0).rule().id());
        assertEquals(34, found.get(1).start());
        assertTrue(new RuleChecker.Finder(List.of(rule(Kind.MANDATORY, "x", MatchMode.TERM, null, null)), "en", 1).isEmpty());
    }

    @Test
    void aRegexThatBacktracksOnAShortTextStopsOnItsDeadline() {
        WritingRule slow = rule(Kind.STYLE, "((a+)\\2?)+b", MatchMode.REGEX, null, null);
        String text = "x" + "a".repeat(30) + "!";
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> RuleChecker.check(List.of(slow), "en", text, false));
    }

    @Test
    void aFinderReportsEveryMatchUpToItsLimit() {
        RuleChecker.Finder finder = new RuleChecker.Finder(List.of(rule(Kind.FORBIDDEN, "free", MatchMode.TERM, null, null)), "en", 1000);
        assertEquals(150, finder.find("free ".repeat(150)).size());
    }

    @Test
    void aRegexMatchesInAnyCaseUnlessItAsksForTheCase() {
        WritingRule archaic = rule(Kind.FORBIDDEN, "\\b(thee|thou|thy|prithee|forsooth|verily|ye olde)\\b", MatchMode.REGEX, null, "en");
        assertEquals("Verily", RuleChecker.check(List.of(archaic), "en", "Verily, the road goes ever on.", false).get(0).match());
        assertEquals("Thou", RuleChecker.check(List.of(archaic), "en", "Thou art a fool of a Took.", false).get(0).match());
        WritingRule exact = rule(Kind.FORBIDDEN, "(?-i)\\bJAHIA\\b", MatchMode.REGEX, null, "en");
        assertTrue(RuleChecker.check(List.of(exact), "en", "Built on Jahia.", false).isEmpty(), "(?-i) keeps the case");
        assertEquals(1, RuleChecker.check(List.of(exact), "en", "Built on JAHIA.", false).size());
    }

    @Test
    void aRegexIgnoresTheCaseOfAnAccentedCapitalAndOfAMandatoryMention() {
        WritingRule summer = rule(Kind.FORBIDDEN, "\\bété\\b", MatchMode.REGEX, null, "fr");
        assertEquals("ÉTÉ", RuleChecker.check(List.of(summer), "fr", "Les soldes d'ÉTÉ.", false).get(0).match());
        WritingRule risk = rule(Kind.MANDATORY, "capital at risk", MatchMode.REGEX, null, "en");
        assertTrue(RuleChecker.check(List.of(risk), "en", "Invest now. CAPITAL AT RISK.", true).isEmpty(),
                "The mandatory mention is found in another case");
    }

    @Test
    void aTermInsideALongerMatchOfAnotherTermReportsNothingThere() {
        WritingRule baggins = rule(Kind.REPLACE, "Baggins", MatchMode.TERM, "Sacquet", "fr");
        WritingRule sackville = rule(Kind.REPLACE, "Sackville-Baggins", MatchMode.TERM, "Sacquet-de-Besace", "fr");
        List<RuleViolation> found = RuleChecker.check(List.of(baggins, sackville), "fr",
                "Lobelia Sackville-Baggins convoitait Cul-de-Sac.", false);
        assertEquals(1, found.size());
        assertEquals("Sacquet-de-Besace", found.get(0).replacement());

        List<RuleViolation> both = RuleChecker.check(List.of(baggins, sackville), "fr",
                "Bilbo Baggins et Lobelia Sackville-Baggins.", false);
        assertEquals(List.of("Baggins", "Sackville-Baggins"), both.stream().map(RuleViolation::match).toList());
    }

    @Test
    void aFinderDropsATermInsideALongerTermToo() {
        RuleChecker.Finder finder = new RuleChecker.Finder(List.of(
                rule(Kind.REPLACE, "Baggins", MatchMode.TERM, "Sacquet", "fr"),
                rule(Kind.REPLACE, "Sackville-Baggins", MatchMode.TERM, "Sacquet-de-Besace", "fr")), "fr", 10);
        List<RuleChecker.Match> found = finder.find("Lobelia Sackville-Baggins.");
        assertEquals(1, found.size());
        assertEquals("id-Sackville-Baggins", found.get(0).rule().id());
    }

    @Test
    void twoRulesWithTheSameTermEachReport() {
        WritingRule legal = new WritingRule("legal.free", Kind.FORBIDDEN, "free", MatchMode.TERM, null, null,
                Severity.WARNING, null, "site", List.of(), List.of(), null, null, null, null);
        WritingRule brand = new WritingRule("brand.free", Kind.FORBIDDEN, "free", MatchMode.TERM, null, null,
                Severity.WARNING, null, "site", List.of(), List.of(), null, null, null, null);
        assertEquals(2, RuleChecker.check(List.of(legal, brand), "en", "A free offer.", false).size());
    }

    @Test
    void aTermStillReportsAfterManyMatchesInsideALongerTerm() {
        WritingRule baggins = rule(Kind.REPLACE, "Baggins", MatchMode.TERM, "Sacquet", "fr");
        WritingRule sackville = rule(Kind.REPLACE, "Sackville-Baggins", MatchMode.TERM, "Sacquet-de-Besace", "fr");
        String text = "Lobelia Sackville-Baggins. ".repeat(120) + "Bilbo Baggins.";
        List<RuleViolation> found = RuleChecker.check(List.of(baggins, sackville), "fr", text, false);
        assertEquals(List.of("Sacquet", "Sacquet-de-Besace", "Sacquet-de-Besace", "Sacquet-de-Besace"),
                found.stream().map(RuleViolation::replacement).toList());
    }

    @Test
    void theViolationsComeInTheOrderOfTheRules() {
        WritingRule free = rule(Kind.FORBIDDEN, "free", MatchMode.TERM, null, "en");
        WritingRule risk = rule(Kind.MANDATORY, "capital at risk", MatchMode.TERM, null, "en");
        WritingRule seamless = rule(Kind.REPLACE, "seamless", MatchMode.TERM, "smooth", "en");
        List<RuleViolation> found = RuleChecker.check(List.of(free, risk, seamless), "en",
                "A free and seamless offer.", true);
        assertEquals(List.of("free", "capital at risk", "seamless"), found.stream().map(RuleViolation::pattern).toList());
    }

    private static WritingRule name(String id, String term, String form, String language) {
        return new WritingRule(id, Kind.REPLACE, term, MatchMode.TERM, form, language, Severity.WARNING, null, "site",
                List.of(), List.of(), null, null, null, null, List.of(), List.of(), "en", "/sites/tolkien/contents/names");
    }

    @Test
    void aFormOfAnotherLanguageOfAGlossaryTermGetsTheFormOfTheLanguageOfTheText() {
        List<WritingRule> names = List.of(name("glossary.frodo-fr", "Frodo", "Frodon", "fr"),
                name("glossary.frodo-es", "Frodo", "Frodo", "es"), name("glossary.baggins-es", "Baggins", "Bolsón", "es"));

        List<RuleViolation> spanish = RuleChecker.check(names, "es", "Frodon Bolsón abandona la Comarca.", false);
        assertEquals(1, spanish.size());
        assertEquals("Frodon", spanish.get(0).match());
        assertEquals("Frodo", spanish.get(0).replacement());
        assertEquals("glossary.frodo-fr", spanish.get(0).ruleId());
        assertEquals("/sites/tolkien/contents/names", spanish.get(0).folder());

        List<RuleViolation> english = RuleChecker.check(names, "en", "Frodon leaves the Shire.", false);
        assertEquals(List.of("Frodo"), english.stream().map(RuleViolation::replacement).toList());

        List<RuleViolation> french = RuleChecker.check(names, "fr", "Frodo quitte la Comté.", false);
        assertEquals(1, french.size(), "The French rule reports the English form once");
        assertTrue(RuleChecker.check(names, "de", "Frodon verlässt das Auenland.", false).isEmpty(),
                "A language with no form of the term gets nothing");
    }

    @Test
    void aFinderReportsAFormOfAnotherLanguageToo() {
        RuleChecker.Finder finder = new RuleChecker.Finder(List.of(name("glossary.frodo-fr", "Frodo", "Frodon", "fr"),
                name("glossary.frodo-es", "Frodo", "Frodo", "es")), "es", 10);
        assertEquals(1, finder.find("Frodon abandona la Comarca.").size());
    }

    @Test
    void aTextOfARegionalLanguageGetsTheFormOfItsLanguage() {
        List<WritingRule> names = List.of(name("glossary.frodo-fr", "Frodo", "Frodon", "fr"),
                name("glossary.frodo-es", "Frodo", "Frodo", "es"));

        List<RuleViolation> found = RuleChecker.check(names, "es_ES", "Frodon abandona la Comarca.", false);
        assertEquals(1, found.size());
        assertEquals("Frodo", found.get(0).replacement());
    }

    @Test
    void aFormThatTwoOtherLanguagesShareIsReportedOnce() {
        List<WritingRule> names = List.of(name("glossary.frodo-fr", "Frodo", "Frodon", "fr"),
                name("glossary.frodo-it", "Frodo", "Frodon", "it"), name("glossary.frodo-es", "Frodo", "Frodo", "es"));

        List<RuleViolation> found = RuleChecker.check(names, "es", "Frodon abandona la Comarca.", false);
        assertEquals(1, found.size());
        assertEquals("glossary.frodo-fr", found.get(0).ruleId());
    }
}
