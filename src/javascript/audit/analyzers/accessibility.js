// axe-core is shipped as a module static resource (copied by webpack) and
// injected into the preview iframe via <script src>, so it runs in the
// iframe's own realm. axe.source was removed in axe-core 4.x, so inline
// source injection is not an option.
import {moduleUrl} from '../../common/url';

const AXE_URL = moduleUrl('/javascript/apps/axe.min.js');
/** The languages of the interface whose axe-core locale the module ships (webpack.config.js). */
const AXE_LOCALES = new Set(['fr', 'de', 'es']);
/** The prefix of the id of the rule of the catalog of each axe-core check, such as axe.image-alt. */
export const AXE_RULE_PREFIX = 'axe.';

const TAG_SETS = {
    A: ['wcag2a', 'wcag21a'],
    AA: ['wcag2aa', 'wcag21aa', 'wcag22aa'],
    AAA: ['wcag2aaa']
};

const ALL_TAGS = [...TAG_SETS.A, ...TAG_SETS.AA, ...TAG_SETS.AAA, 'best-practice'];

const IMPACT_ORDER = {critical: 0, serious: 1, moderate: 2, minor: 3};

function levelOf(tags) {
    if (tags.some(t => TAG_SETS.AAA.includes(t))) {
        return 'AAA';
    }

    if (tags.some(t => TAG_SETS.AA.includes(t))) {
        return 'AA';
    }

    if (tags.some(t => TAG_SETS.A.includes(t))) {
        return 'A';
    }

    return 'BP';
}

/**
 * One axe-core result, with the rule of the catalog that applies it on the site: `severity` is the
 * severity that the site gives that rule (axe-core's impact is its default), and `title` the title that
 * the site wrote, or null to keep the help text of axe-core.
 */
function mapRule(rule, rules) {
    const catalogId = AXE_RULE_PREFIX + rule.id;
    const siteRule = rules?.find(catalogId);
    const custom = siteRule && ((siteRule.title || []).length > 0 || siteRule.label);
    return {
        id: rule.id,
        rule: siteRule ? catalogId : null,
        impact: rule.impact || 'minor',
        severity: siteRule?.severity || rule.impact || 'minor',
        title: custom ? rules.word(catalogId, {count: rule.nodes.length}).title : null,
        help: rule.help,
        helpUrl: rule.helpUrl,
        description: rule.description,
        level: levelOf(rule.tags),
        nodes: rule.nodes.slice(0, 25).map(n => ({
            target: Array.isArray(n.target) ? n.target.join(' ') : String(n.target),
            html: n.html,
            failureSummary: n.failureSummary
        })),
        totalNodes: rule.nodes.length
    };
}

function injectAxe(frame) {
    const win = frame.contentWindow;
    const doc = frame.contentDocument;

    if (win.axe) {
        return Promise.resolve(win.axe);
    }

    return new Promise((resolve, reject) => {
        const script = doc.createElement('script');
        script.src = AXE_URL;
        script.onload = () => {
            if (win.axe) {
                resolve(win.axe);
            } else {
                reject(new Error('axe-core failed to initialize in the preview frame'));
            }
        };

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
 * The axe-core checks that the site turned off, as the option `rules` of `axe.run` takes them. A check
 * turned off does not run, as in Deque's axe and in Lighthouse: it gives no violation and no item to
 * review, and the level scores leave it out.
 */
export function disabledChecks(rules) {
    if (!rules?.all) {
        return {};
    }
    return Object.fromEntries(rules.all()
        .filter(rule => rule.id.startsWith(AXE_RULE_PREFIX) && !rule.enabled)
        .map(rule => [rule.id.slice(AXE_RULE_PREFIX.length), {enabled: false}]));
}

/**
 * Injects axe-core into the (same-origin) preview iframe and runs the full
 * WCAG A / AA / AAA + best-practice rule set against its document, minus the checks that the site turned
 * off. Each check is the rule `axe.<id>` of the catalog.
 *
 * @param {HTMLIFrameElement} frame the preview
 * @param {object} [rules] the rules of the site (`ruleBook`)
 * @param {string} [language] the editor's interface language, for the help texts of axe-core
 */
export async function runAccessibility(frame, rules, language) {
    const win = frame.contentWindow;
    const doc = frame.contentDocument;

    if (!win || !doc) {
        throw new Error('Preview frame is not accessible');
    }

    await injectAxe(frame);
    await localize(win.axe, language);

    const raw = await win.axe.run(doc, {
        runOnly: {type: 'tag', values: ALL_TAGS},
        rules: disabledChecks(rules),
        resultTypes: ['violations', 'passes', 'incomplete', 'inapplicable']
    });

    const violations = raw.violations.map(rule => mapRule(rule, rules))
        .sort((a, b) => (IMPACT_ORDER[a.severity] ?? 4) - (IMPACT_ORDER[b.severity] ?? 4));
    const incomplete = raw.incomplete.map(rule => mapRule(rule, rules));
    const passLevels = raw.passes.map(p => levelOf(p.tags));

    const summary = {};
    ['A', 'AA', 'AAA', 'BP'].forEach(level => {
        summary[level] = {
            violations: violations.filter(v => v.level === level).length,
            passes: passLevels.filter(l => l === level).length,
            incomplete: incomplete.filter(v => v.level === level).length
        };
    });

    return {
        summary,
        violations,
        incomplete,
        passCount: raw.passes.length,
        rulesRun: raw.violations.length + raw.passes.length + raw.incomplete.length +
            (raw.inapplicable ? raw.inapplicable.length : 0),
        engine: `axe-core ${raw.testEngine ? raw.testEngine.version : ''}`.trim()
    };
}
