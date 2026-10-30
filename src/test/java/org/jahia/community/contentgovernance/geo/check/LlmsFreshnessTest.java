package org.jahia.community.contentgovernance.geo.check;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LlmsFreshness#check}: each link of the served llms.txt is checked against the
 * published pages of the site.
 *
 * GeoReport once read "stale" as a boolean and "outdated" as an array - the reverse of what this
 * class produces - so every report said the file was never stale. That is only catchable by
 * asserting the JSON *type* of each key, not just its value, so the tests below do both.
 */
class LlmsFreshnessTest {

    private static final String SITE = "/sites/mysite";
    private static final String BASE = "https://www.mysite.example";

    private static PublishedMap.Entry entry(String jcrPath) {
        return new PublishedMap.Entry(jcrPath, "Some Title", "2024-01-01", 0L, "jnt:page", false, true, "en");
    }

    private static Map<String, PublishedMap.Entry> published(String... paths) {
        Map<String, PublishedMap.Entry> map = new LinkedHashMap<>();
        for (String path : paths) {
            map.put(path, entry(SITE + path.replace(".html", "")));
        }
        return map;
    }

    private static String link(String title, String url) {
        return "- [" + title + "](" + url + ")\n";
    }

    /** Builds `count` distinct links under distinct paths. */
    private static String links(int count, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(link("Title " + i, "/" + prefix + "-" + i + ".html"));
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("no file, or nothing to compare with")
    class AbsentInputs {

        @ParameterizedTest(name = "served = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", " \n\t "})
        @DisplayName("present is false and outdated is false, no other keys are emitted")
        void servedMissingOrBlank_reportsNotPresent(String served) {
            JSONObject out = LlmsFreshness.check(served, published("/index.html"), SITE, BASE);

            assertThat(out.get("present")).isInstanceOf(Boolean.class);
            assertThat(out.getBoolean("present")).isFalse();
            assertThat(out.get("outdated")).isInstanceOf(Boolean.class);
            assertThat(out.getBoolean("outdated")).isFalse();
            assertThat(out.has("listed")).isFalse();
            assertThat(out.has("stale")).isFalse();
            assertThat(out.has("listedPaths")).isFalse();
        }

        @Test
        @DisplayName("no published map: present, not outdated, nothing judged")
        void nullPublished_judgesNothing() {
            JSONObject out = LlmsFreshness.check(link("Home", "/index.html"), null, SITE, BASE);

            assertThat(out.getBoolean("present")).isTrue();
            assertThat(out.getBoolean("outdated")).isFalse();
            assertThat(out.has("stale")).isFalse();
        }
    }

    @Nested
    @DisplayName("every link leads to a published page")
    class FreshCase {

        @Test
        @DisplayName("not outdated, empty stale array, listed count and paths")
        void allLinksPublished_isNotOutdated() {
            String body = link("Home", "/index.html") + link("About", "/about.html");

            JSONObject out = LlmsFreshness.check(body, published("/index.html", "/about.html"), SITE, BASE);

            assertThat(out.get("listed")).isInstanceOf(Integer.class);
            assertThat(out.getInt("listed")).isEqualTo(2);
            assertThat(out.get("stale")).isInstanceOf(JSONArray.class);
            assertThat(out.getJSONArray("stale").length()).isZero();
            assertThat(out.get("outdated")).isInstanceOf(Boolean.class);
            assertThat(out.getBoolean("outdated")).isFalse();
            JSONArray listedPaths = out.getJSONArray("listedPaths");
            assertThat(listedPaths.getString(0)).isEqualTo("/index.html");
            assertThat(listedPaths.getString(1)).isEqualTo("/about.html");
        }

        @Test
        @DisplayName("a published page the file does not list is the author's choice, not a finding")
        void unlistedPublishedPage_isNotReported() {
            String body = link("Home", "/index.html");

            JSONObject out = LlmsFreshness.check(body, published("/index.html", "/other.html"), SITE, BASE);

            assertThat(out.getBoolean("outdated")).isFalse();
            assertThat(out.has("missing")).isFalse();
        }

        @Test
        @DisplayName("CRLF line endings, indentation and trailing text are read the same")
        void crlfAndIndentation_areRead() {
            String served = "  -   [Home](/index.html)  trailing text\r\n  -  [About](/about.html)\r\n";

            JSONObject out = LlmsFreshness.check(served, published("/index.html", "/about.html"), SITE, BASE);

            assertThat(out.getInt("listed")).isEqualTo(2);
            assertThat(out.getBoolean("outdated")).isFalse();
        }

        @Test
        @DisplayName("an absolute link to the site's own host is compared by its path")
        void absoluteLinkToSite_isComparedByPath() {
            JSONObject out = LlmsFreshness.check(link("Home", BASE + "/index.html"), published("/index.html"),
                    SITE, BASE);

            assertThat(out.getInt("listed")).isEqualTo(1);
            assertThat(out.getBoolean("outdated")).isFalse();
            assertThat(out.getJSONArray("listedPaths").getString(0)).isEqualTo("/index.html");
        }
    }

    @Nested
    @DisplayName("links to other sites")
    class OtherHosts {

        @Test
        @DisplayName("a link to another host is neither counted nor judged")
        void linkToAnotherHost_isIgnored() {
            String body = link("Home", "/index.html") + link("Partner", "https://partner.example/page.html");

            JSONObject out = LlmsFreshness.check(body, published("/index.html"), SITE, BASE);

            assertThat(out.getInt("listed")).isEqualTo(1);
            assertThat(out.getBoolean("outdated")).isFalse();
        }

        @Test
        @DisplayName("without a known site host, absolute links are judged like relative ones")
        void unknownSiteHost_judgesAbsoluteLinks() {
            JSONObject out = LlmsFreshness.check(link("Ghost", "https://any.example/ghost.html"),
                    published("/index.html"), SITE, null);

            assertThat(out.getInt("listed")).isEqualTo(1);
            assertThat(out.getJSONArray("stale").getJSONObject(0).getString("why")).isEqualTo("gone");
        }
    }

    @Nested
    @DisplayName("links that no published page answers")
    class StaleLinks {

        @Test
        @DisplayName("the page now answers at another address: why is addressChanged")
        void linkToMovedPage_reportsAddressChanged() {
            // The node behind /en/old-page.html is now published as /en/new-page.html.
            Map<String, PublishedMap.Entry> pub = new LinkedHashMap<>();
            pub.put("/en/new-page.html", entry(SITE + "/old-page"));

            JSONObject out = LlmsFreshness.check(link("Old Page", "/en/old-page.html"), pub, SITE, BASE);

            JSONArray stale = out.getJSONArray("stale");
            assertThat(stale.length()).isEqualTo(1);
            JSONObject e = stale.getJSONObject(0);
            assertThat(e.getString("path")).isEqualTo("/en/old-page.html");
            assertThat(e.getString("title")).isEqualTo("Old Page");
            assertThat(e.getString("why")).isEqualTo("addressChanged");
            assertThat(out.getBoolean("outdated")).isTrue();
        }

        @Test
        @DisplayName("no published trace at all: why is gone")
        void linkToUnpublishedPage_reportsGone() {
            JSONObject out = LlmsFreshness.check(link("Ghost Page", "/ghost.html"), new LinkedHashMap<>(), SITE,
                    BASE);

            JSONArray stale = out.getJSONArray("stale");
            assertThat(stale.length()).isEqualTo(1);
            assertThat(stale.getJSONObject(0).getString("why")).isEqualTo("gone");
        }
    }

    @Nested
    @DisplayName("the 100-item cap")
    class Cap {

        @Test
        @DisplayName("101 stale links: stale is capped at 100, in document order")
        void staleArray_isCappedAt100InDocumentOrder() {
            JSONObject out = LlmsFreshness.check(links(101, "stale"), new LinkedHashMap<>(), SITE, BASE);

            JSONArray stale = out.getJSONArray("stale");
            assertThat(stale.length()).isEqualTo(100);
            assertThat(stale.getJSONObject(0).getString("path")).isEqualTo("/stale-0.html");
            assertThat(stale.getJSONObject(99).getString("path")).isEqualTo("/stale-99.html");
        }

        @Test
        @DisplayName("listedPaths is capped, the count is not")
        void listedPaths_isCapped() {
            JSONObject out = LlmsFreshness.check(links(150, "page"), new LinkedHashMap<>(), SITE, BASE);

            assertThat(out.getInt("listed")).isEqualTo(150);
            JSONArray listedPaths = out.getJSONArray("listedPaths");
            assertThat(listedPaths.length()).isEqualTo(100);
            assertThat(listedPaths.getString(99)).isEqualTo("/page-99.html");
        }
    }

    @Nested
    @DisplayName("listingFor: reading a report produced by check")
    class ListingFor {

        @Test
        @DisplayName("null report, or no file, returns null")
        void noReport_returnsNull() {
            assertThat(LlmsFreshness.listingFor(null, "/index.html")).isNull();
            JSONObject report = LlmsFreshness.check(null, null, SITE, BASE);
            assertThat(LlmsFreshness.listingFor(report, "/index.html")).isNull();
        }

        @Test
        @DisplayName("null path returns null even for a present report")
        void nullPath_returnsNull() {
            JSONObject report = LlmsFreshness.check(link("Home", "/index.html"), published("/index.html"), SITE, BASE);
            assertThat(LlmsFreshness.listingFor(report, null)).isNull();
        }

        @Test
        @DisplayName("listed=true for a listed path, false otherwise, and nothing else")
        void listedFlag_only() {
            JSONObject report = LlmsFreshness.check(link("Home", "/index.html"), published("/index.html"), SITE, BASE);

            JSONObject listed = LlmsFreshness.listingFor(report, "/index.html");
            assertThat(listed.get("listed")).isInstanceOf(Boolean.class);
            assertThat(listed.getBoolean("listed")).isTrue();
            assertThat(listed.length()).isEqualTo(1);

            JSONObject other = LlmsFreshness.listingFor(report, "/elsewhere.html");
            assertThat(other.getBoolean("listed")).isFalse();
            assertThat(other.length()).isEqualTo(1);
        }
    }
}
