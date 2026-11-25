import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {bandOf, rowsOf, ruleViewsOf, paramsOf, severityOf} from '../../javascript/audit/ruleViews.js';
import {parsedRun, runFindings, runStateOf, shownRun} from '../../javascript/audit/manualRuns.js';
import {browserRules} from '../../javascript/audit/runChecks.js';
import {ruleBook} from '../../javascript/audit/rules.js';
import {applicableFixers, drawerFixers, FIXER_TYPE} from '../../javascript/drawer/entries.js';
import {API_TYPE, registerResultPatterns, RESULTS_API_KEY} from '../../javascript/drawer/drawerApi.js';
import {RuleViews} from '../../javascript/common/results/RuleViews.jsx';
import {Recommendations} from '../../javascript/audit/tabs/Recommendations.jsx';
import {DrawerEntryContext} from '../../javascript/drawer/DrawerEntryContext.js';

const h = React.createElement;

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
});

/** A rule of another module that shows its values as facts, and one that runs on demand in the browser. */
const RULES = ruleBook([
    {id: 'acme.reading', subdomain: 'writing', evaluator: 'acme.reading', severity: 'minor', view: 'fact',
        title: [{form: 'other', text: 'Reading'}], valueLabels: [{key: 'minutes', label: 'Reading time'}, {key: 'words', label: 'Words'}]},
    {id: 'acme.score', subdomain: 'writing', evaluator: 'acme.score', severity: 'minor', view: 'score', minimum: 60, label: 'Tone'},
    {id: 'acme.tone', subdomain: 'writing', evaluator: 'model', severity: 'minor', trigger: 'manual', title: [{form: 'other', text: 'Tone of the page'}]},
    {id: 'acme.alt', subdomain: 'writing', evaluator: 'acme.alt', severity: 'minor', runtime: 'browser', trigger: 'manual'},
    {id: 'acme.contrast', subdomain: 'writing', evaluator: 'acme.alt', severity: 'minor', runtime: 'browser'}
]);

describe('the views of the rules that show their values', () => {
    it('labels each value by the rule, labelled values first, and keeps the key of a value with no label', () => {
        expect(rowsOf({words: 640, minutes: 3, lang: 'en'}, RULES.get('acme.reading'))).toEqual([
            {key: 'minutes', label: 'Reading time', value: '3'},
            {key: 'words', label: 'Words', value: '640'},
            {key: 'lang', label: 'lang', value: 'en'}
        ]);
    });

    it('bands a score by the limits of its rule, and gives no band without a limit or a number', () => {
        expect(bandOf(72, {minimum: 60, maximum: null})).toBe('good');
        expect(bandOf(40, {minimum: 60, maximum: null})).toBe('poor');
        expect(bandOf(40, {minimum: null, maximum: null})).toBeUndefined();
        expect(bandOf('40', {minimum: 60, maximum: null})).toBeUndefined();
    });

    it('gives a view per result whose rule shows as facts or scores, for any check', () => {
        const views = ruleViewsOf([
            {ruleId: 'acme.reading', passed: true, values: {words: 640}, findings: []},
            {ruleId: 'acme.score', passed: true, values: {tone: 72}, findings: []},
            {ruleId: 'acme.tone', passed: false, values: {}, findings: [{text: 'x'}]}
        ], RULES, paramsOf);

        expect(views).toEqual([
            {key: 'acme.reading', rule: 'acme.reading', view: 'fact', title: 'Reading', items: [{key: 'words', label: 'Words', value: '640'}]},
            {key: 'acme.score', rule: 'acme.score', view: 'score', title: 'Tone', items: [{key: 'tone', label: 'tone', value: '72', band: 'good'}]}
        ]);
        const markup = renderToStaticMarkup(h(RuleViews, {views}));
        expect(markup).toContain('Reading');
        expect(markup).toContain('640');
    });

    it('takes the most serious severity of the findings over the severity of the rule', () => {
        expect(severityOf([{severity: 'moderate'}, {severity: 'serious'}, {}], 'minor')).toBe('serious');
        expect(severityOf([{}], 'minor')).toBe('minor');
    });
});

describe('the runs of the rules on demand', () => {
    const run = {auditedAt: '2026-11-25T10:00:00Z', auditedBy: 'jane', results: JSON.stringify({results: [
        {ruleId: 'acme.tone', passed: false, values: {}, findings: [{ruleId: 'acme.tone', severity: 'serious', text: 'Hey you', values: {fix: 'Hello'}}]},
        {ruleId: 'acme.reading', passed: true, values: {words: 640}, findings: []}
    ]})};

    it('reads a stored run, and counts its failed rules with the severity of their findings', () => {
        const parsed = parsedRun(run);

        expect(parsed.by).toBe('jane');
        expect(parsed.at).toBe(Date.parse('2026-11-25T10:00:00Z'));
        expect(runStateOf(parsed)).toEqual({count: 1, serious: true});
        expect(runStateOf(null)).toEqual({count: null, serious: false});
        expect(parsedRun({auditedAt: 'x', results: 'not json'})).toBeNull();
    });

    it('words a run with the rules of the site, as cards, facts and findings for "Fix first"', () => {
        const parsed = parsedRun(run);
        const shown = shownRun(parsed.results, RULES);

        expect(shown.recommendations).toHaveLength(1);
        expect(shown.recommendations[0]).toMatchObject({rule: 'acme.tone', severity: 'serious', title: 'Tone of the page',
            items: [{text: 'Hey you', values: {fix: 'Hello'}}]});
        expect(shown.views.map(view => view.rule)).toEqual(['acme.reading']);
        expect(runFindings(parsed, 'writing', RULES)).toEqual([{key: 'audit.manual.writing.acme.tone', tab: 'writing', rule: 'acme.tone',
            severity: 'serious', title: 'Tone of the page', count: 1}]);
    });

    it('runs a rule on demand of the browser only in a run on demand', () => {
        expect(browserRules(RULES).map(rule => rule.id)).toEqual(['acme.contrast']);
        expect(browserRules(RULES, true).map(rule => rule.id)).toEqual(['acme.alt']);
    });
});

describe('the fixers of other modules', () => {
    const rewrite = {label: 'acme:fixers.rewrite', appliesTo: ({rule}) => rule.evaluator === 'model', run: async () => ({proposals: []})};

    it('keeps a fixer with a label, appliesTo and run, and applies it where appliesTo answers true', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        registry.add(FIXER_TYPE, 'rewrite', rewrite);
        registry.add(FIXER_TYPE, 'noApplies', {label: 'x', run: rewrite.run});
        registry.add(FIXER_TYPE, 'throws', {label: 'y', run: rewrite.run, appliesTo: () => {
            throw new Error('boom');
        }});
        const fixers = drawerFixers();

        expect(fixers.map(fixer => fixer.key)).toEqual(['rewrite', 'throws']);
        expect(applicableFixers(fixers, RULES.get('acme.tone'), {}).map(fixer => fixer.key)).toEqual(['rewrite']);
        expect(applicableFixers(fixers, RULES.get('acme.reading'), {})).toEqual([]);
    });

    it('shows a fixer that applies on the finding and on each occurrence, with the fix that the check proposes', () => {
        registry.add(FIXER_TYPE, 'rewrite', rewrite);
        const rec = {key: 'acme.tone', rule: 'acme.tone', severity: 'minor', title: 'Tone of the page', detail: 'Be friendly.',
            items: [{text: 'Hey you', values: {fix: 'Hello'}}, {text: 'Yo'}]};
        const markup = renderToStaticMarkup(h(DrawerEntryContext.Provider, {value: {path: '/sites/a/home', language: 'en', findings: [],
            actions: [], ruleOf: id => RULES.find(id), previewDocument: () => null}}, h(Recommendations, {items: [rec], ns: 'recs', tab: 'other'})));

        expect(markup.match(/data-fixer="rewrite"/g)).toHaveLength(3);
        expect(markup).toContain('Hello');
    });
});

describe('the result patterns', () => {
    it('gives other modules the result patterns of the drawer', () => {
        registerResultPatterns();

        expect(registry.get(API_TYPE, RESULTS_API_KEY)).toEqual(expect.objectContaining({
            FactBlock: expect.any(Function), PassedFold: expect.any(Function), ResultBanner: expect.any(Function),
            ResultCard: expect.any(Function), ScoreBlock: expect.any(Function)
        }));
    });
});
