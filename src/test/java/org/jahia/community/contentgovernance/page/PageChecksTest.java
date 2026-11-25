package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.ThresholdCheck;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PageChecksTest {

    private static final String PATH = "/sites/digitall/home";
    private static final String GOOD_HEAD = "<head><title>Annual report of the Digitall company 2026</title>"
            + "<meta name='description' content='The results, the people and the plans of Digitall for the next year.'>"
            + "<link rel='canonical' href='/home'><meta property='og:title' content='Report'>"
            + "<meta property='og:image' content='/a.png'><script type='application/ld+json'>{\"@type\":\"Report\"}</script>"
            + "</head>";

    /** Every check of the server, as the service registers them. */
    private static CheckRegistry registry() {
        CheckRegistry registry = new CheckRegistry();
        bind(registry, SeoChecks.IDS, SeoChecks::read);
        bind(registry, ReadabilityChecks.IDS, ReadabilityChecks::read);
        bind(registry, LinkChecks.IDS, LinkChecks::read);
        bind(registry, HealthChecks.IDS, HealthChecks::read);
        Registries.bind(registry, new ThresholdCheck(), ContentRule.THRESHOLD);
        return registry;
    }

    /** Every check of the server on the HTML, with the shipped rules, as the service runs them. */
    private static List<Finding> run(CheckInput input) {
        return Registries.run(registry(), ContentRules.base(), input);
    }

    /** The result of each shipped rule that ran on the HTML, by rule id. */
    private static Map<String, CheckResult> results(CheckInput input) {
        Map<String, CheckResult> out = new LinkedHashMap<>();
        registry().results(ContentRules.base(), input).forEach(result -> out.put(result.ruleId(), result));
        return out;
    }

    private static void bind(CheckRegistry registry, List<String> ids, Function<CheckInput, Family.Found> read) {
        if (ids.isEmpty()) {
            return;
        }
        Family.checks(ids, "test:" + ids.get(0), read).forEach((id, check) -> Registries.bind(registry, check, id));
    }

    private static CheckInput page(String html) {
        return HtmlCheck.input(PATH, "en", Jsoup.parse(html), Scope.PAGE);
    }

    private static List<String> rules(List<Finding> findings) {
        return findings.stream().map(Finding::ruleId).toList();
    }

    @Nested
    class SeoRules {

        @Test
        @DisplayName("a complete head gives no SEO finding")
        void aCompleteHead_givesNone() {
            assertThat(rules(run(page("<html lang='en'>" + GOOD_HEAD + "<body><h1>Report</h1></body></html>"))))
                    .noneMatch(rule -> rule.startsWith("seo."));
        }

        @Test
        @DisplayName("a bare page breaks each SEO rule, and a missing title has the length 0")
        void aBarePage_breaksEachRule() {
            List<Finding> found = run(page("<html><head><meta name='robots' content='NOINDEX'>"
                    + "<script type='application/ld+json'>{oops</script></head><body></body></html>"));

            assertThat(rules(found)).contains("seo.title", "seo.metaDescription", "seo.indexable", "seo.canonical",
                    "seo.openGraph", "seo.structuredDataValid").doesNotContain("seo.followLinks", "seo.structuredData");
            assertThat(found).filteredOn(finding -> finding.ruleId().equals("seo.title"))
                    .extracting(Finding::text).containsExactly("0");
            assertThat(found).filteredOn(finding -> finding.ruleId().equals("seo.openGraph"))
                    .extracting(Finding::text).containsExactly("og:title og:image");
        }

        @Test
        @DisplayName("the label of the preview is not part of the title")
        void thePreviewLabel_isNotCounted() {
            assertThat(SeoChecks.title(Jsoup.parse("<title>Aperçu - Home</title>"))).isEqualTo("Home");
        }

        @Test
        @DisplayName("a content item gets no rule of a whole page")
        void anItem_getsNoPageRule() {
            CheckInput item = HtmlCheck.input(PATH + "/main/intro", "en", Jsoup.parse("<p>Intro</p>"), Scope.ITEM);
            assertThat(rules(run(item))).noneMatch(rule -> rule.startsWith("seo."));
        }
    }

    @Nested
    class ReadabilityRules {

        private static String sentences(String word, int count, int words) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < count; i++) {
                out.append(String.join(" ", Collections.nCopies(words, word))).append(". ");
            }
            return out.toString().trim();
        }

        @Test
        @DisplayName("each language uses its formula, as the drawer does")
        void eachLanguage_usesItsFormula() {
            // Three sentences of ten words of two syllables: ASL 10, ASW 2.
            Map<String, Object[]> expected = Map.of(
                    "en", new Object[]{"pilot", "Flesch", 27},
                    "fr", new Object[]{"salon", "Kandel-Moles", 50},
                    "de", new Object[]{"Auto", "Amstad", 53},
                    "es", new Object[]{"casa", "Fernández Huerta", 77},
                    "it", new Object[]{"casa", "Franchina-Vacca", 66},
                    "nl", new Object[]{"tafel", "Douma", 44},
                    "pt", new Object[]{"casa", "Martins", 69});
            expected.forEach((language, values) -> {
                Readability.Measure measure = Readability.measure(sentences((String) values[0], 3, 10), language);
                assertThat(measure.formula()).as(language).isEqualTo(values[1]);
                assertThat(measure.score()).as(language).isEqualTo(values[2]);
                assertThat(measure.words()).isEqualTo(30);
                assertThat(measure.longestSentence()).isEqualTo(10);
            });
        }

        @Test
        @DisplayName("the Nordic languages use LIX, where a higher score reads harder")
        void nordic_usesLix() {
            String text = sentences("bok bok bok bok bok bok fönstret fönstret fönstret fönstret", 3, 1);
            var measure = Readability.measure(text, "sv");
            assertThat(measure.formula()).isEqualTo("LIX");
            assertThat(measure.score()).isEqualTo(50);
            assertThat(measure.band()).isEqualTo(Readability.Band.DIFFICULT);
            assertThat(Readability.measure(text, "fi").score()).isNull();
        }

        @Test
        @DisplayName("syllables follow the vowels of each language")
        void syllables() {
            Map<String, Integer> en = Map.of("named", 1, "school", 1, "2026", 0);
            en.forEach((word, count) -> assertThat(
                    Readability.syllables(word, "en")).as(word).isEqualTo(count));
            assertThat(Readability.syllables("Noël", "fr")).isEqualTo(2);
            assertThat(Readability.syllables("école", "fr")).isEqualTo(2);
            assertThat(Readability.syllables("schöne", "de")).isEqualTo(2);
            assertThat(Readability.syllables("ruïne", "nl")).isEqualTo(3);
            assertThat(Readability.syllables("coração", "pt")).isEqualTo(3);
        }

        @Test
        @DisplayName("the score is the fact of the rule of the scale of the language: flesch, or lix for Swedish")
        void theScore_isTheFactOfItsScale() {
            String text = "<html><body><main><p>" + sentences("information", 3, 12) + "</p></main></body></html>";

            assertThat(ReadabilityChecks.facts(Jsoup.parse(text), "en")).containsKey("readability.flesch")
                    .doesNotContainKey("readability.lix");
            assertThat(ReadabilityChecks.facts(Jsoup.parse(text), "sv")).containsKey("readability.lix")
                    .doesNotContainKey("readability.flesch");
        }

        @Test
        @DisplayName("a hard text breaks the rule of its scale and the thresholds, and a short text gives none")
        void aHardText_givesFindings() {
            String hard = sentences("internationalisation", 2, 30);
            List<Finding> found = run(page("<html lang='en'>" + GOOD_HEAD + "<body><h1>T</h1><main><p>" + hard
                    + "</p></main></body></html>"));

            assertThat(rules(found)).contains("readability.flesch", "readability.longSentences",
                    "readability.thinContent").doesNotContain("readability.lix", "readability.longParagraphs");
            assertThat(found).filteredOn(finding -> finding.ruleId().equals("readability.longSentences"))
                    .as("one finding per long sentence").extracting(Finding::text).containsExactly("30", "30");
            assertThat(rules(run(page("<html lang='en'><body><p>Five words are not enough.</p></body></html>"))))
                    .noneMatch(rule -> rule.startsWith("readability."));
        }

        @Test
        @DisplayName("each long sentence is a finding, the longest first, with the sentence and its words as values")
        void eachLongSentence_isAFinding() {
            String shortOne = sentences("word", 1, 10);
            String longOne = sentences("long", 1, 30);
            String longest = sentences("longest", 1, 40);
            List<Finding> found = run(page("<html lang='en'>" + GOOD_HEAD + "<body><h1>T</h1><main><p>" + shortOne
                    + " " + longOne + " " + longest + "</p></main></body></html>"))
                    .stream().filter(finding -> finding.ruleId().equals("readability.longSentences")).toList();

            assertThat(found).extracting(Finding::text).containsExactly("40", "30");
            assertThat(found.get(0).context()).isEqualTo(longest);
            assertThat(found.get(0).values()).containsExactly(Map.entry("sentence", longest), Map.entry("words", 40));
            assertThat(found.get(1).values()).containsEntry("sentence", longOne).containsEntry("words", 30);
        }

        @Test
        @DisplayName("the text leaves out the navigation, the scripts and what is hidden")
        void theText_leavesOutWhatIsNotText() {
            assertThat(ReadabilityChecks.text(Jsoup.parse("<body><nav>Menu</nav><main><p>One</p><div>two"
                    + "<span aria-hidden='true'>x</span></div><script>var a;</script></main></body>"))).isEqualTo("One\ntwo");
        }

        @Test
        @DisplayName("a list item, a table cell and a menu with a role are no part of a long sentence")
        void aBlock_endsASentence() {
            String text = ReadabilityChecks.text(Jsoup.parse("<body><main><div role='navigation'><a>Home</a> <a>About</a>"
                    + "</div><ul><li><a href='/a'>Our offer for</a> small teams</li><li>Prices and plans</li></ul>"
                    + "<p>A short <b>sentence</b> here.<br>And another one</p></main></body>"));

            assertThat(Readability.sentences(text))
                    .containsExactly("Our offer for small teams", "Prices and plans", "A short sentence here.", "And another one");
        }
    }

    @Nested
    class LinkRules {

        @Test
        @DisplayName("the links of the HTML and the broken internal links give one finding each")
        void eachLink_givesAFinding() {
            CheckInput input = page("<html><body><a href='http://localhost:8080/cms/x.html'>A</a>"
                    + "<a href='https://example.com' target='_blank'>B</a>"
                    + "<a href='https://example.com' target='_blank' rel='noopener'>C</a>"
                    + "<a href='mailto:a@b.c'>D</a></body></html>")
                    .withFact(LinkChecks.BROKEN, List.of(new LinkCheck.BrokenPath("/sites/digitall/gone", "missing")))
                    .withFact(LinkChecks.LOCAL_HOSTS, Set.of("localhost"));

            List<Finding> found = run(input).stream().filter(finding -> finding.ruleId().startsWith("links.")).toList();

            assertThat(found).extracting(Finding::ruleId, Finding::text).containsExactlyInAnyOrder(
                    tuple("links.broken", "/sites/digitall/gone"),
                    tuple("links.hardcodedUrls", "http://localhost:8080/cms/x.html"),
                    tuple("links.mixedContent", "http://localhost:8080/cms/x.html"),
                    tuple("links.blankNoopener", "<a href=\"https://example.com\" target=\"_blank\"></a>"));
        }
    }

    @Nested
    class HealthRules {

        @Test
        @DisplayName("a raw key, a placeholder and the state of the content give one finding each")
        void eachDefect_givesAFinding() {
            CheckInput input = page("<html><body><p>jnt_news:label.title</p><p>Lorem ipsum dolor</p>"
                    + "<p>Visit https://example.com/a.b</p><script>var x = 'cg:key.one';</script></body></html>")
                    .withFact(HealthChecks.CONTENT, new HealthChecks.Content(
                            List.of(new HealthChecks.Unpublished("/sites/digitall/home/main/intro", "jnt:text", "modified")),
                            List.of(new HealthChecks.Untranslated("/sites/digitall/home/main/intro", "jnt:text", List.of("de", "fr"))),
                            0, List.of(), null));

            List<Finding> found = run(input).stream().filter(finding -> finding.ruleId().startsWith("health.")).toList();

            assertThat(found).extracting(Finding::ruleId, Finding::text).containsExactlyInAnyOrder(
                    tuple("health.rawI18nKeys", "jnt_news:label.title"),
                    tuple("health.unpublishedChanges", "/sites/digitall/home/main/intro"),
                    tuple("health.missingTranslations", "/sites/digitall/home/main/intro"),
                    tuple("health.placeholderText", "Lorem ipsum"));
            assertThat(found).filteredOn(finding -> finding.ruleId().equals("health.missingTranslations"))
                    .extracting(Finding::context).containsExactly("de, fr");
        }
    }

    @Nested
    @DisplayName("the values that the drawer shows")
    class Values {

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object value) {
            return (Map<String, Object>) value;
        }

        @SuppressWarnings("unchecked")
        private static List<Object> list(Object value) {
            return (List<Object>) value;
        }

        /** Three sentences of ten words, every word of two syllables: ASL 10, ASW 2. */
        private static String textOf(String word) {
            String sentence = String.join(" ", Collections.nCopies(10, word)) + ".";
            return String.join(" ", Collections.nCopies(3, sentence));
        }

        @Test
        @DisplayName("the tags of seo.tags read the head and the images")
        void seo() {
            Map<String, Object> seo = SeoChecks.tagsValues(Jsoup.parse("<html lang='EN'>" + GOOD_HEAD
                    + "<body><img src='a.png'><img src='b.png' alt=''>"
                    + "<p>Our yearly report is out, with every number of the year. <a href='/report'>Read more</a></p>"
                    + "<p><a href='/other'>read more »</a></p></body></html>"), "en-US");

            assertThat(seo).containsEntry("title", Map.of("text", "Annual report of the Digitall company 2026", "length", 42))
                    .containsEntry("canonical", "/home")
                    .containsEntry("twitterCard", null)
                    .containsEntry("jsonLd", Map.of("count", 1, "errors", 0))
                    .containsEntry("totalImages", 2)
                    .containsEntry("imagesWithoutAlt", 1)
                    .containsEntry("lang", Map.of("htmlLang", "en", "expected", "en"));
            assertThat(map(seo.get("robots"))).containsEntry("content", null).containsEntry("noindex", false);
            assertThat(map(seo.get("og"))).containsEntry("title", "Report").containsEntry("description", null);
        }

        @Test
        @DisplayName("readability.statistics gives the statistics of the main text, in English with the grade level")
        void readability() {
            Document html = Jsoup.parse("<body><h1>Report</h1><main><p>" + textOf("pilot") + "</p></main></body>");
            Map<String, Object> facts = ReadabilityChecks.statisticsValues(html, "en", 9);

            assertThat(facts).containsEntry("lang", "en").containsEntry("spaced", true).containsEntry("empty", false)
                    .containsEntry("scored", true).containsEntry("formula", "Flesch").containsEntry("scale", "flesch")
                    .containsEntry("score", 27).containsEntry("band", "veryDifficult").containsEntry("gradeLevel", 11.9)
                    .containsEntry("words", 30).containsEntry("sentences", 3).containsEntry("avgSentenceLength", 10.0)
                    .containsEntry("avgSyllablesPerWord", 2.0).containsEntry("longSentences", 3L)
                    .containsEntry("longSentenceWords", 9).containsEntry("readingMinutes", 1)
                    .containsEntry("paragraphs", 1).containsEntry("avgWordsPerParagraph", 30)
                    .doesNotContainKey("outline");
            assertThat(ReadabilityChecks.headingsValues(html)).containsEntry("h1Count", 1).containsEntry("headingSkips", 0)
                    .containsEntry("headingCount", 0);
        }

        @Test
        @DisplayName("LIX reads harder upwards, and a short or unspaced text gives counts only")
        void readability_otherLanguages() {
            String nordic = String.join(" ", Collections.nCopies(3,
                    String.join(" ", Collections.nCopies(6, "bok")) + " " + String.join(" ", Collections.nCopies(4, "fönstret")) + "."));
            assertThat(Readability.statistics(nordic, "sv", null)).containsEntry("formula", "LIX")
                    .containsEntry("scale", "lix").containsEntry("score", 50).containsEntry("band", "difficult")
                    .containsEntry("avgSyllablesPerWord", null).containsEntry("longSentences", null);
            assertThat(Readability.statistics("Five words are not enough.", "en", 25)).containsEntry("empty", true)
                    .containsEntry("words", 5).containsEntry("score", null);
            assertThat(Readability.statistics("これは文です。これも文です。", "ja", 25)).containsEntry("spaced", false)
                    .containsEntry("empty", false).containsEntry("sentences", 2L).containsEntry("words", null);
        }

        @Test
        @DisplayName("readability.headings lists the headings of the main text and a selector of each, and the counts cover the page")
        void outline() {
            Document html = Jsoup.parse("<body><header><h2>Menu</h2><h4>Shop</h4></header>"
                    + "<h1>Site</h1><main>"
                    + "<h2>Intro</h2><section><h4>Detail</h4></section><section id='faq'><h2><img alt='Help'></h2>"
                    + "<h3></h3></section></main></body>");
            Map<String, Object> facts = ReadabilityChecks.headingsValues(html);

            assertThat(facts).containsEntry("h1Count", 1).containsEntry("headingSkips", 2).containsEntry("headingCount", 4);
            List<Object> outline = list(facts.get("outline"));
            assertThat(outline).extracting(row -> map(row).get("level"), row -> map(row).get("text"),
                    row -> map(row).get("skippedFrom"), row -> map(row).get("empty")).containsExactly(
                    tuple(2, "Intro", null, null), tuple(4, "Detail", 2, null), tuple(2, "Help", null, null),
                    tuple(3, "", null, true));
            List<Element> headings = html.select("main h1, main h2, main h3, main h4, main h5, main h6");
            for (int i = 0; i < outline.size(); i++) {
                assertThat(html.select((String) map(outline.get(i)).get("selector")))
                        .as("the selector of heading %d finds it alone", i).containsExactly(headings.get(i));
            }
        }

        @Test
        @DisplayName("links.broken counts the links to a page, and the internal links that the render resolved")
        void links() {
            Map<String, Object> facts = LinkChecks.values(Jsoup.parse("<a href='/sites/digitall/a.html'>A</a>"
                            + "<a href='https://example.com'>B</a><a href='http://localhost:8080/x'>C</a>"
                            + "<a href='#top'>D</a><a href='mailto:a@b.c'>E</a>"), Set.of("localhost"),
                    new LinkCheck.InternalLinks(1, 1, List.of(new LinkCheck.BrokenPath("/sites/digitall/a", "missing"))));

            assertThat(facts).containsEntry("total", 3).containsEntry("internal", 2).containsEntry("external", 1)
                    .containsEntry("checked", 1).containsEntry("skipped", 1)
                    .containsEntry("broken", List.of(Map.of("path", "/sites/digitall/a", "reason", "missing")));
            assertThat(LinkChecks.values(Jsoup.parse(""), null, null)).containsEntry("checked", null)
                    .containsEntry("broken", List.of());
        }

        @Test
        @DisplayName("the health rules give the state of the content: what is not published, and what is not translated")
        void health() {
            HealthChecks.Content content = new HealthChecks.Content(
                    List.of(new HealthChecks.Unpublished(PATH, "jnt:page", "modified")),
                    List.of(new HealthChecks.Untranslated(PATH + "/main/intro", "jnt:text", List.of("fr"))),
                    3, List.of("en", "fr"), "modified");

            assertThat(HealthChecks.unpublishedValues(content)).containsEntry("pageStatus", "modified")
                    .containsEntry("scanned", 3)
                    .containsEntry("unpublished", List.of(Map.of("path", PATH, "type", "jnt:page", "status", "modified")));
            assertThat(HealthChecks.untranslatedValues(content)).containsEntry("activeLanguages", List.of("en", "fr"))
                    .containsEntry("scanned", 3).containsEntry("untranslated",
                            List.of(Map.of("path", PATH + "/main/intro", "type", "jnt:text", "missing", List.of("fr"))));
        }
    }

    @Nested
    @DisplayName("the result of each rule")
    class Results {

        @Test
        @DisplayName("a page gives one result per rule that ran, passed or failed, with the values of its check")
        void aPage_givesOneResultPerRule() {
            String text = String.join(" ", Collections.nCopies(40, "word")) + ".";
            Map<String, CheckResult> results = results(page("<html lang='en'><head><title>Home</title></head><body>"
                    + "<h1>Home</h1><main><p>" + text + "</p></main><a href='/sites/digitall/a.html'>A</a></body></html>")
                    .withFact(LinkChecks.INTERNAL, new LinkCheck.InternalLinks(1, 0, List.of()))
                    .withFact(ReadabilityChecks.LONG_SENTENCE_WORDS, 25));

            assertThat(results.get("seo.title")).satisfies(result -> {
                assertThat(result.passed()).isFalse();
                assertThat(result.values()).containsEntry("length", 4).containsEntry("text", "Home");
                assertThat(result.findings()).extracting(Finding::text).containsExactly("4");
            });
            assertThat(results.get("seo.canonical").values()).containsEntry("href", null);
            assertThat(results.get("seo.openGraph").values()).containsOnlyKeys("title", "description", "image", "type");
            assertThat(results.get("links.broken")).satisfies(result -> {
                assertThat(result.passed()).isTrue();
                assertThat(result.values()).containsEntry("total", 1).containsEntry("checked", 1)
                        .containsEntry("broken", List.of());
            });
            assertThat(results.get("readability.thinContent")).satisfies(result -> {
                assertThat(result.passed()).isFalse();
                assertThat(result.values()).containsEntry("value", 40);
            });
            assertThat(results.get("readability.flesch").values()).containsOnlyKeys("score", "formula", "band")
                    .containsEntry("formula", "Flesch");
            assertThat(results.get("html.imageAltMissing")).as("a rule whose check is not bound gives no result").isNull();
        }

        @Test
        @DisplayName("a measure rule always passes, and carries what the tab of the drawer shows")
        void aMeasureRule_passesWithItsValues() {
            Map<String, CheckResult> results = results(page("<html lang='en'><head><title>Home</title></head><body>"
                    + "<main><h1>Home</h1><h3>Detail</h3><p>Short.</p></main></body></html>"));

            for (String measure : List.of(SeoChecks.TAGS, ReadabilityChecks.STATISTICS, ReadabilityChecks.HEADINGS)) {
                assertThat(results.get(measure).passed()).as(measure).isTrue();
                assertThat(results.get(measure).findings()).as(measure).isEmpty();
                assertThat(ContentRules.base().get(measure).minimum()).as(measure).isNull();
                assertThat(ContentRules.base().get(measure).maximum()).as(measure).isNull();
            }
            assertThat(map(results.get(SeoChecks.TAGS).values().get("title"))).containsEntry("text", "Home");
            assertThat(results.get(ReadabilityChecks.STATISTICS).values()).containsEntry("words", 3)
                    .containsEntry("longSentenceWords", null);
            assertThat(results.get(ReadabilityChecks.HEADINGS).values()).containsEntry("h1Count", 1)
                    .containsEntry("headingSkips", 1);
        }

        @Test
        @DisplayName("the health rules carry the state of the content, and no value when the render could not read it")
        void theHealthRules_carryTheStateOfTheContent() {
            String html = "<html><body><p>Text</p></body></html>";
            Map<String, CheckResult> read = results(page(html).withFact(HealthChecks.CONTENT, new HealthChecks.Content(
                    List.of(), List.of(), 2, List.of("en"), "published")));
            Map<String, CheckResult> unread = results(page(html));

            assertThat(read.get("health.unpublishedChanges").values()).containsEntry("pageStatus", "published")
                    .containsEntry("scanned", 2);
            assertThat(read.get("health.missingTranslations").values()).containsEntry("activeLanguages", List.of("en"));
            assertThat(unread.get("health.unpublishedChanges").values()).isEmpty();
            assertThat(unread.get("health.missingTranslations").values()).isEmpty();
        }

        @Test
        @DisplayName("an item gets the tags of seo.tags, and the other SEO rules pass with no value")
        void anItem_getsTheTagsOnly() {
            Map<String, CheckResult> results = results(HtmlCheck.input(PATH + "/main/intro", "en",
                    Jsoup.parse("<p>Intro <img src='a.png'></p>"), Scope.ITEM));

            assertThat(results.get(SeoChecks.TAGS).values()).containsEntry("totalImages", 1);
            assertThat(results.get("seo.canonical")).satisfies(result -> {
                assertThat(result.passed()).isTrue();
                assertThat(result.values()).isEmpty();
            });
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object value) {
            return (Map<String, Object>) value;
        }
    }

    @Test
    @DisplayName("each page check is a check of the scope PAGE, under the id of a shipped rule")
    void eachCheck_isShipped() {
        for (List<String> ids : List.of(SeoChecks.IDS, ReadabilityChecks.IDS, LinkChecks.IDS, HealthChecks.IDS)) {
            Family.checks(ids, "x", input -> new Family.Found()).forEach((id, check) -> {
                assertThat(check.scopes()).containsExactly(ContentCheck.Scope.PAGE);
                assertThat(ContentRules.base().get(id).evaluator()).isEqualTo(id);
            });
        }
    }
}
