import {limits} from './rules';
import {tabOf} from './pageCheck';
import {browserRules, VITALS} from './runChecks';

/**
 * The results of the page check from the results of the checks of the browser (runChecks.js): what each view
 * shows. A failed result of any rule becomes a recommendation of the results of its subdomain, worded by its
 * rule. Two evaluators also feed a view of their own: `axe` the WCAG level cards and violations of the
 * Accessibility view, and `webVitals` the figures of the Web Vitals view.
 */

/** The evaluator of axe-core, whose violations the Accessibility tab lists with their elements. */
export const AXE = 'axe';

/**
 * The key of each tab in the results. `other` holds the recommendations of the rules of the subdomains with
 * no view of the page check (audit/subdomainResults.js).
 */
export const RESULT_KEYS = {
    accessibility: 'a11y',
    seo: 'seo',
    vitals: 'vitals',
    readability: 'readability',
    ecodesign: 'ecodesign',
    links: 'links',
    jahia: 'jahia',
    other: 'other'
};

/** The tabs whose results the check of the server gives, by their key in the results. */
export const SERVER_KEYS = ['seo', 'readability', 'links', 'jahia', 'other'];

/** The tabs that list recommendations of rules. */
const RECOMMENDATION_TABS = ['accessibility', 'seo', 'readability', 'links', 'jahia', 'ecodesign', 'other'];
const LEVELS = ['A', 'AA', 'AAA', 'BP'];
const IMPACT_ORDER = {critical: 0, serious: 1, moderate: 2, minor: 3};

const evaluatorIs = (rules, evaluator) => result => rules.find(result.ruleId)?.evaluator === evaluator;

/**
 * The values of the texts of a result: the limits of its rule, `count` (its findings), `value` (the text of
 * its first finding), and each value of the result that is a text, a number or a flag, such as `size`.
 *
 * @param {{values: object, findings: Array<{text: string}>}} result a result of a check
 * @param {object} rule its rule
 * @returns {object} the values
 */
export function paramsOf(result, rule) {
    const scalars = Object.fromEntries(Object.entries(result.values).filter(([, value]) => value === null || typeof value !== 'object'));
    return {...limits(rule), count: result.findings.length, ...(result.findings[0] ? {value: result.findings[0].text} : {}), ...scalars};
}

/**
 * The recommendations of the failed results, by tab: one per rule, worded by its rule, with each finding
 * in `items`. The violations of axe-core are left out: the Accessibility tab lists them with their elements.
 *
 * @param {Array<object>} results the results of the checks
 * @param {{find: function(string): ?object, word: function(string, object): object}} rules the rules of the site
 * @returns {Object<string, Array<object>>} by tab id
 */
export function recommendationsOf(results, rules) {
    const out = Object.fromEntries(RECOMMENDATION_TABS.map(tab => [tab, []]));
    results.filter(result => !result.passed).forEach(result => {
        const rule = rules.find(result.ruleId);
        const tab = rule && rule.evaluator !== AXE ? tabOf(rule) : null;
        if (!out[tab]) {
            return;
        }
        const params = paramsOf(result, rule);
        out[tab].push({
            key: result.ruleId,
            rule: result.ruleId,
            severity: rule.severity,
            params,
            ...(result.findings.length > 0 ? {items: result.findings.map(({text, context, locator}) => ({text, ...(context ? {context} : {}), ...(locator ? {locator} : {})}))} : {}),
            ...rules.word(result.ruleId, params)
        });
    });
    return out;
}

/** A check of axe-core as the Accessibility tab lists it, with the severity and the title that the site gives its rule. */
function axeCheck(result, rules) {
    const {values} = result;
    const rule = rules.find(result.ruleId);
    const custom = rule && ((rule.title || []).length > 0 || rule.label);
    return {
        id: values.axeRule || result.ruleId,
        rule: result.ruleId,
        impact: values.impact || 'minor',
        severity: rule?.severity || values.impact || 'minor',
        title: custom ? rules.word(result.ruleId, {count: values.nodes}).title : null,
        help: values.help,
        helpUrl: values.helpUrl,
        description: values.description,
        level: values.level || 'BP',
        nodes: result.findings.map(finding => ({target: finding.locator, html: finding.text, failureSummary: finding.context})),
        totalNodes: values.nodes ?? result.findings.length
    };
}

/**
 * The view of axe-core in the Accessibility tab: the WCAG level cards, the violations (most severe first),
 * the checks to review by hand, and the engine.
 *
 * @param {Array<object>} results the results of the checks
 * @param {object} rules the rules of the site
 * @returns {{summary: object, violations: Array<object>, incomplete: Array<object>, passCount: number, rulesRun: number, engine: string}} the view
 */
export function accessibilityOf(results, rules) {
    const axe = results.filter(evaluatorIs(rules, AXE));
    const outcome = name => axe.filter(result => (result.passed ? result.values.outcome === name : name === 'violation'));
    const violations = outcome('violation').map(result => axeCheck(result, rules))
        .sort((a, b) => (IMPACT_ORDER[a.severity] ?? 4) - (IMPACT_ORDER[b.severity] ?? 4));
    const incomplete = outcome('incomplete').map(result => axeCheck(result, rules));
    const passes = outcome('pass');
    const summary = Object.fromEntries(LEVELS.map(level => [level, {
        violations: violations.filter(check => check.level === level).length,
        passes: passes.filter(result => (result.values.level || 'BP') === level).length,
        incomplete: incomplete.filter(check => check.level === level).length
    }]));
    return {summary, violations, incomplete, passCount: passes.length, rulesRun: axe.length, engine: axe[0]?.values.engine || ''};
}

/**
 * The view of the Web Vitals tab: the figures of the measure of the web vitals, or none when the site turned
 * its rule off. Its findings are rules of the subdomain ecodesign, which the Ecodesign tab shows.
 *
 * @param {Array<object>} results the results of the checks
 * @param {object} rules the rules of the site
 * @returns {{recommendations: Array, metrics?: object, diagnostics?: object}} the view
 */
export function vitalsOf(results, rules) {
    const measure = results.find(evaluatorIs(rules, VITALS));
    return {...(measure?.values.metrics ? {metrics: measure.values.metrics, diagnostics: measure.values.diagnostics} : {}), recommendations: []};
}

/**
 * The view of the Ecodesign tab: each result of its tab that names its RGESN family is a criterion, and the
 * cards read the figures of the web vitals.
 *
 * @param {Array<object>} results the results of the checks
 * @param {object} rules the rules of the site
 * @param {?object} diagnostics what the measure of the web vitals found, or null
 * @returns {{criteria: Array<object>, passed: number, total: number, stats: ?object}} the view
 */
export function ecodesignOf(results, rules, diagnostics) {
    const criteria = results.filter(result => result.values.family && tabOf(rules.find(result.ruleId)) === 'ecodesign')
        .map(result => ({
            key: result.ruleId,
            label: rules.word(result.ruleId, {}).label,
            family: result.values.family,
            ok: result.passed,
            value: String(result.values.display ?? '')
        }));
    return {
        criteria,
        passed: criteria.filter(criterion => criterion.ok).length,
        total: criteria.length,
        stats: diagnostics ? {totalBytes: diagnostics.totalBytes, requests: diagnostics.requests, thirdParty: diagnostics.thirdParty} : null
    };
}

/** The rules of some ids by the tab of their subdomain, each with its label: `{rule, label}`. */
function labelsByTab(ids, rules) {
    const out = {};
    ids.forEach(id => {
        const tab = tabOf(rules.find(id));
        if (tab) {
            out[tab] = [...(out[tab] || []), {rule: id, label: rules.word(id, {}).label}];
        }
    });
    return out;
}

/**
 * What the checks of the browser give each Page quality tab.
 *
 * @param {{results: Array<object>, incomplete: Object<string, object>, notChecked: Array<string>}} run what runChecks gave
 * @param {object} rules the rules of the site
 * @returns {{accessibility: ?object, vitals: ?object, ecodesign: object, recommendations: Object<string, Array<object>>,
 *     incomplete: Object<string, object>, notChecked: Object<string, Array<{rule: string, label: string}>>}} by tab id:
 *     the views of axe-core and of the web vitals (null when their check did not finish), the recommendations, the
 *     outcome of each tab that a check did not finish, and the rules that no check gave a result for
 */
export function browserTabs({results, incomplete, notChecked}, rules) {
    const unfinished = {};
    browserRules(rules).filter(rule => incomplete[rule.evaluator]).forEach(rule => {
        unfinished[tabOf(rule)] = incomplete[rule.evaluator];
    });
    if (incomplete[VITALS]) {
        unfinished.vitals = incomplete[VITALS];
    }
    const vitals = incomplete[VITALS] ? null : vitalsOf(results, rules);
    return {
        accessibility: incomplete[AXE] ? null : accessibilityOf(results, rules),
        vitals,
        ecodesign: ecodesignOf(results, rules, vitals?.diagnostics || null),
        recommendations: recommendationsOf(results, rules),
        incomplete: unfinished,
        notChecked: labelsByTab(notChecked, rules)
    };
}

/**
 * The results of each tab: those of the server, with the recommendations of the checks of the browser of
 * the subdomain of the tab, and the rules of the tab that no check of the browser gave a result for. One rule
 * gives one recommendation, so a rule that the server and the browser both report shows once.
 */
export function withBrowserResults(checked, browser) {
    const unique = recommendations => {
        const seen = new Set();
        return recommendations.filter(rec => !seen.has(rec.rule) && seen.add(rec.rule));
    };
    const measured = {
        // The rules of the subdomain accessibility that the server checks go with the checks of axe-core
        a11y: browser.accessibility && {...browser.accessibility,
            recommendations: [...(checked?.accessibility || []), ...browser.recommendations.accessibility]},
        vitals: browser.vitals,
        ecodesign: {...browser.ecodesign, recommendations: unique([...browser.recommendations.ecodesign, ...(checked?.ecodesign || [])])},
        ...Object.fromEntries(SERVER_KEYS.filter(key => checked?.[key]).map(key => [key, {
            ...checked[key],
            recommendations: unique([...checked[key].recommendations, ...browser.recommendations[key]])
        }]))
    };
    Object.entries(browser.notChecked).forEach(([tab, rules]) => {
        const key = RESULT_KEYS[tab];
        if (measured[key]) {
            measured[key] = {...measured[key], notChecked: rules};
        }
    });
    return measured;
}
