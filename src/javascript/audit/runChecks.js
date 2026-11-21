import {browserChecks, CHECKS_MARK} from '../drawer/entries';
import {tabOf} from './pageCheck';
import {runStep} from './timeLimit';

/**
 * Runs the checks that modules register in the browser (`contentGovernanceCheck`) on the preview of the
 * page. Each check gets the enabled rules of its evaluator that state `runtime: browser`, and gives one
 * result per rule: `{ruleId, passed, values, findings: [{text, context, locator, values}]}`, the shape of
 * the results of the server.
 *
 * Example: the check `axe` gets the rules `axe.image-alt`, `axe.label`... and gives
 * `{ruleId: 'axe.image-alt', passed: false, values: {impact: 'critical'}, findings: [{text: '<img src="chart.png">',
 * locator: 'main > p:nth-child(4) > img'}]}`.
 */

/** The time one check may take before its rules are left out of the audit, in milliseconds. */
export const CHECK_LIMIT_MS = 60_000;

/** The most findings that one result keeps, as on the server (CheckRegistry.MAX_FINDINGS). */
export const MAX_FINDINGS = 50;

/** The value of `runtime` of a rule whose check runs in the browser. */
export const BROWSER = 'browser';

/** The evaluator of the measure of the web vitals, whose values the Web Vitals tab shows. */
export const VITALS = 'webVitals';

const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

/** A finding as a check gave it, with texts only: `text`, and `context`, `locator` and `values` when it has them. */
const findingOf = finding => ({
    text: String(finding?.text ?? ''),
    ...(finding?.context ? {context: String(finding.context)} : {}),
    ...(finding?.locator ? {locator: String(finding.locator)} : {}),
    ...(isObject(finding?.values) ? {values: finding.values} : {})
});

/**
 * The results of a check on the rules it was given. A result of another rule is dropped, and so is a
 * second result of one rule. A result with no `passed` passes when it has no finding.
 *
 * @param {*} value what the check returned
 * @param {Array<{id: string}>} given the rules the check was given
 * @param {string} key the key of the check, for the console
 * @returns {Array<{ruleId: string, passed: boolean, values: object, findings: Array<object>}>} the results
 */
export function resultsOf(value, given, key) {
    if (!Array.isArray(value)) {
        console.warn(`[content-governance] the check "${key}" returned no list of results`);
        return [];
    }
    const ids = new Set(given.map(rule => rule.id));
    const seen = new Set();
    return value.filter(result => isObject(result) && ids.has(result.ruleId) && !seen.has(result.ruleId) && seen.add(result.ruleId))
        .map(result => {
            const findings = Array.isArray(result.findings) ? result.findings : [];
            return {
                ruleId: result.ruleId,
                passed: typeof result.passed === 'boolean' ? result.passed : findings.length === 0,
                values: isObject(result.values) ? result.values : {},
                findings: findings.slice(0, MAX_FINDINGS).map(findingOf)
            };
        });
}

/**
 * The rules that a check of the browser applies: on, stating `runtime: browser`, and of a subdomain that a
 * Page quality tab shows. A rule of another subdomain never runs, as on the server.
 *
 * @param {{all: function(): Array<object>}} rules the rules of the site
 * @returns {Array<object>} the rules
 */
export const browserRules = rules => rules.all().filter(rule => rule.runtime === BROWSER && rule.enabled && tabOf(rule));

/**
 * The rules of the tabs that the site keeps on, for {@link runChecks}: a check of a tab that is off does not
 * run. The measure of the web vitals runs while the Web Vitals tab or the Ecodesign tab is on, since both
 * read it.
 *
 * @param {{all: function(): Array<object>}} rules the rules of the site
 * @param {function(string): boolean} isOn whether the site keeps a tab on
 * @returns {{all: function(): Array<object>}} the rules of the tabs that are on
 */
export const rulesOfTabsOn = (rules, isOn) => ({
    all: () => rules.all().filter(rule => isOn(tabOf(rule)) || (rule.evaluator === VITALS && isOn('vitals')))
});

/**
 * Runs each check of the browser, one after the other, on the preview. A check runs when the site keeps
 * at least one rule of its evaluator on. A check that fails or runs out of time leaves its rules out, and
 * the other checks still run.
 *
 * @param {{contentDocument: Document, contentWindow: Window}} frame the preview
 * @param {{all: function(): Array<object>}} rules the rules of the site
 * @param {string} language the editor's interface language
 * @param {Array<object>} [checks] the checks, those of the registry by default
 * @returns {Promise<{results: Array<object>, incomplete: Object<string, object>, notChecked: Array<string>}>}
 *     the results of every check; `incomplete`, by evaluator, the outcome of each check that did not finish
 *     (`runStep`); `notChecked`, the ids of the rules that no check gave a result for: no module registers
 *     a check of their evaluator, or the check left them out. Never rejects.
 */
export async function runChecks(frame, rules, language, checks = browserChecks()) {
    const document = frame.contentDocument;
    const window = frame.contentWindow;
    const wanted = browserRules(rules);
    try {
        window.performance.mark(CHECKS_MARK);
    } catch {
        // A frame with no performance timeline: a check measures every resource
    }
    const results = new Map();
    const incomplete = {};
    for (const check of checks) {
        const given = wanted.filter(rule => rule.evaluator === check.evaluator);
        if (given.length === 0) {
            continue;
        }
        const controller = new AbortController();
        const outcome = await runStep(() => check.run({document, window, rules: given, language, signal: controller.signal}), CHECK_LIMIT_MS);
        if (outcome.incomplete) {
            controller.abort();
            console.warn(`[content-governance] the check "${check.key}" did not finish`, outcome);
            incomplete[check.evaluator] = outcome;
            continue;
        }
        resultsOf(outcome.value, given, check.key).forEach(result => {
            if (!results.has(result.ruleId)) {
                results.set(result.ruleId, result);
            }
        });
    }
    const notChecked = wanted.filter(rule => !results.has(rule.id) && !incomplete[rule.evaluator]).map(rule => rule.id);
    return {results: [...results.values()], incomplete, notChecked};
}
