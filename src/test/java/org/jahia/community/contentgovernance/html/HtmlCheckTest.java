package org.jahia.community.contentgovernance.html;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jahia.community.contentgovernance.html.rules.Scope;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HtmlCheckTest {

    @Test
    @DisplayName("the HTML check runs the checks of the scope PAGE on the rendered HTML, with the rules of its site")
    void findings_runTheChecksOfAPage() {
        CheckRegistry registry = mock(CheckRegistry.class);
        Finding finding = new Finding("html.imageAltMissing", "critical", "<img src=\"a.png\">", null);
        when(registry.run(any(CheckInput.class), eq(Set.of())))
                .thenReturn(List.of(new CheckRegistry.Checked(ContentRules.base().get("html.imageAltMissing"), finding)));
        Document html = Jsoup.parse("<p><img src=\"a.png\"></p>");
        HtmlCheck.Rendered rendered = new HtmlCheck.Rendered("/sites/digitall/home/main/intro", "jnt:text", Scope.ITEM,
                html, Set.of("localhost"), Map.of());

        assertThat(new HtmlCheck(registry).findings(rendered, Locale.FRENCH)).containsExactly(finding);

        ArgumentCaptor<CheckInput> input = ArgumentCaptor.forClass(CheckInput.class);
        verify(registry).run(input.capture(), eq(Set.of()));
        assertThat(input.getValue().scope()).isEqualTo(ContentCheck.Scope.PAGE);
        assertThat(input.getValue().siteKey()).isEqualTo("digitall");
        assertThat(input.getValue().language()).isEqualTo("fr");
        assertThat(input.getValue().fact(HtmlRuleChecks.DOCUMENT)).isSameAs(html);
        assertThat(input.getValue().fact(HtmlRuleChecks.HTML_SCOPE)).isEqualTo(Scope.ITEM);
    }
}
