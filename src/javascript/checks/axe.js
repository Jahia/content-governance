// axe-core is shipped as a module static resource (copied by webpack) and
// injected into the preview iframe via <script src>, so it runs in the
// iframe's own realm. axe.source was removed in axe-core 4.x, so inline
// source injection is not an option.
import {moduleUrl} from '../common/url';

/**
 * The check `axe`: axe-core on the preview. A rule of this evaluator names one check of axe-core by its id,
 * `axe.<axe-core rule id>`, such as `axe.image-alt`. Only the checks of the enabled rules run: a check that
 * the site turned off gives no violation and no item to review, as in Deque's axe and in Lighthouse.
 *
 * Each result carries in `values`: `outcome` (`violation`, `pass`, `incomplete` or `inapplicable`), the
 * `impact`, the WCAG `level` (A, AA, AAA, or BP for a best practice), the `help`, `helpUrl` and
 * `description` of axe-core in the editor's language, `nodes` (how many elements fail or need a review),
 * `axeRule` (the id of the check of axe-core) and `engine`. A check that axe-core cannot decide passes,
 * with the outcome `incomplete`: the Accessibility tab lists it to review by hand. Each finding is one
 * element: its HTML as `text`, what fails as `context`, and its selector as `locator`.
 */

const AXE_URL = moduleUrl('/javascript/apps/axe.min.js');
/** The languages of the interface whose axe-core locale the module ships (webpack.config.js). */
const AXE_LOCALES = new Set(['fr', 'de', 'es']);
/** The prefix of the id of the rule of each check of axe-core, such as axe.image-alt. */
export const AXE_RULE_PREFIX = 'axe.';
/** The most elements of one check that a result lists. */
const MAX_NODES = 25;

const TAG_SETS = {
    A: ['wcag2a', 'wcag21a'],
    AA: ['wcag2aa', 'wcag21aa', 'wcag22aa'],
    AAA: ['wcag2aaa']
};

/** The WCAG level of the tags of a check of axe-core, or BP for a best practice. */
export function levelOf(tags) {
    for (const level of ['AAA', 'AA', 'A']) {
        if (tags.some(tag => TAG_SETS[level].includes(tag))) {
            return level;
        }
    }
    return 'BP';
}

function injectAxe(win, doc) {
    if (win.axe) {
        return Promise.resolve(win.axe);
    }
    return new Promise((resolve, reject) => {
        const script = doc.createElement('script');
        script.src = AXE_URL;
        script.onload = () => (win.axe ? resolve(win.axe) : reject(new Error('axe-core failed to initialize in the preview frame')));
        script.onerror = () => reject(new Error('Could not load axe-core into the preview frame'));
        doc.head.appendChild(script);
    });
}

/** Gives axe-core the texts of the editor's language, when the module ships its locale. */
async function localize(axe, language) {
    const base = (language || 'en').split(/[_-]/)[0];
    if (!AXE_LOCALES.has(base)) {
        return;
    }
    try {
        const response = await fetch(moduleUrl(`/javascript/apps/axe-locales/${base}.json`));
        if (response.ok) {
            axe.configure({locale: await response.json()});
        }
    } catch (e) {
        console.warn('[content-governance] the axe-core locale could not be read', e);
    }
}

/**
 * The result of one check of axe-core, under its rule.
 *
 * @param {object} check a check of the answer of `axe.run`
 * @param {string} outcome violation, pass, incomplete or inapplicable
 * @param {string} engine the name and the version of axe-core
 * @returns {{ruleId: string, passed: boolean, values: object, findings: Array<object>}} the result
 */
export function resultOf(check, outcome, engine) {
    const nodes = check.nodes || [];
    return {
        ruleId: AXE_RULE_PREFIX + check.id,
        passed: outcome !== 'violation',
        values: {
            outcome,
            axeRule: check.id,
            impact: check.impact || 'minor',
            level: levelOf(check.tags || []),
            help: check.help,
            helpUrl: check.helpUrl,
            description: check.description,
            nodes: outcome === 'violation' || outcome === 'incomplete' ? nodes.length : 0,
            engine
        },
        findings: outcome === 'violation' || outcome === 'incomplete' ? nodes.slice(0, MAX_NODES).map(node => ({
            text: node.html,
            ...(node.failureSummary ? {context: node.failureSummary} : {}),
            locator: Array.isArray(node.target) ? node.target.join(' ') : String(node.target)
        })) : []
    };
}

/**
 * Injects axe-core into the (same-origin) preview and runs the checks of the given rules on its document.
 * A rule whose check this version of axe-core does not have gives no result.
 *
 * @param {{document: Document, window: Window, rules: Array<{id: string}>, language: string}} context what the
 *     drawer gives a check; `language` is the editor's interface language, for the texts of axe-core
 * @returns {Promise<Array<object>>} one result per rule that axe-core checked
 */
export async function runAxe({document, window, rules, language}) {
    const axe = await injectAxe(window, document);
    await localize(axe, language);
    const known = new Set(axe.getRules().map(rule => rule.ruleId));
    const ids = rules.map(rule => rule.id.slice(AXE_RULE_PREFIX.length)).filter(id => known.has(id));
    if (ids.length === 0) {
        return [];
    }
    const raw = await axe.run(document, {
        runOnly: {type: 'rule', values: ids},
        resultTypes: ['violations', 'passes', 'incomplete', 'inapplicable']
    });
    const engine = `axe-core ${raw.testEngine ? raw.testEngine.version : ''}`.trim();
    // axe-core lists a check in each group that one of its elements falls in: a check with one failing
    // element fails, and one with an element to review needs a review
    const byRule = new Map();
    [['violation', raw.violations], ['incomplete', raw.incomplete], ['pass', raw.passes], ['inapplicable', raw.inapplicable || []]]
        .forEach(([outcome, checks]) => checks.forEach(check => {
            if (!byRule.has(check.id)) {
                byRule.set(check.id, resultOf(check, outcome, engine));
            }
        }));
    return [...byRule.values()];
}
