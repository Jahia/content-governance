package org.jahia.community.contentgovernance.html.rules;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HtmlRuleChecksTest {

    private static final String PAGE = "<html lang=\"en\"><head><title>Home</title>"
            + "<meta name=\"description\" content=\"d\"></head><body><h1>Home</h1><img src=\"a.png\"></body></html>";

    @Test
    @DisplayName("the check of an HTML rule gives the findings of its rule, with the severity of the site")
    void check_givesTheFindingsOfItsRule() {
        ContentRule imageAlt = ContentRules.base().get("html.imageAltMissing");
        ContentRule language = ContentRules.base().get("html.pageLanguageMissing");
        CheckInput input = CheckInput.page("/sites/digitall/home", "en", PAGE);

        assertThat(new HtmlRuleChecks.Check(HtmlRule.IMAGE_ALT_MISSING).check(input, imageAlt))
                .extracting(Finding::ruleId, Finding::text, Finding::locator)
                .containsExactly(tuple("html.imageAltMissing", "<img src=\"a.png\">", "html > body > img"));
        assertThat(new HtmlRuleChecks.Check(HtmlRule.PAGE_LANGUAGE_MISSING).check(input, language)).isEmpty();
    }

    @Test
    @DisplayName("a rendered item gets the rules of one item only")
    void check_ofAnItem_skipsTheRulesOfAPage() {
        ContentRule language = ContentRules.base().get("html.pageLanguageMissing");
        CheckInput item = CheckInput.page("/sites/digitall/home/main/intro", "en", "<p>intro</p>")
                .withFact(HtmlRuleChecks.DOCUMENT, Jsoup.parse("<p>intro</p>"))
                .withFact(HtmlRuleChecks.HTML_SCOPE, Scope.ITEM);

        assertThat(new HtmlRuleChecks.Check(HtmlRule.PAGE_LANGUAGE_MISSING).check(item, language)).isEmpty();
        assertThat(new HtmlRuleChecks.Check(HtmlRule.PAGE_LANGUAGE_MISSING)
                .check(CheckInput.page("/sites/digitall/home", "en", "<p>intro</p>"), language)).hasSize(1);
    }

    @Test
    @DisplayName("each HTML rule is a rule that the module ships, under the same id")
    void eachHtmlRule_isShipped() {
        for (HtmlRule rule : HtmlRule.values()) {
            assertThat(ContentRules.base().get(rule.id()).evaluator()).isEqualTo(rule.id());
            assertThat(HtmlRule.byId(rule.id())).isSameAs(rule);
        }
        assertThat(HtmlRule.byId("seo.title")).isNull();
    }
}
