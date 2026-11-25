/**
 * The result of a rule that shows its values rather than a card: `view: fact` shows them as label and value lines
 * (a FactBlock), and `view: score` as tiles (a ScoreBlock). The title is the title of the rule, worded by its
 * values, and each value is one row, labelled by the `valueLabels` of the rule, else by its key. A rule of the
 * server and a rule of the browser show the same way.
 *
 * Example: `acme.readingTime` with `view: fact` and the values `{words: 640, minutes: 3}` shows the rows
 * "Words 640" and "Reading time 3".
 */

import {limits} from './rules';

export const CARD = 'card';
export const FACT = 'fact';
export const SCORE = 'score';

/** The severities from the most serious. */
const RANK = ['critical', 'serious', 'moderate', 'minor'];

const isScalar = value => value === null || ['string', 'number', 'boolean'].includes(typeof value);

/**
 * @param {*} value a value of a result
 * @returns {string} its text: a scalar as is, a list of scalars joined, anything else as JSON
 */
export function valueText(value) {
    if (value === null || value === undefined) {
        return '';
    }
    if (isScalar(value)) {
        return String(value);
    }
    if (Array.isArray(value) && value.every(isScalar)) {
        return value.join(', ');
    }
    return JSON.stringify(value);
}

/**
 * The band of a score tile: `good` inside the limits of the rule, `poor` outside, and none for a value that is no
 * number or a rule with no limit.
 *
 * @param {*} value the value
 * @param {{minimum: ?number, maximum: ?number}} rule the rule
 * @returns {(string|undefined)} the band
 */
export function bandOf(value, rule) {
    const hasMin = typeof rule.minimum === 'number';
    const hasMax = typeof rule.maximum === 'number';
    if (typeof value !== 'number' || (!hasMin && !hasMax)) {
        return undefined;
    }
    return (!hasMin || value >= rule.minimum) && (!hasMax || value <= rule.maximum) ? 'good' : 'poor';
}

/**
 * The most serious severity of the findings of a result, which a check may give over the severity of its rule.
 *
 * @param {Array<{severity: ?string}>} findings the findings
 * @param {string} fallback the severity of the rule
 * @returns {string} the severity
 */
export function severityOf(findings, fallback) {
    const ranks = (findings || []).map(finding => RANK.indexOf(finding.severity)).filter(rank => rank >= 0);
    return ranks.length > 0 ? RANK[Math.min(...ranks)] : fallback;
}

/**
 * The values of the texts of a result: the limits of its rule, `count` (its findings), `value` (the text of
 * its first finding), and each value of the result that is a text, a number or a flag, such as `size`.
 *
 * @param {{values: object, findings: Array<{text: string}>}} result a result of a check
 * @param {object} rule its rule
 * @returns {object} the values
 */
export function paramsOf(result, rule) {
    const scalars = Object.fromEntries(Object.entries(result.values || {}).filter(([, value]) => value === null || typeof value !== 'object'));
    return {...limits(rule), count: result.findings.length, ...(result.findings[0] ? {value: result.findings[0].text} : {}), ...scalars};
}

/**
 * One occurrence of a recommendation, from a finding: its `text`, and its `context`, `locator` and `values` when it
 * has them, such as the `replacement` of a term or the `fix` that a model proposes.
 *
 * @param {{text: string, context: ?string, locator: ?string, values: ?object}} finding the finding
 * @returns {object} the occurrence
 */
export const itemOf = ({text, context, locator, values}) => ({
    text,
    ...(context ? {context} : {}),
    ...(locator ? {locator} : {}),
    ...(values && Object.keys(values).length > 0 ? {values} : {})
});

/**
 * The rows of the values of a result: the labelled values first, in the order of the labels, then the others.
 *
 * @param {object} values the values of the result
 * @param {{view: string, valueLabels: ?Array<{key: string, label: string}>, minimum: ?number, maximum: ?number}} rule the rule
 * @returns {Array<{key: string, label: string, value: string, band: (string|undefined)}>} the rows
 */
export function rowsOf(values, rule) {
    const labels = new Map((rule.valueLabels || []).map(label => [label.key, label.label]));
    const keys = [...[...labels.keys()].filter(key => key in values), ...Object.keys(values).filter(key => !labels.has(key))];
    return keys.map(key => ({
        key,
        label: labels.get(key) || key,
        value: valueText(values[key]),
        ...(rule.view === SCORE && bandOf(values[key], rule) ? {band: bandOf(values[key], rule)} : {})
    }));
}

/**
 * The views of the results whose rule shows as facts or as scores, in the order of the results.
 *
 * @param {Array<{ruleId: string, values: object, findings: Array<object>}>} results the results of the checks
 * @param {{find: function(string): ?object, word: function(string, object): object}} rules the rules of the site
 * @param {function(object, object): object} params the values of the texts of a result and its rule
 * @returns {Array<{key: string, rule: string, view: string, title: string, items: Array<object>}>} the views
 */
export function ruleViewsOf(results, rules, params) {
    return results.flatMap(result => {
        const rule = rules.find(result.ruleId);
        if (!rule || (rule.view !== FACT && rule.view !== SCORE)) {
            return [];
        }
        return [{
            key: result.ruleId,
            rule: result.ruleId,
            view: rule.view,
            title: rules.word(result.ruleId, params(result, rule)).title,
            items: rowsOf(result.values || {}, rule)
        }];
    });
}
