package org.jahia.community.contentgovernance.text;

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
    void aPlainValueReadsBackFromItsHtml() {
        String value = "  Prices < 10 EUR &\n <b>bold</b> stays &amp; text ";

        assertThat(PlainText.fromHtml(PlainText.toHtml(value))).isEqualTo(PlainText.collapse(value))
                .isEqualTo("Prices < 10 EUR & <b>bold</b> stays &amp; text");
    }

    @Test
    void unclosedScriptsAndCommentsDropTheRestOfTheValue() {
        assertThat(PlainText.fromHtml("Visible <script>never closed")).isEqualTo("Visible");
        assertThat(PlainText.fromHtml("Visible <!-- never closed")).isEqualTo("Visible");
    }

    @Test
    void aLessThanSignThatNoTagCloses_isText() {
        assertThat(PlainText.fromHtml("Prices < 10 EUR, click here")).isEqualTo("Prices < 10 EUR, click here");
        assertThat(PlainText.fromHtml("<b>Prices</b> < 10 EUR")).isEqualTo("Prices < 10 EUR");
    }

    @Test
    void closingTagsOfScriptsAreMatchedWhateverTheCase() {
        assertThat(PlainText.fromHtml("a<SCRIPT type=x>bad()</Script >b")).isEqualTo("a b");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void hostileValuesAreReadInLinearTime() {
        // With no > anywhere, no tag starts, so the value is text
        assertThat(PlainText.fromHtml("<".repeat(400_000))).hasSize(400_000);
        assertThat(PlainText.fromHtml("<script ".repeat(50_000))).startsWith("<script <script");
        assertThat(PlainText.fromHtml("<p ".repeat(100_000))).startsWith("<p <p");
        assertThat(PlainText.fromHtml("<!--".repeat(100_000))).isEmpty();
        assertThat(PlainText.fromHtml("<a>".repeat(100_000) + "<".repeat(100_000))).hasSize(100_000);
    }
}
