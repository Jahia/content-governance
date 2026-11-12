/**
 * A Jahia language as the browser's formatters take it. Jahia writes locales the Java way
 * (`zh_CN`, `pt_BR`, `sr_RS_#Latn`); `Intl` and `toLocaleString` take a BCP 47 tag (`zh-CN`) and
 * throw `RangeError: Invalid language tag` on anything else, which took the drawer down on a site
 * in Chinese (China).
 *
 * @param {string} [language] a Jahia language, or a tag already
 * @returns {string|undefined} the canonical tag, or undefined (the browser's own language) when
 *     there is none or it cannot be read
 */
export function toLocaleTag(language) {
    if (!language) {
        return undefined;
    }
    const [base, script] = String(language).split('_#');
    const parts = base.split('_').filter(Boolean);
    if (script) {
        parts.splice(1, 0, script);
    }
    try {
        return Intl.getCanonicalLocales(parts.join('-'))[0];
    } catch {
        return undefined;
    }
}

/**
 * A language's name in the editor's language (`zh_CN` in English: "Chinese (China)"), else the
 * code as given.
 *
 * @param {string} code the language to name
 * @param {string} [uiLanguage] the editor's language
 * @param {string} [fallback] what to show when the browser cannot name it (default: the code)
 * @returns {string}
 */
export function languageDisplayName(code, uiLanguage, fallback = code) {
    const tag = toLocaleTag(code);
    if (!tag) {
        return fallback;
    }
    try {
        return new Intl.DisplayNames([toLocaleTag(uiLanguage) || 'en'], {type: 'language'}).of(tag) || fallback;
    } catch {
        return fallback;
    }
}
