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

/** A day such as 2026-11-25, which a date reads at midday UTC so that it stays the same day in every time zone. */
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const asDate = value => {
    if (value instanceof Date) {
        return value;
    }
    return new Date(DAY.test(value) ? `${value}T12:00:00Z` : value);
};
const isMissing = value => value === null || value === undefined || value === '';

/**
 * A date and its time in the language of the interface of the editor: "25/11/2026 14:02:52" in French,
 * "11/25/2026, 2:02:52 PM" in English. Every date of the drawer goes through it or through `formatDay`.
 *
 * @param {string|number|Date} value an ISO date, a timestamp or a date
 * @param {string} [uiLanguage] the language of the interface, such as `fr`
 * @returns {string} the value as given when it is not a date, and an empty text for no value
 */
export function formatDateTime(value, uiLanguage) {
    if (isMissing(value)) {
        return '';
    }
    const date = asDate(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString(toLocaleTag(uiLanguage));
}

/**
 * A day in the language of the interface of the editor: "25/11/2026" in French, or "25 nov." with
 * `{day: 'numeric', month: 'short'}`.
 *
 * @param {string|number|Date} value an ISO date, a day such as 2026-11-25, a timestamp or a date
 * @param {string} [uiLanguage] the language of the interface, such as `fr`
 * @param {Intl.DateTimeFormatOptions} [options] the parts of the day to show, else the whole day
 * @returns {string} the value as given when it is not a date, and an empty text for no value
 */
export function formatDay(value, uiLanguage, options) {
    if (isMissing(value)) {
        return '';
    }
    const date = asDate(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString(toLocaleTag(uiLanguage), options);
}
