package org.jahia.community.contentgovernance.html;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.ThresholdCheck;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.community.contentgovernance.page.ReadabilityChecks;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HtmlCheckTest {

    @Test
    @DisplayName("the HTML check runs the checks of the scope PAGE on the rendered HTML, with the rules of its site")
    void findings_runTheChecksOfAPage() {
        CheckRegistry registry = mock(CheckRegistry.class);
        Finding finding = new Finding("html.imageAltMissing", "critical", "<img src=\"a.png\">", null);
        CheckResult failed = new CheckResult("html.imageAltMissing", false, Map.of(), List.of(finding));
        CheckResult passed = new CheckResult("seo.tags", true, Map.of("totalImages", 1), List.of());
        when(registry.results(any(ContentRules.class), any(CheckInput.class))).thenReturn(List.of(failed, passed));
        Document html = Jsoup.parse("<p><img src=\"a.png\"></p>");
        HtmlCheck.Rendered rendered = new HtmlCheck.Rendered("/sites/digitall/home/main/intro", "jnt:text", Scope.ITEM,
                html, Set.of("localhost"), Map.of());

        assertThat(new HtmlCheck(registry).results(rendered, Locale.FRENCH)).containsExactly(failed, passed);
        assertThat(new HtmlCheck(registry).findings(rendered, Locale.FRENCH)).containsExactly(finding);

        ArgumentCaptor<CheckInput> input = ArgumentCaptor.forClass(CheckInput.class);
        verify(registry, times(2)).results(any(ContentRules.class), input.capture());
        assertThat(input.getValue().scope()).isEqualTo(ContentCheck.Scope.PAGE);
        assertThat(input.getValue().siteKey()).isEqualTo("digitall");
        assertThat(input.getValue().language()).isEqualTo("fr");
        assertThat(input.getValue().fact(HtmlRuleChecks.DOCUMENT)).isSameAs(html);
        assertThat(input.getValue().fact(HtmlRuleChecks.HTML_SCOPE)).isEqualTo(Scope.ITEM);
        assertThat(input.getValue().fact(ReadabilityChecks.LONG_SENTENCE_WORDS))
                .as("the maximum of readability.longSentences, which the statistics count against").isEqualTo(25);
    }

    @Test
    @DisplayName("on fetched HTML, the HTML check runs the named rules only, with the rules that the caller read")
    void findings_onFetchedHtml_runTheNamedRules() {
        CheckRegistry registry = new CheckRegistry();
        Registries.bind(registry, new ThresholdCheck(), ContentRule.THRESHOLD);
        Document html = Jsoup.parse("<html><head><title>" + "x".repeat(35) + "</title></head><body><p>"
                + "word ".repeat(10) + "</p></body></html>");
        ContentRules site = ContentRules.base().changed("seo.title", null, null, 40.0, null)
                .changed("readability.thinContent", null, null, 300.0, null);
        HtmlCheck pages = new HtmlCheck(registry);

        assertThat(pages.findings(site, "/sites/digitall/home", "en", html, Set.of("seo.title")))
                .extracting(Finding::ruleId, Finding::text).containsExactly(tuple("seo.title", "35"));
        assertThat(pages.findings(ContentRules.base(), "/sites/digitall/home", "en", html, Set.of("seo.title"))).isEmpty();
    }
}
