package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.rules.ContentRules;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterisation tests for the scoring engine.
 *
 * These exist to freeze what {@link GeoScore#compute} does TODAY, not to argue
 * that it is right. compute is about to be decomposed - Sonar scores it at a
 * cognitive complexity of 43 and calls it a Brain Method with 34 variables - and
 * both the drawer and the site-wide scan route through it. Without something
 * pinning the output, a refactor could change every site's score and the whole
 * suite would still pass: the Cypress tests assert that a run HAPPENED and that
 * the UI shows "{n} of {m} checks passed", never which n or which m.
 *
 * So each test below states an input and the exact verdict it produces. If one
 * fails after a change to compute, the change altered a score. That is the
 * entire point; decide deliberately whether the new answer is the wanted one and
 * update the test with a reason, rather than adjusting it to go green.
 */
class GeoScoreTest {

    private static final ContentRules RULES = ContentRules.base();

    /** The eighteen check ids, in the order compute emits them. */
    private static final String[] ALL_CHECKS = {
        "reachable", "policyMatchesReality", "noClientRedirect", "namedInRobots",
        "contentInInitialHtml", "sameContentForCrawlers", "title", "singleH1",
        "headingOutline", "metaDescription", "canonical", "langDeclared",
        "structuredData", "freshness", "imageAlt", "robotsPresent", "llmsPresent"
    };

    // ---------------------------------------------------------------- helpers

    /** A report with nothing in it: every optional read falls back. */
    private static JSONObject emptyReport() {
        return new JSONObject();
    }

    /** One agent that answered 200 with the given html block - compute's "control". */
    private static JSONObject withControl(JSONObject html) {
        JSONObject agent = new JSONObject();
        agent.put("status", 200);
        agent.put("html", html);
        JSONObject report = new JSONObject();
        report.put("agents", new JSONArray().put(agent));
        return report;
    }

    /** A control with the given html block, and the rules that the page checks found broken on it. */
    private static JSONObject checked(JSONObject html, String... broken) {
        JSONObject report = withControl(html);
        JSONArray found = new JSONArray();
        for (String ruleId : broken) {
            found.put(ruleId);
        }
        report.put(PageFindings.KEY, found);
        return report;
    }

    private static JSONObject check(JSONObject score, String id) {
        JSONArray checks = score.getJSONArray("checks");
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.getJSONObject(i);
            if (id.equals(c.getString("id"))) {
                return c;
            }
        }
        throw new AssertionError("no check with id " + id);
    }

    private static boolean passed(JSONObject score, String id) {
        return check(score, id).getBoolean("passed");
    }

    // ------------------------------------------------------------ the shape

    @Nested
    @DisplayName("the shape of the result")
    class Shape {

        @Test
        @DisplayName("an empty report still produces all seventeen unconditional checks")
        void emptyReport_emitsSeventeenChecks() {
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(score.getInt("total")).isEqualTo(17);
            assertThat(score.getJSONArray("checks").length()).isEqualTo(17);
        }

        @Test
        @DisplayName("checks come out in a fixed order, which the UI relies on")
        void checkOrder_isStable() {
            JSONArray checks = GeoScore.compute(emptyReport(), RULES).getJSONArray("checks");

            String[] ids = new String[checks.length()];
            for (int i = 0; i < checks.length(); i++) {
                ids[i] = checks.getJSONObject(i).getString("id");
            }
            assertThat(ids).containsExactly(ALL_CHECKS);
        }

        @Test
        @DisplayName("every check carries id, rule, group, severity, passed and value, and the limits of its rule")
        void everyCheck_hasTheFullShape() {
            JSONArray checks = GeoScore.compute(emptyReport(), RULES).getJSONArray("checks");

            for (int i = 0; i < checks.length(); i++) {
                JSONObject c = checks.getJSONObject(i);
                assertThat(c.keySet())
                        .as("check %s", c.optString("id"))
                        .contains("id", "rule", "group", "severity", "passed", "value")
                        .isSubsetOf("id", "rule", "group", "severity", "passed", "value", "min", "max");
                assertThat(c.getString("group")).isIn("access", "content", "files");
                assertThat(c.getString("severity")).isIn("critical", "important", "advisory");
            }
        }

        @ParameterizedTest(name = "{0} is {1}/{2}")
        @CsvSource({
            "reachable,             access,  critical",
            "policyMatchesReality,  access,  important",
            "noClientRedirect,      access,  critical",
            "namedInRobots,         access,  advisory",
            "contentInInitialHtml,  content, critical",
            "sameContentForCrawlers,content, critical",
            "title,                 content, important",
            "singleH1,              content, important",
            "headingOutline,        content, advisory",
            "metaDescription,       content, important",
            "canonical,             content, important",
            "langDeclared,          content, important",
            "structuredData,        content, important",
            "freshness,             content, advisory",
            "imageAlt,              content, critical",
            "robotsPresent,         files,   important",
            "llmsPresent,           files,   advisory"
        })
        @DisplayName("each check keeps its group and severity")
        void groupAndSeverity_arePinned(String id, String group, String severity) {
            JSONObject c = check(GeoScore.compute(emptyReport(), RULES), id);

            assertThat(c.getString("group")).isEqualTo(group);
            assertThat(c.getString("severity")).isEqualTo(severity);
        }

        @Test
        @DisplayName("a value the report cannot supply is JSON null, never absent")
        void missingValue_isJsonNull() {
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(check(score, "metaDescription").isNull("value")).isTrue();
            assertThat(check(score, "imageAlt").isNull("value")).isTrue();
        }
    }

    // ------------------------------------------------- the conditional check

    @Nested
    @DisplayName("guestReadable, the one conditional check")
    class GuestReadable {

        @Test
        @DisplayName("is absent when the repository did not answer, so the total stays seventeen")
        void absentVisibility_omitsTheCheck() {
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(score.getInt("total")).isEqualTo(17);
            assertThat(score.getJSONArray("checks").toString()).doesNotContain("guestReadable");
        }

        @Test
        @DisplayName("appears only when visibility actually carries the key, taking the total to eighteen")
        void presentVisibility_addsTheCheck() {
            JSONObject report = emptyReport();
            report.put("visibility", new JSONObject().put("guestReadable", true));

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(score.getInt("total")).isEqualTo(18);
            assertThat(passed(score, "guestReadable")).isTrue();
        }

        @Test
        @DisplayName("a visibility object WITHOUT the key still omits the check")
        void visibilityWithoutTheKey_omitsTheCheck() {
            // has() not optBoolean(): "the repository could not say" must not read
            // as a failure, which a default would have made it.
            JSONObject report = emptyReport();
            report.put("visibility", new JSONObject().put("kind", "unknown"));

            assertThat(GeoScore.compute(report, RULES).getInt("total")).isEqualTo(17);
        }

        @Test
        @DisplayName("a gated page fails critically and reports the kind as the value")
        void gatedPage_failsCriticallyWithKind() {
            JSONObject report = emptyReport();
            report.put("visibility", new JSONObject()
                    .put("guestReadable", false)
                    .put("kind", "acl"));

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "guestReadable")).isFalse();
            assertThat(check(score, "guestReadable").getString("value")).isEqualTo("acl");
        }
    }

    // --------------------------------------------------------- access checks

    @Nested
    @DisplayName("access")
    class Access {

        @Test
        @DisplayName("reachable passes only when nothing was blocked, and reports the count")
        void reachable_countsBlocked() {
            assertThat(passed(GeoScore.compute(emptyReport(), RULES), "reachable")).isTrue();

            JSONObject report = emptyReport();
            report.put("blockedCount", 3);
            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "reachable")).isFalse();
            assertThat(check(score, "reachable").getInt("value")).isEqualTo(3);
        }

        @Test
        @DisplayName("a fetch with no answer is not a refusal: fetched fails, reachable is not judged")
        void unreachable_isNotBlocked() {
            JSONObject report = emptyReport();
            report.put("unreachableCount", 16);
            report.put("unreachableError", "ConnectException");
            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "fetched")).isFalse();
            assertThat(check(score, "fetched").getString("value")).isEqualTo("ConnectException");
            assertThat(score.getJSONArray("checks").toString()).doesNotContain("\"reachable\"");

            // A real refusal next to the failures is still reported as one.
            report.put("blockedCount", 2);
            score = GeoScore.compute(report, RULES);
            assertThat(passed(score, "reachable")).isFalse();
            assertThat(check(score, "reachable").getInt("value")).isEqualTo(2);
        }

        @Test
        @DisplayName("policyMatchesReality sums BOTH kinds of contradiction")
        void policyMatchesReality_sumsBothMismatchKinds() {
            JSONObject report = emptyReport();
            report.put("blockedButAllowedCount", 2);
            report.put("reachableButDisallowedCount", 1);

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "policyMatchesReality")).isFalse();
            assertThat(check(score, "policyMatchesReality").getInt("value")).isEqualTo(3);
        }

        @Test
        @DisplayName("being disallowed in robots.txt is deliberately NOT a failure on its own")
        void disallowedButConsistent_isNotAFailure() {
            // The class comment states this as a rule: refusing a crawler is a
            // legitimate decision. Only a DISAGREEMENT between policy and reality
            // is reported, because then one of the two is wrong.
            JSONObject report = emptyReport();
            report.put("blockedCount", 0);
            report.put("blockedButAllowedCount", 0);
            report.put("reachableButDisallowedCount", 0);

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "policyMatchesReality")).isTrue();
            assertThat(passed(score, "reachable")).isTrue();
        }

        @Test
        @DisplayName("a meta refresh fails critically and reports the target")
        void metaRefresh_failsCritically() {
            JSONObject score = GeoScore.compute(withControl(
                    new JSONObject().put("metaRefresh", "0;url=/elsewhere")), RULES);

            assertThat(passed(score, "noClientRedirect")).isFalse();
            assertThat(check(score, "noClientRedirect").getString("value")).isEqualTo("0;url=/elsewhere");
        }

        @Test
        @DisplayName("namedInRobots is advisory and passes on any non-zero count")
        void namedInRobots_passesOnAnyCount() {
            JSONObject report = emptyReport();
            report.put("siteFiles", new JSONObject()
                    .put("robots", new JSONObject().put("namedAiBotCount", 1)));

            assertThat(passed(GeoScore.compute(report, RULES), "namedInRobots")).isTrue();
        }
    }

    // -------------------------------------------------------- content checks

    @Nested
    @DisplayName("content")
    class Content {

        @Test
        @DisplayName("the control is the FIRST agent that answered 200 with html")
        void control_isFirst200WithHtml() {
            JSONArray agents = new JSONArray();
            agents.put(new JSONObject().put("status", 403));
            // 200 but no html block: skipped, not taken as the control.
            agents.put(new JSONObject().put("status", 200));
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("h1Count", 1)));
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("h1Count", 9)));
            JSONObject report = new JSONObject();
            report.put("agents", agents);

            assertThat(check(GeoScore.compute(report, RULES), "singleH1").getInt("value")).isEqualTo(1);
        }

        @ParameterizedTest(name = "{0} words -> passed={1}")
        @CsvSource({"0, false", "49, false", "50, true", "51, true"})
        @DisplayName("contentInInitialHtml turns at fifty words")
        void contentInInitialHtml_thresholdIsFifty(int words, boolean expected) {
            JSONObject report = emptyReport();
            report.put("controlWords", words);

            assertThat(passed(GeoScore.compute(report, RULES), "contentInInitialHtml")).isEqualTo(expected);
        }

        @Test
        @DisplayName("title passes unless the page checks found seo.title broken, and reports the length")
        void title_followsThePageChecks() {
            JSONObject html = new JSONObject().put("title", "x".repeat(35));

            assertThat(passed(GeoScore.compute(checked(html), RULES), "title")).isTrue();
            assertThat(passed(GeoScore.compute(checked(html, "seo.title"), RULES), "title")).isFalse();
            assertThat(check(GeoScore.compute(checked(html), RULES), "title").getInt("value")).isEqualTo(35);
        }

        @Test
        @DisplayName("an absent title fails and reports length zero")
        void absentTitle_failsWithZero() {
            JSONObject score = GeoScore.compute(checked(new JSONObject(), "seo.title"), RULES);

            assertThat(passed(score, "title")).isFalse();
            assertThat(check(score, "title").getInt("value")).isZero();
        }

        @Test
        @DisplayName("without the findings of the page checks, every check of the HTML fails")
        void noPageFindings_failsTheChecksOfTheHtml() {
            JSONObject score = GeoScore.compute(withControl(new JSONObject()
                    .put("title", "x".repeat(35)).put("h1Count", 1).put("lang", "en").put("canonical", true)), RULES);

            for (String id : new String[]{"title", "singleH1", "metaDescription", "canonical", "langDeclared",
                    "structuredData", "imageAlt"}) {
                assertThat(passed(score, id)).as(id).isFalse();
            }
        }

        @ParameterizedTest(name = "{0} h1, {1} broken -> passed={2}, rule {3}")
        @CsvSource({
            "1, '',                     true,  html.headingH1Missing",
            "0, html.headingH1Missing,  false, html.headingH1Missing",
            "2, html.headingH1Multiple, false, html.headingH1Multiple"
        })
        @DisplayName("singleH1 fails on either h1 rule, and names the rule of the count it reports")
        void singleH1_followsBothRules(int count, String broken, boolean expected, String rule) {
            JSONObject html = new JSONObject().put("h1Count", count);
            JSONObject score = GeoScore.compute(broken.isEmpty() ? checked(html) : checked(html, broken), RULES);

            assertThat(passed(score, "singleH1")).isEqualTo(expected);
            assertThat(check(score, "singleH1").getString("rule")).isEqualTo(rule);
        }

        @Test
        @DisplayName("a crawler under half the control's word count counts as thin")
        void sameContentForCrawlers_halfIsTheThreshold() {
            JSONArray agents = new JSONArray();
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("words", 100)));
            // exactly half: NOT thin, the comparison is strictly less than
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("words", 50)));
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("words", 49)));
            JSONObject report = new JSONObject();
            report.put("agents", agents);
            report.put("controlWords", 100);

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "sameContentForCrawlers")).isFalse();
            assertThat(check(score, "sameContentForCrawlers").getInt("value")).isEqualTo(1);
        }

        @Test
        @DisplayName("with no control words the thin check cannot run, and passes")
        void sameContentForCrawlers_noControlWords_passes() {
            // SUSPECT: a page that returned nothing at all scores a PASS here
            // rather than being excluded. contentInInitialHtml catches that case
            // critically, so the total is not misleading, but this check reads as
            // "every crawler got the same content" when the truth is "there was
            // nothing to compare". Pinned as-is.
            JSONArray agents = new JSONArray();
            agents.put(new JSONObject().put("status", 200)
                    .put("html", new JSONObject().put("words", 0)));
            JSONObject report = new JSONObject();
            report.put("agents", agents);
            report.put("controlWords", 0);

            assertThat(passed(GeoScore.compute(report, RULES), "sameContentForCrawlers")).isTrue();
        }

        @Test
        @DisplayName("imageAlt passes unless the page checks found an image without alt")
        void imageAlt_followsThePageChecks() {
            JSONObject html = new JSONObject().put("images", 10).put("imagesWithAlt", 9);

            assertThat(passed(GeoScore.compute(checked(html), RULES), "imageAlt")).isTrue();
            assertThat(passed(GeoScore.compute(checked(html, "html.imageAltMissing"), RULES), "imageAlt")).isFalse();
        }

        @Test
        @DisplayName("imageAlt reports the images with an alt attribute out of all, or null when there is nothing to describe")
        void imageAlt_valueIsTheRatio() {
            JSONObject some = GeoScore.compute(withControl(new JSONObject()
                    .put("images", 4).put("imagesWithAlt", 2).put("imagesWithoutAlt", 1)), RULES);
            assertThat(check(some, "imageAlt").getString("value")).isEqualTo("3/4");

            JSONObject none = GeoScore.compute(withControl(new JSONObject().put("images", 0)), RULES);
            assertThat(check(none, "imageAlt").isNull("value")).isTrue();
        }

        @Test
        @DisplayName("structuredData joins the JSON-LD types with a comma")
        void structuredData_joinsTypes() {
            JSONObject score = GeoScore.compute(checked(new JSONObject()
                    .put("jsonLdTypes", new JSONArray().put("WebPage").put("Article"))), RULES);

            assertThat(passed(score, "structuredData")).isTrue();
            assertThat(check(score, "structuredData").getString("value")).isEqualTo("WebPage, Article");
        }

        @Test
        @DisplayName("structuredData fails when the page checks found no structured data")
        void structuredData_emptyArrayFails() {
            JSONObject score = GeoScore.compute(checked(new JSONObject()
                    .put("jsonLdTypes", new JSONArray()), "seo.structuredData"), RULES);

            assertThat(passed(score, "structuredData")).isFalse();
        }

        @Test
        @DisplayName("langDeclared reports the language it found")
        void langDeclared_reportsTheValue() {
            JSONObject score = GeoScore.compute(checked(new JSONObject().put("lang", "fr")), RULES);

            assertThat(passed(score, "langDeclared")).isTrue();
            assertThat(check(score, "langDeclared").getString("value")).isEqualTo("fr");
        }
    }

    // ------------------------------------------------------------ site files

    @Nested
    @DisplayName("site files")
    class Files {

        @Test
        @DisplayName("both fail when siteFiles is absent entirely")
        void absentSiteFiles_bothFail() {
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(passed(score, "robotsPresent")).isFalse();
            assertThat(passed(score, "llmsPresent")).isFalse();
        }

        @Test
        @DisplayName("each passes only on its own present flag")
        void presentFlags_areIndependent() {
            JSONObject report = emptyReport();
            report.put("siteFiles", new JSONObject()
                    .put("robots", new JSONObject().put("present", true))
                    .put("llms", new JSONObject().put("present", false)));

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(passed(score, "robotsPresent")).isTrue();
            assertThat(passed(score, "llmsPresent")).isFalse();
        }
    }

    // ---------------------------------------------------------------- tally

    @Nested
    @DisplayName("the tally")
    class Tally {

        @Test
        @DisplayName("an empty report scores four of seventeen with TWO critical failures")
        void emptyReport_scoresFourOfSeventeen() {
            // The exact baseline. If a refactor moves any of these four numbers it
            // has changed what every unscored page reports.
            //
            // SUSPECT, and the reason this test is worth having. A report about a
            // page nothing is known about - never fetched, no html, no site files -
            // reports only ONE critical failure, because three critical checks pass
            // on the ABSENCE of evidence rather than on evidence:
            //
            //   reachable              blockedCount defaults to 0, so "nothing was
            //                          blocked" and "nothing was tried" are equal
            //   noClientRedirect       no control html means no metaRefresh found
            //   sameContentForCrawlers controlWords is 0, so the thin-content loop
            //                          never runs and thin stays 0
            //
            // contentInInitialHtml fails, and imageAlt too: with no HTML the page
            // checks did not run, so none of the checks of the HTML passes. A page
            // that could not be fetched at all still scores better than a page that
            // was fetched and found wanting. Pinned as today's behaviour, not endorsed.
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(score.getInt("total")).isEqualTo(17);
            assertThat(score.getInt("passed")).isEqualTo(4);
            assertThat(score.getInt("criticalFailed")).isEqualTo(2);
            assertThat(score.getInt("importantFailed")).isEqualTo(7);
        }

        @Test
        @DisplayName("the three checks that pass on absence of evidence, named")
        void absenceOfEvidence_passesThreeCriticalChecks() {
            // Pinned individually so a refactor that changes any ONE of them is
            // identified, rather than only showing up as a moved total above.
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            assertThat(passed(score, "reachable")).isTrue();
            assertThat(passed(score, "noClientRedirect")).isTrue();
            assertThat(passed(score, "sameContentForCrawlers")).isTrue();
            assertThat(passed(score, "contentInInitialHtml")).isFalse();
        }

        @Test
        @DisplayName("passed plus the two failure counts plus advisory failures equals the total")
        void counts_accountForEveryCheck() {
            JSONObject score = GeoScore.compute(emptyReport(), RULES);

            int advisoryFailed = 0;
            JSONArray checks = score.getJSONArray("checks");
            for (int i = 0; i < checks.length(); i++) {
                JSONObject c = checks.getJSONObject(i);
                if (!c.getBoolean("passed") && "advisory".equals(c.getString("severity"))) {
                    advisoryFailed++;
                }
            }

            assertThat(score.getInt("passed")
                    + score.getInt("criticalFailed")
                    + score.getInt("importantFailed")
                    + advisoryFailed)
                    .isEqualTo(score.getInt("total"));
        }

        @Test
        @DisplayName("a fully healthy page passes everything")
        void healthyPage_passesAll() {
            JSONObject html = new JSONObject()
                    .put("title", "A title comfortably inside the bounds")
                    .put("h1Count", 1)
                    .put("h2Count", 3)
                    .put("words", 500)
                    .put("metaDescription", true)
                    .put("canonical", true)
                    .put("lang", "en")
                    .put("dateModified", "2026-01-01")
                    .put("images", 2)
                    .put("imagesWithAlt", 2)
                    .put("jsonLdTypes", new JSONArray().put("WebPage"));

            JSONObject report = checked(html);
            report.put("controlWords", 500);
            report.put("blockedCount", 0);
            report.put("siteFiles", new JSONObject()
                    .put("robots", new JSONObject().put("present", true).put("namedAiBotCount", 4))
                    .put("llms", new JSONObject().put("present", true)));
            report.put("visibility", new JSONObject().put("guestReadable", true));

            JSONObject score = GeoScore.compute(report, RULES);

            assertThat(score.getInt("total")).isEqualTo(18);
            assertThat(score.getInt("passed")).isEqualTo(18);
            assertThat(score.getInt("criticalFailed")).isZero();
            assertThat(score.getInt("importantFailed")).isZero();
        }
    }

    @Nested
    @DisplayName("the content rules of the site")
    class SiteRules {

        private JSONObject titled(int length, ContentRules rules) {
            return GeoScore.compute(checked(new JSONObject().put("title", "x".repeat(length)), "seo.title"), rules);
        }

        @Test
        @DisplayName("every check carries the id of its rule")
        void everyCheck_carriesItsRule() {
            JSONArray checks = GeoScore.compute(withControl(new JSONObject()), RULES).getJSONArray("checks");

            for (int i = 0; i < checks.length(); i++) {
                String rule = checks.getJSONObject(i).getString("rule");
                assertThat(RULES.get(rule).id()).isEqualTo(rule);
            }
            assertThat(check(GeoScore.compute(withControl(new JSONObject()), RULES), "title").getString("rule"))
                    .isEqualTo("seo.title");
        }

        @Test
        @DisplayName("a check states the range of the rule of the site")
        void changedTitleRange_isStated() {
            ContentRules site = RULES.changed("seo.title", null, null, 40.0, null);

            assertThat(check(titled(35, site), "title").getInt("min")).isEqualTo(40);
            assertThat(check(titled(35, site), "title").getInt("max")).isEqualTo(60);
        }

        @Test
        @DisplayName("a rule the site turns off removes its check from the score")
        void disabledRule_removesTheCheck() {
            ContentRules site = RULES.changed("seo.title", false, null, null, null);
            JSONObject score = titled(5, site);

            JSONArray checks = score.getJSONArray("checks");
            for (int i = 0; i < checks.length(); i++) {
                assertThat(checks.getJSONObject(i).getString("id")).isNotEqualTo("title");
            }
            assertThat(score.getInt("total")).isEqualTo(titled(5, RULES).getInt("total") - 1);
        }

        @Test
        @DisplayName("the severity of the rule decides the severity of the check")
        void severity_comesFromTheRule() {
            ContentRules site = RULES.changed("seo.title", null, "critical", null, null);

            assertThat(check(titled(5, RULES), "title").getString("severity")).isEqualTo("important");
            assertThat(check(titled(5, site), "title").getString("severity")).isEqualTo("critical");
        }
    }
}
