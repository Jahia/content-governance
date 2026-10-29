package org.jahia.community.contentgovernance.compliance.service.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcerptTest {

    @Test
    void shortTextIsKeptWhole() {
        String text = "Livraison gratuite partout";
        Excerpt e = Excerpt.around(text, 10, 18);
        assertThat(e.before()).isEqualTo("Livraison ");
        assertThat(e.match()).isEqualTo("gratuite");
        assertThat(e.after()).isEqualTo(" partout");
    }

    @Test
    void longTextIsCutAtWordBoundaries() {
        String text = "word ".repeat(30) + "TERM" + " tail".repeat(30);
        int start = text.indexOf("TERM");
        Excerpt e = Excerpt.around(text, start, start + 4);
        assertThat(e.before()).startsWith("…word").endsWith("word ");
        assertThat(e.after()).startsWith(" tail").endsWith("tail…");
        assertThat(e.before().length()).isLessThanOrEqualTo(61);
    }
}
