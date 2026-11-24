import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {governanceDomains, rulePlaces} from '../../javascript/drawer/layout.js';
import {checksOf, COMPLIANCE, GEO, PAGE, sectionElementId, viewOf} from '../../javascript/drawer/subdomains.js';
import {domainFindings, sectionsOf} from '../../javascript/drawer/sections.js';
import {pageStateOf, placeOf, recommendationsIn} from '../../javascript/audit/subdomainResults.js';
import {geoStateOf} from '../../javascript/geo/geoState.js';
import {complianceStateOf, termPlaceOf} from '../../javascript/compliance/complianceState.js';
import {GovernanceSections} from '../../javascript/drawer/GovernancePanel.jsx';

vi.mock('@jahia/data-helper', () => ({useNodeChecks: () => ({checksResult: true, loading: false})}));

const h = React.createElement;
const t = (key, params) => `${key}${params ? JSON.stringify(params) : ''}`;

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
});

/** The layout that content-governance ships, as `contentDomains` gives it, with the domain of another panel. */
const SHIPPED = [
    {id: 'quality', label: 'Page quality', panel: 'governance', subdomains: [
        {id: 'health', label: 'Jahia health'}, {id: 'links', label: 'Links'}, {id: 'accessibility', label: 'Accessibility'},
        {id: 'vitals', label: 'Web Vitals'}, {id: 'ecodesign', label: 'Ecodesign'}]},
    {id: 'geo', label: 'SEO & GEO', panel: 'governance', subdomains: [
        {id: 'geo.citations', label: 'AI citations'}, {id: 'seo', label: 'SEO'}, {id: 'geo.score', label: 'Score'},
        {id: 'geo.content', label: 'Content'}, {id: 'geo.files', label: 'Site files'}, {id: 'geo.schema', label: 'Structured data'},
        {id: 'geo.crawlers', label: 'Crawler access'}]},
    {id: 'compliance', label: 'Compliance', panel: 'governance', subdomains: [
        {id: 'readability', label: 'Readability'}, {id: 'terms', label: 'Terms'}, {id: 'glossary', label: 'Glossary'},
        {id: 'writing', label: 'Writing'}]},
    {id: 'acme.chat', label: 'Chat', panel: 'chat', subdomains: [{id: 'acme.answers', label: 'Answers'}]},
    {id: 'acme.empty', label: 'Empty', panel: 'governance', subdomains: []}
];

/** The rules of a site: `seo.canonical` moved to `links` by the layout of the site, `seo.indexable` switched off. */
const RULES = [
    {id: 'seo.title', subdomain: 'seo', evaluator: 'threshold', enabled: true},
    {id: 'seo.canonical', subdomain: 'links', evaluator: 'seo.canonical', enabled: true},
    {id: 'seo.indexable', subdomain: 'seo', evaluator: 'seo.indexable', enabled: false},
    {id: 'links.broken', subdomain: 'links', evaluator: 'links.broken', enabled: true},
    {id: 'html.imageAltMissing', subdomain: 'accessibility', evaluator: 'html.imageAltMissing', enabled: true},
    {id: 'axe.image-alt', subdomain: 'accessibility', evaluator: 'axe', enabled: true},
    {id: 'acme.tone', subdomain: 'writing', evaluator: 'model', enabled: true},
    {id: 'geo.dateModified', subdomain: 'geo.content', evaluator: 'geo.dateModified', enabled: true},
    {id: 'brand.seamless', subdomain: 'terms', evaluator: 'pattern', enabled: true},
    {id: 'brand.synergy', subdomain: 'writing', evaluator: 'pattern', enabled: true},
    {id: 'brand.none', subdomain: null, evaluator: 'pattern', enabled: true}
];

const places = rulePlaces(RULES);
const domains = governanceDomains(SHIPPED);

/** An audit that holds `seo.canonical` under `seo`, as it ran before the site moved the rule to `links`. */
const RESULTS = {
    page: {schema: 7},
    a11y: {violations: [{id: 'image-alt', rule: 'axe.image-alt', severity: 'critical'}],
        recommendations: [{key: 'html.imageAltMissing', rule: 'html.imageAltMissing', severity: 'moderate', title: 'No alt'}]},
    seo: {recommendations: [{key: 'seo.title', rule: 'seo.title', severity: 'moderate', title: 'Title'},
        {key: 'seo.canonical', rule: 'seo.canonical', severity: 'serious', title: 'Canonical'}]},
    links: {recommendations: []},
    jahia: {recommendations: [{key: 'gone.rule', rule: 'gone.rule', severity: 'minor', title: 'Gone'}]},
    other: {recommendations: [{key: 'acme.tone', rule: 'acme.tone', severity: 'minor', title: 'Tone'}]},
    readability: {recommendations: []},
    ecodesign: {recommendations: []},
    vitals: {metrics: {}, recommendations: []}
};

const report = {
    published: true,
    blockedCount: 2,
    score: {criticalFailed: 1, checks: [
        {id: 'dateModified', rule: 'geo.dateModified', passed: false, severity: 'important'},
        {id: 'fetched', rule: 'geo.fetched', passed: false, severity: 'critical'}
    ]},
    siteFiles: {llms: {present: true}, robots: {present: true}},
    schema: {mapped: false}
};

const issue = (ruleId, term) => ({ruleId, term, severity: 'serious', nodeUuid: `node-${term}`});
const engines = (change = {}) => ({
    page: {ready: true, results: RESULTS},
    geo: {report: null, citationsState: 'loading'},
    compliance: {result: {}},
    ...change
});

describe('the layout of the site', () => {
    it('keeps the domains of the governance panel that have a subdomain on, in their order', () => {
        expect(domains.map(domain => domain.id)).toEqual(['quality', 'geo', 'compliance']);
        expect(governanceDomains(undefined)).toEqual([]);
    });

    it('places each rule in its subdomain on the site, and leaves a rule that is off out of its subdomain', () => {
        expect(places.subdomainOf('seo.canonical')).toBe('links');
        expect(places.subdomainOf('unknown')).toBeUndefined();
        expect(places.rulesIn('links').map(rule => rule.id)).toEqual(['seo.canonical', 'links.broken']);
        expect(places.rulesIn('seo').map(rule => rule.id)).toEqual(['seo.title']);
        expect(places.rulesIn('nowhere')).toEqual([]);
    });
});

describe('what feeds a subdomain', () => {
    it('gives each dedicated view its check, and none to a subdomain that lists findings', () => {
        expect(['accessibility', 'vitals', 'health', 'geo.score', 'geo.citations', 'terms', 'writing', 'geo.content', 'acme.prices'].map(viewOf))
            .toEqual([PAGE, PAGE, PAGE, GEO, GEO, COMPLIANCE, null, null, null]);
    });

    it('needs the check of each view, the page check for a list of findings, and the scan for a term rule placed there', () => {
        const [quality, geo, compliance] = domains;
        expect([...checksOf(quality, places)]).toEqual([PAGE]);
        expect([...checksOf(geo, places)].sort()).toEqual([GEO, PAGE].sort());
        expect([...checksOf(compliance, places)].sort()).toEqual([COMPLIANCE, PAGE].sort());
        expect([...checksOf({subdomains: [{id: 'geo.score'}]}, places)]).toEqual([GEO]);
    });

    it('gives a section an element id that a CSS selector reads', () => {
        expect(sectionElementId('geo.score')).toBe('content-governance-subdomain-geo-score');
        expect(sectionElementId('links')).toBe('content-governance-subdomain-links');
    });
});

describe('the findings of the page check by subdomain', () => {
    it('shows a recommendation in the subdomain of its rule on the site, whatever results hold it', () => {
        expect(recommendationsIn(RESULTS, 'links', places.subdomainOf).map(rec => [rec.rule, rec.bucket]))
            .toEqual([['seo.canonical', 'seo']]);
        expect(recommendationsIn(RESULTS, 'seo', places.subdomainOf).map(rec => rec.rule)).toEqual(['seo.title']);
        expect(recommendationsIn(RESULTS, 'writing', places.subdomainOf).map(rec => rec.rule)).toEqual(['acme.tone']);
    });

    it('keeps a recommendation of a rule the site no longer has in the subdomain of its results', () => {
        expect(placeOf('gone.rule', 'jahia', places.subdomainOf)).toBe('health');
        expect(placeOf(null, 'a11y', places.subdomainOf)).toBe('accessibility');
        expect(placeOf('gone.rule', 'other', places.subdomainOf)).toBeNull();
    });

    it('counts the violations of axe-core with the accessibility section, and nothing for a subdomain not measured', () => {
        expect(pageStateOf(RESULTS, 'accessibility', places.subdomainOf)).toEqual({count: 2, serious: true});
        expect(pageStateOf(RESULTS, 'links', places.subdomainOf)).toEqual({count: 1, serious: true});
        expect(pageStateOf({page: {}, incomplete: {seo: {incomplete: 'timedOut'}}}, 'seo', places.subdomainOf))
            .toEqual({count: null, serious: false});
        expect(pageStateOf(RESULTS, 'writing', places.subdomainOf)).toEqual({count: 1, serious: false});
    });
});

describe('the findings of the GEO check and the compliance scan by subdomain', () => {
    it('counts a GEO view on the report of a published page only', () => {
        expect(geoStateOf({report}, 'geo.score', new Set())).toEqual({count: 1, serious: true, unavailable: false});
        expect(geoStateOf({report}, 'geo.crawlers', new Set()).count).toBe(2);
        expect(geoStateOf({report: null}, 'geo.score', new Set()).count).toBeNull();
        expect(geoStateOf({report: {...report, published: false}}, 'geo.files', new Set()).count).toBeNull();
        expect(geoStateOf({citationsState: 'notConfigured'}, 'geo.citations', new Set())).toEqual({count: null, serious: false, unavailable: true});
    });

    it('counts the checks of the score that failed on the rules placed in a subdomain with no GEO view', () => {
        expect(geoStateOf({report}, 'geo.content', new Set(['geo.dateModified']))).toEqual({count: 1, serious: false, unavailable: false});
    });

    it('places a term in the subdomain of its rule, terms for a rule with none', () => {
        expect(termPlaceOf('brand.synergy', places.subdomainOf)).toBe('writing');
        expect(termPlaceOf('brand.none', places.subdomainOf)).toBe('terms');
        const scan = {result: {report: {issues: [issue('brand.seamless', 'seamless'), issue('brand.synergy', 'synergy'), issue('brand.none', 'none')]}}};
        expect(complianceStateOf(scan, 'terms', places.subdomainOf)).toEqual({count: 2, serious: true});
        expect(complianceStateOf(scan, 'writing', places.subdomainOf).count).toBe(1);
        expect(complianceStateOf({result: {loading: true}}, 'terms', places.subdomainOf).count).toBeNull();
    });
});

describe('sectionsOf', () => {
    it('gives each subdomain of a domain its count, in the order of the layout', () => {
        const [quality] = domains;
        expect(sectionsOf(quality, engines(), places, null).map(section => [section.id, section.count]))
            .toEqual([['health', 1], ['links', 1], ['accessibility', 2], ['vitals', 0], ['ecodesign', 0]]);
    });

    it('gives a GEO view no count before the check, and counts a rule of the page check that the site placed there', () => {
        const geo = domains[1];
        const moved = rulePlaces(RULES.map(rule => (rule.id === 'seo.title' ? {...rule, subdomain: 'geo.score'} : rule)));
        const before = Object.fromEntries(sectionsOf(geo, engines(), places, null).map(section => [section.id, section.count]));
        expect(before).toMatchObject({'geo.score': null, 'geo.crawlers': null, 'geo.content': 0, 'geo.citations': null, seo: 1});
        const after = Object.fromEntries(sectionsOf(geo, engines({geo: {report}}), moved, null).map(section => [section.id, section.count]));
        expect(after).toMatchObject({'geo.score': 2, 'geo.crawlers': 2, 'geo.content': 1, seo: 0});
    });

    it('counts the terms of a subdomain, and none where the editor cannot use the scan', () => {
        const compliance = domains[2];
        const scan = {result: {report: {issues: [issue('brand.seamless', 'seamless'), issue('brand.synergy', 'synergy')]}}};
        const counts = unavailable => Object.fromEntries(sectionsOf(compliance, engines({compliance: scan}), places, unavailable)
            .map(section => [section.id, section.count]));
        expect(counts(null)).toEqual({readability: 0, terms: 1, glossary: 0, writing: 2});
        expect(counts('noWrite')).toEqual({readability: 0, terms: null, glossary: null, writing: 1});
    });
});

describe('domainFindings', () => {
    it('names the domain and the subdomain of each finding, in the order of the layout', () => {
        const scan = {result: {report: {issues: [issue('brand.synergy', 'synergy')]}}};
        const found = domainFindings(domains, engines({geo: {report}, compliance: scan}), places, null, {audit: t, geo: t, compliance: t});

        expect(found.quality.map(finding => [finding.tab, finding.key])).toEqual([
            ['health', 'audit.jahia.gone.rule'],
            ['links', 'audit.seo.seo.canonical'],
            ['accessibility', 'audit.a11y.image-alt'],
            ['accessibility', 'audit.a11y.html.imageAltMissing']
        ]);
        expect(found.quality.every(finding => finding.section === 'quality')).toBe(true);
        expect(found.geo.map(finding => [finding.tab, finding.key])).toEqual([
            ['seo', 'audit.seo.seo.title'], ['geo.score', 'geo.score'], ['geo.content', 'geo.content'], ['geo.crawlers', 'geo.crawlers']
        ]);
        expect(found.compliance.map(finding => [finding.section, finding.tab, finding.key]))
            .toEqual([['compliance', 'writing', 'audit.other.acme.tone'], ['compliance', 'writing', 'brand.synergy']]);
    });

    it('leaves out a finding of a subdomain that the site does not show', () => {
        const [quality] = domains;
        const found = domainFindings([{...quality, subdomains: quality.subdomains.filter(subdomain => subdomain.id !== 'links')}],
            engines(), places, null, {audit: t, geo: t, compliance: t});
        expect(found.quality.map(finding => finding.tab)).not.toContain('links');
    });
});

describe('GovernanceSections with the layout of the site', () => {
    const drawerProps = {path: '/sites/digitall/home', language: 'en', settings: {}, isOn: () => true, onClose: () => {}};
    const sectionTabs = markup => [...markup.matchAll(/id="content-governance-section-([^"]+)"/g)].map(match => match[1]);
    const entry = id => ({id, entry: {key: id, label: id, component: () => h('p', null, `the tab ${id}`)}});

    it('shows the domains in the order of the layout, then the tabs of other modules, and leaves out a tab with the id of a domain', () => {
        const markup = renderToStaticMarkup(h(GovernanceSections, {...drawerProps, domains, places, tabs: [entry('aiReview'), entry('geo')]}));
        expect(sectionTabs(markup)).toEqual(['quality', 'geo', 'compliance', 'aiReview']);
    });

    it('opens on the domain of a subdomain that the request names, with the section of the subdomain', () => {
        const markup = renderToStaticMarkup(h(GovernanceSections, {...drawerProps, domains, places, tabs: [], request: {section: 'terms'}}));
        expect(markup).toContain('id="content-governance-section-compliance" role="tab" aria-selected="true"');
        expect(markup).toContain('id="content-governance-subdomain-terms"');
        expect(markup).not.toContain('id="content-governance-subdomain-links"');
    });
});
