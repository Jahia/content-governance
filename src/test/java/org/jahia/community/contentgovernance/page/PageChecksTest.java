package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.ThresholdCheck;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
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

    /** Every check of the server on the HTML, with the shipped rules, as the service runs them. */
    private static List<Finding> run(CheckInput input) {
        CheckRegistry registry = new CheckRegistry();
        bind(registry, SeoChecks.IDS, SeoChecks::read);
        bind(registry, ReadabilityChecks.IDS, ReadabilityChecks::read);
        bind(registry, LinkChecks.IDS, LinkChecks::read);
        bind(registry, HealthChecks.IDS, HealthChecks::read);
        Registries.bind(registry, new ThresholdCheck(), ContentRule.THRESHOLD);
        return Registries.run(registry, ContentRules.base(), input);
    }

    private static void bind(CheckRegistry registry, List<String> ids, Function<CheckInput, Family.Found> read) {
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
        @DisplayName("a hard text and its thresholds give findings, and a short text gives none")
        void aHardText_givesFindings() {
            String hard = sentences("internationalisation", 2, 30);
            List<Finding> found = run(page("<html lang='en'>" + GOOD_HEAD + "<body><h1>T</h1><main><p>" + hard
                    + "</p></main></body></html>"));

            assertThat(rules(found)).contains("readability.veryHardText", "readability.longSentences",
                    "readability.thinContent").doesNotContain("readability.hardText", "readability.longParagraphs");
            assertThat(found).filteredOn(finding -> finding.ruleId().equals("readability.longSentences"))
                    .extracting(Finding::text).containsExactly("30");
            assertThat(rules(run(page("<html lang='en'><body><p>Five words are not enough.</p></body></html>"))))
                    .noneMatch(rule -> rule.startsWith("readability."));
        }

        @Test
        @DisplayName("the text leaves out the navigation, the scripts and what is hidden")
        void theText_leavesOutWhatIsNotText() {
            assertThat(ReadabilityChecks.text(Jsoup.parse("<body><nav>Menu</nav><main><p>One</p><div>two"
                    + "<span aria-hidden='true'>x</span></div><script>var a;</script></main></body>"))).isEqualTo("One two");
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
                            List.of(new HealthChecks.Unpublished("/sites/digitall/home/main/intro", "modified")),
                            List.of(new HealthChecks.Untranslated("/sites/digitall/home/main/intro", List.of("de", "fr"))),
                            2, List.of("de", "en", "fr")));

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
    class TheFactsOfTheDrawer {

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object value) {
            return (Map<String, Object>) value;
        }

        @Test
        @DisplayName("the SEO facts read the tags of the head, the language and the images")
        void seo_readsTheHead() {
            Map<String, Object> seo = SeoChecks.describe(Jsoup.parse("<html lang='FR'>" + GOOD_HEAD
                    + "<body><img src='a.png'><img src='b.png' alt=''><script type='application/ld+json'>{</script>"
                    + "</body></html>"), "fr");

            assertThat(seo.get("title")).isEqualTo(Map.of("text", "Annual report of the Digitall company 2026", "length", 42));
            assertThat(seo.get("canonical")).isEqualTo("/home");
            assertThat(map(seo.get("og"))).containsEntry("title", "Report").containsEntry("description", null);
            assertThat(seo.get("jsonLd")).isEqualTo(Map.of("count", 2, "errors", 1L));
            assertThat(map(seo.get("lang"))).containsEntry("htmlLang", "fr").containsEntry("expected", "fr");
            assertThat(seo).containsEntry("totalImages", 2).containsEntry("imagesWithoutAlt", 1L);
            assertThat(map(seo.get("robots"))).containsEntry("content", null).containsEntry("noindex", false);
        }

        @Test
        @DisplayName("the readability facts give the score, the long sentences and the outline")
        void readability_givesTheScoreAndTheOutline() {
            String sentence = String.join(" ", Collections.nCopies(10, "pilot")) + ". ";
            String html = "<body><h1>Title</h1><main><h2>Part</h2><h4></h4><p>" + sentence.repeat(3)
                    + "</p><p>" + String.join(" ", Collections.nCopies(30, "pilot")) + ".</p></main></body>";

            Map<String, Object> facts = ReadabilityChecks.describe(Jsoup.parse(html), "en", 25);

            assertThat(facts).containsEntry("formula", "Flesch").containsEntry("scale", "flesch")
                    .containsEntry("band", "veryDifficult").containsEntry("words", 61).containsEntry("sentences", 4)
                    .containsEntry("longSentences", 1L).containsEntry("longSentenceWords", 25)
                    .containsEntry("paragraphs", 2).containsEntry("avgWordsPerParagraph", 30)
                    .containsEntry("h1Count", 1).containsEntry("headingSkips", 1).containsEntry("headingCount", 2);
            assertThat((List<?>) facts.get("outline")).hasSize(2);
            assertThat(map(((List<?>) facts.get("outline")).get(1)))
                    .containsEntry("level", 4).containsEntry("skippedFrom", 2).containsEntry("empty", true);
        }

        @Test
        @DisplayName("a text too short to measure, or written without spaces, gives no score")
        void readability_withoutAScore() {
            assertThat(ReadabilityChecks.describe(Jsoup.parse("<p>Five words are not enough.</p>"), "en", 25))
                    .containsEntry("empty", true).containsEntry("words", 5).containsEntry("scored", false);
            assertThat(ReadabilityChecks.describe(Jsoup.parse("<p>これは文です。これも文です。</p>"), "ja", 25))
                    .containsEntry("spaced", false).containsEntry("sentences", 2L).containsEntry("empty", false);
        }

        @Test
        @DisplayName("the link facts count the links and list the broken ones")
        void links_countAndListTheBroken() {
            CheckInput input = page("<body><a href='/home.html'>Home</a><a href='https://example.com'>Out</a>"
                    + "<a href='http://localhost:8080/x.html' target='_blank'>Here</a><a href='#top'>Top</a></body>")
                    .withFact(LinkChecks.BROKEN, List.of(new LinkCheck.BrokenPath("/sites/digitall/old", "missing")));

            assertThat(LinkChecks.describe(input))
                    .containsEntry("total", 3).containsEntry("internal", 2).containsEntry("external", 1)
                    .containsEntry("broken", List.of(Map.of("path", "/sites/digitall/old", "reason", "missing")))
                    .containsEntry("hardcodedHost", 1).containsEntry("mixedContent", 1).containsEntry("blankNoopener", 1);
        }

        @Test
        @DisplayName("the Jahia facts give the state of the content and samples of the raw keys")
        void health_givesTheStateAndTheSamples() {
            CheckInput input = page("<body><p>jnt_news:label.title</p></body>")
                    .withFact(HealthChecks.CONTENT, new HealthChecks.Content(
                            List.of(new HealthChecks.Unpublished(PATH, "modified")), List.of(), 3, List.of("en", "fr")));

            Map<String, Object> facts = HealthChecks.describe(input);

            assertThat(facts).containsEntry("scanned", 3).containsEntry("activeLanguages", List.of("en", "fr"))
                    .containsEntry("unpublished", List.of(Map.of("path", PATH, "status", "modified")));
            assertThat(facts.get("rawKeys")).isEqualTo(Map.of("count", 1,
                    "samples", List.of(Map.of("key", "jnt_news:label.title", "text", "jnt_news:label.title"))));
        }

        @Test
        @DisplayName("a content item gets no SEO facts")
        void item_hasNoSeoFacts() {
            HtmlCheck.Rendered item = new HtmlCheck.Rendered(PATH + "/main/intro", "jnt:text", Scope.ITEM,
                    Jsoup.parse("<p>Intro</p>"), Set.of("localhost"));

            assertThat(HtmlCheck.facts(item, java.util.Locale.ENGLISH, ContentRules.base()))
                    .containsOnlyKeys("readability", "links", "jahia");
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
