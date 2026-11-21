import {describe, expect, it} from 'vitest';
import {tabCounts} from '../../javascript/audit/tabCounts.js';
import {withoutTimingFindings} from '../../javascript/audit/vitalsFindings.js';
import {auditFindings, isSerious} from '../../javascript/drawer/findings.js';

const t = key => key;

describe('tabCounts', () => {
    it('counts the findings of each measured tab', () => {
        const counts = tabCounts({
            a11y: {violations: [{id: 'image-alt', impact: 'critical'}, {id: 'region', impact: 'moderate'}]},
            seo: {recommendations: [{key: 'noDescription', severity: 'moderate'}]},
            links: {recommendations: []}
        });
        expect(counts.accessibility.count).toBe(2);
        expect(counts.seo.count).toBe(1);
        expect(counts.links.count).toBe(0);
        expect(counts.vitals).toBeUndefined();
    });

    it('marks a tab serious only when a finding is critical or serious', () => {
        const counts = tabCounts({
            a11y: {violations: [{id: 'region', impact: 'moderate'}, {id: 'image-alt', impact: 'critical'}]},
            seo: {recommendations: [{key: 'a', severity: 'moderate'}, {key: 'b', severity: 'minor'}]},
            readability: {recommendations: [{key: 'h1Count', severity: 'serious'}]}
        });
        expect(counts.accessibility.serious).toBe(true);
        expect(counts.seo.serious).toBe(false);
        expect(counts.readability.serious).toBe(true);
    });

    it('gives nothing before an audit', () => {
        expect(tabCounts(null)).toEqual({});
    });
});

describe('isSerious', () => {
    it('is true for critical and serious, false for the rest', () => {
        expect(['critical', 'serious', 'moderate', 'minor', undefined].map(isSerious)).toEqual([true, true, false, false, false]);
    });
});

describe('Web Vitals findings', () => {
    it('drops the timing findings of an audit stored before 1.11, on the tab and in the lists', () => {
        const stored = [{key: 'slowLcp', severity: 'serious'}, {key: 'heavyPage', severity: 'moderate'}, {key: 'highCls'}, {key: 'slowTtfb'}];
        expect(withoutTimingFindings(stored).map(rec => rec.key)).toEqual(['heavyPage']);
        expect(withoutTimingFindings(undefined)).toEqual([]);
        expect(auditFindings({vitals: {recommendations: stored}}, t).map(finding => finding.key)).toEqual(['audit.vitals.heavyPage']);
    });
});
