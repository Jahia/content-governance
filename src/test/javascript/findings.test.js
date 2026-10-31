import {describe, expect, it} from 'vitest';
import {auditFindings, complianceFindings, rankFindings} from '../../javascript/drawer/findings.js';

const t = (key, params) => `${key}${params ? ` ${JSON.stringify(params)}` : ''}`;

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
    it('lists the accessibility violations, then the recommendations of each tab', () => {
        const findings = auditFindings({
            a11y: {violations: [{id: 'image-alt', impact: 'critical', help: 'Images must have alternative text'}]},
            seo: {recommendations: [{key: 'shortTitle', severity: 'moderate', params: {count: 12}}]},
            ecodesign: {recommendations: [{key: 'heavyPage', severity: 'serious'}]}
        }, t);
        expect(findings).toEqual([
            {key: 'audit.a11y.image-alt', section: 'audit', tab: 'accessibility', severity: 'critical', title: 'Images must have alternative text'},
            {key: 'audit.seo.shortTitle', section: 'audit', tab: 'seo', severity: 'moderate', title: 'seo.recs.shortTitle.title {"count":12}'},
            {key: 'audit.ecodesign.heavyPage', section: 'audit', tab: 'ecodesign', severity: 'serious', title: 'eco.recs.heavyPage.title'}
        ]);
    });

    it('has nothing without results', () => {
        expect(auditFindings(null, t)).toEqual([]);
    });
});

describe('complianceFindings', () => {
    it('gives one serious finding per term, with its number of uses', () => {
        const findings = complianceFindings({issues: [{term: 'click here'}, {term: 'leverage'}, {term: 'click here'}]}, t);
        expect(findings.map(x => x.title)).toEqual(['fixFirst.term {"term":"click here","count":2}', 'fixFirst.term {"term":"leverage","count":1}']);
        expect(findings.every(x => x.severity === 'serious' && x.section === 'compliance')).toBe(true);
    });
});
