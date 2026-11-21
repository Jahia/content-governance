import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {browserChecks, CHECK_TYPE, CHECKS_MARK} from '../../javascript/drawer/entries.js';
import {ECODESIGN_EVALUATORS, registerChecks} from '../../javascript/checks/register.js';
import {MEASURES} from '../../javascript/checks/ecodesign.js';
import {browserRules, CHECK_LIMIT_MS, MAX_FINDINGS, resultsOf, rulesOfTabsOn, runChecks} from '../../javascript/audit/runChecks.js';
import {browserTabs} from '../../javascript/audit/browserResults.js';
import {resultOf} from '../../javascript/checks/axe.js';
import {ruleBook} from '../../javascript/audit/rules.js';
import {BASE_RULES} from './baseRules.js';

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
    vi.useRealTimers();
});

/** A preview with no page: what runChecks gives a check, and the marks it set. */
const frame = () => {
    const marks = [];
    return {
        marks,
        contentDocument: {title: 'preview'},
        contentWindow: {performance: {mark: name => marks.push(name)}}
    };
};

/** A rule of the browser of another module, such as the contrast of the brand colors. */
const brand = {id: 'acme.brandContrast', evaluator: 'acme.contrast', runtime: 'browser', domain: 'accessibility', severity: 'serious',
    label: 'Contrast of the brand colors', title: [{form: 'one', text: '{{count}} element uses {{color}} on white'},
        {form: 'other', text: '{{count}} elements use {{color}} on white'}]};

const rulesWith = (...extra) => ruleBook([...BASE_RULES.all(), ...extra]);

describe('browserChecks', () => {
    it('leaves out a check with no evaluator or no run function', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        const run = () => [];
        registry.add(CHECK_TYPE, 'good', {evaluator: 'acme.contrast', run});
        registry.add(CHECK_TYPE, 'noEvaluator', {run});
        registry.add(CHECK_TYPE, 'noRun', {evaluator: 'acme.contrast'});

        expect(browserChecks().map(entry => entry.key)).toEqual(['good']);
        expect(console.warn).toHaveBeenCalledTimes(2);
    });

    it('holds a check of Content Governance for the evaluator of each rule of the browser that it ships', () => {
        registerChecks();
        const registered = new Set(browserChecks().map(entry => entry.evaluator));

        expect(browserRules(BASE_RULES).filter(rule => !registered.has(rule.evaluator)).map(rule => rule.id)).toEqual([]);
        expect(ECODESIGN_EVALUATORS.toSorted()).toEqual(Object.keys(MEASURES).toSorted());
        expect(browserRules(BASE_RULES).map(rule => rule.evaluator)).toContain('webVitals');
    });
});

describe('runChecks', () => {
    it('gives each check the enabled rules of its evaluator that run in the browser, and keeps its results on them', async () => {
        const rules = rulesWith(brand, {...brand, id: 'acme.off', enabled: false}, {...brand, id: 'acme.server', runtime: 'server'});
        const run = vi.fn(async ({rules: given}) => [
            {ruleId: 'acme.brandContrast', passed: false, values: {color: '#ffcc00'}, findings: [{text: '<h1>', locator: 'h1', extra: 1}]},
            {ruleId: 'seo.title', passed: false, findings: []},
            {ruleId: 'acme.brandContrast', passed: true},
            ...given.map(() => null)
        ]);
        const preview = frame();

        const outcome = await runChecks(preview, rules, 'fr', [{key: 'acme', evaluator: 'acme.contrast', run}]);

        expect(run).toHaveBeenCalledTimes(1);
        const context = run.mock.calls[0][0];
        expect(context.rules.map(rule => rule.id)).toEqual(['acme.brandContrast']);
        expect(context.document).toBe(preview.contentDocument);
        expect(context.language).toBe('fr');
        expect(context.signal.aborted).toBe(false);
        expect(preview.marks).toEqual([CHECKS_MARK]);
        expect(outcome.results).toEqual([{ruleId: 'acme.brandContrast', passed: false, values: {color: '#ffcc00'},
            findings: [{text: '<h1>', locator: 'h1'}]}]);
        expect(outcome.incomplete).toEqual({});
    });

    it('lists as not checked a rule whose evaluator no check has, or that its check left out', async () => {
        const rules = rulesWith(brand, {...brand, id: 'acme.logo', evaluator: 'acme.logo'}, {...brand, id: 'acme.second'});
        const outcome = await runChecks(frame(), rules, 'en', [
            {key: 'acme', evaluator: 'acme.contrast', run: () => [{ruleId: 'acme.brandContrast', passed: true}]}
        ]);

        expect(outcome.notChecked.filter(id => id.startsWith('acme.'))).toEqual(['acme.logo', 'acme.second']);
        expect(outcome.notChecked).toContain('axe.image-alt');
    });

    it('goes on after a check that fails, and keeps its rules out of the list of rules not checked', async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        const rules = rulesWith(brand, {...brand, id: 'acme.logo', evaluator: 'acme.logo'});
        const outcome = await runChecks(frame(), rules, 'en', [
            {key: 'broken', evaluator: 'acme.contrast', run: () => {
                throw new Error('no colors');
            }},
            {key: 'logo', evaluator: 'acme.logo', run: async () => [{ruleId: 'acme.logo', passed: true}]}
        ]);

        expect(outcome.incomplete).toEqual({'acme.contrast': {incomplete: 'failed', message: 'no colors'}});
        expect(outcome.results.map(result => result.ruleId)).toEqual(['acme.logo']);
        expect(outcome.notChecked).not.toContain('acme.brandContrast');
    });

    it('leaves out a check that runs out of time, aborts it, and still runs the next check', async () => {
        vi.useFakeTimers();
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        let signal;
        const rules = rulesWith(brand, {...brand, id: 'acme.logo', evaluator: 'acme.logo'});
        const running = runChecks(frame(), rules, 'en', [
            {key: 'hung', evaluator: 'acme.contrast', run: context => {
                signal = context.signal;
                return new Promise(() => {});
            }},
            {key: 'logo', evaluator: 'acme.logo', run: async () => [{ruleId: 'acme.logo', passed: true}]}
        ]);
        await vi.advanceTimersByTimeAsync(CHECK_LIMIT_MS);
        const outcome = await running;
        vi.useRealTimers();

        expect(outcome.incomplete).toEqual({'acme.contrast': {incomplete: 'timedOut', seconds: CHECK_LIMIT_MS / 1000}});
        expect(signal.aborted).toBe(true);
        expect(outcome.results.map(result => result.ruleId)).toEqual(['acme.logo']);
        expect(outcome.notChecked).not.toContain('acme.brandContrast');
    });

    it('runs no check of a tab that the site switched off, and the web vitals while one of their two tabs is on', async () => {
        const vitals = {id: 'ecodesign.webVitals', evaluator: 'webVitals', runtime: 'browser', domain: 'ecodesign', severity: 'minor'};
        const rules = ruleBook([brand, vitals]);
        const run = vi.fn(async ({rules: given}) => given.map(rule => ({ruleId: rule.id, passed: true})));
        const checks = [{key: 'acme', evaluator: 'acme.contrast', run}, {key: 'webVitals', evaluator: 'webVitals', run}];
        const ran = async onTabs => (await runChecks(frame(), rulesOfTabsOn(rules, tab => onTabs.includes(tab)), 'en', checks))
            .results.map(result => result.ruleId);

        expect(await ran(['seo', 'ecodesign'])).toEqual(['ecodesign.webVitals']);
        expect(await ran(['vitals'])).toEqual(['ecodesign.webVitals']);
        expect(await ran(['accessibility'])).toEqual(['acme.brandContrast']);
        expect((await runChecks(frame(), rulesOfTabsOn(rules, () => false), 'en', checks)).notChecked).toEqual([]);
    });

    it('keeps the first 50 findings of a result, and passes a result with no finding and no verdict', () => {
        const many = Array.from({length: 80}, (_, i) => ({text: `x${i}`}));
        const [first, second] = resultsOf([{ruleId: 'a', passed: false, findings: many}, {ruleId: 'b'}], [{id: 'a'}, {id: 'b'}], 'test');

        expect(first.findings).toHaveLength(MAX_FINDINGS);
        expect(second).toEqual({ruleId: 'b', passed: true, values: {}, findings: []});
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        expect(resultsOf('nothing', [{id: 'a'}], 'test')).toEqual([]);
    });
});

describe('browserTabs', () => {
    const rules = rulesWith(brand);
    const pageWeight = {ruleId: 'ecodesign.pageWeight', passed: false,
        values: {family: 'frontend', size: '3.0 MB', bytes: 3 * 1024 * 1024, display: '3.00 MB'}, findings: [{text: 'hero.jpg', context: '2100 KB'}]};
    const vitals = {ruleId: 'ecodesign.webVitals', passed: true,
        values: {metrics: {lcp: 1200}, diagnostics: {totalBytes: 3 * 1024 * 1024, requests: 40, thirdParty: 2}}, findings: []};
    const imageAlt = resultOf({id: 'image-alt', impact: 'critical', tags: ['wcag2a'], help: 'Images must have alternative text',
        nodes: [{html: '<img src="chart.png">', target: ['main', 'img'], failureSummary: 'Fix: add alt'}]}, 'violation', 'axe-core 4.14.0');
    const label = resultOf({id: 'label', impact: 'serious', tags: ['wcag2aa'], nodes: [{html: '<input>', target: ['input']}]}, 'pass', 'axe-core 4.14.0');
    const run = {
        results: [pageWeight, vitals, imageAlt, label,
            {ruleId: 'acme.brandContrast', passed: false, values: {color: '#ffcc00'}, findings: [{text: '<h1>', locator: 'h1'}, {text: '<h2>', locator: 'h2'}]}],
        incomplete: {},
        notChecked: ['ecodesign.autoplay', 'acme.unknown']
    };

    it('words each failed result by its rule, with the values of the result, in the tab of its domain', () => {
        const tabs = browserTabs(run, rules);

        expect(tabs.recommendations.ecodesign.map(rec => [rec.rule, rec.title, rec.items])).toEqual([
            ['ecodesign.pageWeight', 'The page weighs 3.0 MB (limit 1024 KB)', [{text: 'hero.jpg', context: '2100 KB'}]]
        ]);
        expect(tabs.recommendations.accessibility.map(rec => [rec.rule, rec.severity, rec.title])).toEqual([
            ['acme.brandContrast', 'serious', '2 elements use #ffcc00 on white']
        ]);
    });

    it('gives the Accessibility tab the checks of axe-core, under their rule, with the severity of the site', () => {
        const {accessibility} = browserTabs(run, rulesWith(brand, {...BASE_RULES.get('axe.image-alt'), severity: 'serious'}));

        expect(accessibility.violations).toEqual([expect.objectContaining({id: 'image-alt', rule: 'axe.image-alt', impact: 'critical',
            severity: 'serious', level: 'A', totalNodes: 1, nodes: [{target: 'main img', html: '<img src="chart.png">', failureSummary: 'Fix: add alt'}]})]);
        expect(accessibility.summary.AA).toEqual({violations: 0, passes: 1, incomplete: 0});
        expect([accessibility.passCount, accessibility.rulesRun, accessibility.engine]).toEqual([1, 2, 'axe-core 4.14.0']);
    });

    it('gives the Web Vitals tab the figures of its measure, and the Ecodesign tab its criteria', () => {
        const tabs = browserTabs(run, rules);

        expect(tabs.vitals).toEqual({metrics: {lcp: 1200}, diagnostics: vitals.values.diagnostics, recommendations: []});
        expect(tabs.ecodesign).toEqual({
            criteria: [{key: 'ecodesign.pageWeight', label: 'Page weight', family: 'frontend', ok: false, value: '3.00 MB'}],
            passed: 0,
            total: 1,
            stats: {totalBytes: 3 * 1024 * 1024, requests: 40, thirdParty: 2}
        });
    });

    it('lists the rules not checked in the tab of their domain, with their label', () => {
        expect(browserTabs(run, rules).notChecked).toEqual({ecodesign: [{rule: 'ecodesign.autoplay', label: 'No autoplay'}]});
    });

    it('marks the tabs of a check that did not finish, and gives no view of axe-core or the vitals then', () => {
        const timedOut = {incomplete: 'timedOut', seconds: 60};
        const tabs = browserTabs({...run, incomplete: {axe: timedOut, webVitals: timedOut}}, rules);

        expect(tabs.incomplete).toEqual({accessibility: timedOut, ecodesign: timedOut, vitals: timedOut});
        expect(tabs.accessibility).toBeNull();
        expect(tabs.vitals).toBeNull();
        expect(tabs.ecodesign.stats).toBeNull();
    });
});
