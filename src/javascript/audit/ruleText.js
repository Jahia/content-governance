import {toLocaleTag} from '../common/locale';

/**
 * The texts of a finding, from its rule only: every rule, built-in or of another module, is worded the
 * same way. A rule holds, in the editor's interface language (English when it has none):
 *
 * - `title`: the title of a finding, one text per plural form (`one`, `other`...), with values such as
 *   `{{count}}`, `{{value}}`, `{{min}}` and `{{max}}`;
 * - `label`: a neutral name of what the rule checks, such as "Title length";
 * - `advice`: what to do.
 *
 * Example: `html.imageAltMissing` with 2 findings gives "2 images have no text alternative" in English,
 * and "2 images sans alternative textuelle" in French.
 */

const VALUE = /\{\{\s*(\w+)\s*\}\}/g;

/** The text with each `{{name}}` replaced by its value; an unknown name stays as written. */
export const interpolate = (text, values) => (text || '').replaceAll(VALUE, (whole, name) =>
    (values[name] === undefined || values[name] === null ? whole : String(values[name])));

/** The plural form of a count in a language, as `Intl.PluralRules` names it: `one`, `few`, `other`... */
export function pluralForm(count, language) {
    try {
        return new Intl.PluralRules(toLocaleTag(language) || 'en').select(Number(count) || 0);
    } catch {
        return count === 1 ? 'one' : 'other';
    }
}

/**
 * The title of the rule for a count: the text of its plural form, else of `other`, else the first one.
 *
 * @param {Array<{form: string, text: string}>} forms the forms of the title
 * @param {number} count the count
 * @param {string} language the language of the texts
 * @returns {string|null} the text, or null when the rule has no title
 */
export function titleFor(forms, count, language) {
    if (!forms || forms.length === 0) {
        return null;
    }
    const wanted = pluralForm(count, language);
    const form = forms.find(f => f.form === wanted) || forms.find(f => f.form === 'other') || forms[0];
    return form.text;
}

/**
 * The texts of a finding of a rule.
 *
 * @param {{id: string, title: ?Array, label: ?string, advice: ?string}} rule the rule, with its texts in `language`
 * @param {object} values the values of the finding: `count` (how many findings), `value` (what the first one
 *     measured or matched), the limits of the rule, and what an analyzer of the browser measured
 * @param {string} language the editor's interface language
 * @returns {{title: string, label: string, detail: string}} the texts; with no title, the label or the id
 */
export function wordFinding(rule, values, language) {
    const title = titleFor(rule.title, values.count, language);
    const label = rule.label || rule.id;
    return {
        title: interpolate(title || label, values),
        label,
        detail: interpolate(rule.advice || '', values)
    };
}

/**
 * How many results of rules passed, out of all: the score of any list of pass/fail results, such as the
 * checks of GEO readiness.
 *
 * @param {Array<{passed: boolean}>} results the results
 * @returns {{passed: number, total: number}} the counts
 */
export const passedOf = results => ({passed: results.filter(result => result.passed).length, total: results.length});
