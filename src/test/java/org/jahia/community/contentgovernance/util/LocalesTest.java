package org.jahia.community.contentgovernance.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocalesTest {

    @Test
    void readsAJahiaLanguageWithARegion() {
        assertThat(Locales.of("zh_CN")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(Locales.of("pt_BR")).isEqualTo(new Locale("pt", "BR"));
        assertThat(Locales.of("en_US")).isEqualTo(Locale.US);
    }

    @Test
    void readsALanguageAlone() {
        assertThat(Locales.of("fr")).isEqualTo(Locale.FRENCH);
        assertThat(Locales.of(" en ")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void readsABcp47TagToo() {
        assertThat(Locales.of("zh-CN")).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(Locales.of("zh-Hans-CN").getScript()).isEqualTo("Hans");
    }

    @Test
    void givesTheRootLocaleForNoLanguage() {
        assertThat(Locales.of(null)).isEqualTo(Locale.ROOT);
        assertThat(Locales.of("  ")).isEqualTo(Locale.ROOT);
    }

    /** What the module did before: the reason a zh_CN page was reported as never published. */
    @Test
    void forLanguageTagLosesAJahiaLanguageWithARegion() {
        assertThat(Locale.forLanguageTag("zh_CN")).isEqualTo(Locale.ROOT);
        assertThat(Locales.of("zh_CN").getCountry()).isEqualTo("CN");
    }
}
