/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlRulesTest {

    private static final String HEAD = "<head><title>Annual report</title>"
            + "<meta name='description' content='The results of the year.'></head>";

    private static List<String> rules(String html, Scope scope, String language) {
        return HtmlRules.check(Jsoup.parse(html), scope, language).stream()
                .map(finding -> finding.rule().id())
                .toList();
    }

    @Test
    void acceptsAnAccessiblePage() {
        String html = "<html lang='en'>" + HEAD + "<body><h1>Title</h1><h2>Part</h2><img src='a.png' alt='A chart'>"
                + "<a href='/x'>Annual report</a><label for='q'>Search</label><input id='q' type='text'>"
                + "<iframe title='Map' src='m'></iframe><button>Send</button></body></html>";
        assertEquals(List.of(), rules(html, Scope.PAGE, "en"));
    }

    @Test
    void findsEachDefect() {
        String html = "<html><body><h1>One</h1><h1>Two</h1><h4>Skipped</h4><img src='a.png'>"
                + "<a href='/x'></a><a href='/y'>click here</a><input type='text'><button></button>"
                + "<iframe src='m'></iframe><table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"
                + "<p id='dup'>x</p><p id='dup'>y</p></body></html>";
        List<String> found = rules(html, Scope.PAGE, "en");
        for (String rule : List.of("html.pageLanguageMissing", "html.headingH1Multiple", "html.headingLevelSkipped", "html.imageAltMissing", "html.linkNameMissing",
                "html.linkTextVague", "html.formLabelMissing", "html.buttonNameMissing", "html.frameTitleMissing",
                "html.tableHeaderMissing", "html.duplicateId")) {
            assertTrue(found.contains(rule), rule + " in " + found);
        }
    }

    @Test
    void acceptsADecorativeImageAndALinkNamedByItsImage() {
        String html = "<html lang='fr'>" + HEAD + "<body><h1>T</h1><img src='d.png' alt=''>"
                + "<a href='/x'><img src='l.png' alt='Logo'></a></body></html>";
        assertEquals(List.of(), rules(html, Scope.PAGE, "fr"));
    }

    @Test
    void findsALanguageThatDiffersFromThePage() {
        assertTrue(rules("<html lang='en'>" + HEAD + "<body><h1>T</h1></body></html>", Scope.PAGE, "fr")
                .contains("html.pageLanguageMismatch"));
    }

    @Test
    void leavesTheTitleAndTheDescriptionToTheirLengthRules() {
        // seo.title and seo.metaDescription measure them, and an empty one has the length 0.
        String html = "<html lang='en'><head><title> </title><meta name='description' content=''></head>"
                + "<body><h1>T</h1></body></html>";
        assertEquals(List.of(), rules(html, Scope.PAGE, "en"));
    }

    @Test
    void findsAVagueLinkTextWithItsDecorations() {
        String html = "<html lang='en'>" + HEAD + "<body><h1>T</h1><a href='/x'>Read more +</a>"
                + "<a href='/y'>Voir plus »</a><a href='/z'>Read the pricing guide</a></body></html>";
        assertEquals(List.of("html.linkTextVague", "html.linkTextVague"), rules(html, Scope.PAGE, "en"));
    }

    @Test
    void checksAnItemWithTheRulesOfOneItemOnly() {
        // An item has no language, title, description or main heading of its own, and a skipped
        // level inside it is still a defect.
        String item = "<div><h2>News</h2><h4>Detail</h4><img src='a.png'><p id='a'>x</p><p id='a'>y</p></div>";
        assertEquals(List.of("html.imageAltMissing", "html.headingLevelSkipped"), rules(item, Scope.ITEM, "en"));
    }

    @Test
    void namesTheScopeOfEachRule() {
        assertTrue(HtmlRule.IMAGE_ALT_MISSING.runsOn(Scope.ITEM));
        assertTrue(HtmlRule.HEADING_H1_MISSING.runsOn(Scope.PAGE));
        assertFalse(HtmlRule.HEADING_H1_MISSING.runsOn(Scope.ITEM));
        assertEquals("1.1.1", HtmlRule.IMAGE_ALT_MISSING.wcag());
    }

    @Test
    void listsAtMostTheMaximumNumberOfFindings() {
        String html = "<html lang='en'>" + HEAD + "<body><h1>T</h1>" + "<img src='a.png'>".repeat(80) + "</body></html>";
        assertEquals(HtmlRules.MAX_FINDINGS, rules(html, Scope.PAGE, "en").size());
    }
}
