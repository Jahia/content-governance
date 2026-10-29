package org.jahia.community.contentgovernance.compliance.service.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextTest {

    @Test
    void blockTagsSeparateWordsAndInlineTagsDoNot() {
        assertThat(PlainText.fromHtml("<p>Livraison</p><p><strong>gra</strong>tuite</p><br/>ici"))
                .isEqualTo("Livraison gratuite ici");
    }

    @Test
    void entitiesAreDecodedAndScriptsDropped() {
        assertThat(PlainText.fromHtml("<style>p{color:red}</style>Satisfait&nbsp;ou&nbsp;rembours&eacute; &amp; plus<!-- note --><script>x()</script>"))
                .isEqualTo("Satisfait ou remboursé & plus");
    }

    @Test
    void collapseJoinsLines() {
        assertThat(PlainText.collapse("  one\n\n two\t three ")).isEqualTo("one two three");
    }

    @Test
    void unclosedTagsDropTheRestOfTheValue() {
        assertThat(PlainText.fromHtml("Visible <script>never closed")).isEqualTo("Visible");
        assertThat(PlainText.fromHtml("Visible <!-- never closed")).isEqualTo("Visible");
        assertThat(PlainText.fromHtml("Visible <p never closed")).isEqualTo("Visible");
    }

    @Test
    void closingTagsOfScriptsAreMatchedWhateverTheCase() {
        assertThat(PlainText.fromHtml("a<SCRIPT type=x>bad()</Script >b")).isEqualTo("a b");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void hostileValuesAreReadInLinearTime() {
        assertThat(PlainText.fromHtml("<".repeat(400_000))).isEmpty();
        assertThat(PlainText.fromHtml("<script ".repeat(50_000))).isEmpty();
        assertThat(PlainText.fromHtml("<p ".repeat(100_000))).isEmpty();
        assertThat(PlainText.fromHtml("<!--".repeat(100_000))).isEmpty();
    }
}
