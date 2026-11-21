import {describe, expect, it} from 'vitest';
import {auditFindings, complianceFindings, rankFindings} from '../../javascript/drawer/findings.js';

const t = (key, params) => (params ? `${key} ${JSON.stringify(params)}` : key);

describe('rankFindings', () => {
    const f = (key, severity) => ({key, severity});

    it('puts the most serious first and keeps five', () => {
        const ranked = rankFindings([f('a', 'minor'), f('b', 'critical'), f('c', 'moderate'), f('d', 'serious'),
            f('e', 'serious'), f('g', 'minor'), f('h', 'critical')]);
        expect(ranked.map(x => x.key)).toEqual(['b', 'h', 'd', 'e', 'c']);
    });

    it('keeps the order of findings of equal severity, and puts an unknown severity last', () => {
        expect(rankFindings([f('x', 'other'), f('y', 'minor'), f('z', 'minor')], 3).map(x => x.key)).toEqual(['y', 'z', 'x']);
    });
});

describe('auditFindings', () => {
    it('lists the accessibility violations, then the recommendations of each tab, with the id of their rule (an older audit)', () => {
        const findings = auditFindings({
            a11y: {violations: [{id: 'image-alt', impact: 'critical', help: 'Images must have alternative text',
                description: 'Ensure <img> elements have alternative text', totalNodes: 3}]},
            seo: {recommendations: [{key: 'shortTitle', rule: 'seo.title', severity: 'moderate', params: {count: 12}}]},
            ecodesign: {recommendations: [{key: 'heavyPage', severity: 'serious'}]}
        }, t);
        expect(findings).toEqual([
            {key: 'audit.a11y.image-alt', section: 'audit', tab: 'accessibility', severity: 'critical', rule: null,
                title: 'Images must have alternative text', detail: 'Ensure <img> elements have alternative text', count: 3},
            {key: 'audit.seo.shortTitle', section: 'audit', tab: 'seo', severity: 'moderate', rule: 'seo.title', title: 'seo.recs.shortTitle.title {"count":12}',
                detail: 'seo.recs.shortTitle.detail {"count":12}'},
            {key: 'audit.ecodesign.heavyPage', section: 'audit', tab: 'ecodesign', severity: 'serious', rule: null, title: 'eco.recs.heavyPage.title',
                detail: 'eco.recs.heavyPage.detail'}
        ]);
    });

    it('gives an axe-core violation the rule of the catalog, its site severity and the title the site wrote', () => {
        const [violation, rec] = auditFindings({
            a11y: {violations: [{id: 'image-alt', rule: 'axe.image-alt', impact: 'critical', severity: 'serious', title: null,
                help: 'Images must have alternative text', description: 'Ensure <img> elements have alternative text', totalNodes: 1}],
            recommendations: [{key: 'html.headingEmpty', rule: 'html.headingEmpty', severity: 'minor', title: '1 heading is empty', detail: 'Fill it.'}]}
        }, t);
        expect(violation).toMatchObject({rule: 'axe.image-alt', severity: 'serious', title: 'Images must have alternative text'});
        expect(rec).toMatchObject({key: 'audit.a11y.html.headingEmpty', rule: 'html.headingEmpty', title: '1 heading is empty', detail: 'Fill it.'});
    });

    it('has nothing without results', () => {
        expect(auditFindings(null, t)).toEqual([]);
    });
});

describe('complianceFindings', () => {
    it('gives one finding per rule, keyed by the rule id, with its number of uses and the severity of the rule', () => {
        const findings = complianceFindings({issues: [
            {ruleId: 'compliance.click-here', severity: 'serious', term: 'click here'},
            {ruleId: 'writing.leverage', severity: 'moderate', term: 'leverage'},
            {ruleId: 'compliance.click-here', severity: 'serious', term: 'click here'}
        ]}, t);
        expect(findings.map(x => x.key)).toEqual(['compliance.click-here', 'writing.leverage']);
        expect(findings.map(x => x.title)).toEqual(['fixFirst.term {"term":"click here","count":2}', 'fixFirst.term {"term":"leverage","count":1}']);
        expect(findings.map(x => x.severity)).toEqual(['serious', 'moderate']);
        expect(findings.every(x => x.section === 'compliance')).toBe(true);
    });
});
