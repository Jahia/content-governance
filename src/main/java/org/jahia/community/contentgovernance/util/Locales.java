package org.jahia.community.contentgovernance.util;

import org.jahia.utils.LanguageCodeConverters;

import java.util.Locale;

/**
 * The Java locale of a language as the module receives it.
 *
 * <p>Jahia writes a language the Java way, {@code zh_CN}: it is what jContent sends, what a site lists
 * and what a translation node is named after. {@link Locale#forLanguageTag(String)} reads BCP 47 only
 * ({@code zh-CN}) and answers the empty locale for {@code zh_CN} without failing: a JCR session opened
 * with it finds no translation, and in {@code live} no page at all, so a published page was reported
 * as never published on every site language with a region (1.10.0 and before).</p>
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class Locales {

    private Locales() {
    }

    /**
     * @param language a Jahia language code ({@code fr}, {@code zh_CN}) or a BCP 47 tag ({@code zh-CN})
     * @return its locale, converted as Jahia itself does for a Jahia code; {@link Locale#ROOT} for a
     *         missing or blank language
     */
    public static Locale of(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ROOT;
        }
        String code = language.trim();
        if (code.indexOf('-') >= 0 && code.indexOf('_') < 0) {
            return Locale.forLanguageTag(code);
        }
        return LanguageCodeConverters.languageCodeToLocale(code);
    }
}
