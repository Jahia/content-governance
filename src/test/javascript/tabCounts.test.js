import {describe, expect, it} from 'vitest';
import {tabCounts} from '../../javascript/audit/tabCounts.js';
import {buildVitalsRecommendations} from '../../javascript/audit/analyzers/webVitals.js';
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
        }, {recommendations: [{severity: 'moderate'}]});
        expect(counts.accessibility.serious).toBe(true);
        expect(counts.seo.serious).toBe(false);
        expect(counts.readability.serious).toBe(true);
        expect(counts.ai).toEqual({count: 1, serious: false});
    });

    it('gives nothing before an audit', () => {
        expect(tabCounts(null, null)).toEqual({});
    });
});

describe('isSerious', () => {
    it('is true for critical and serious, false for the rest', () => {
        expect(['critical', 'serious', 'moderate', 'minor', undefined].map(isSerious)).toEqual([true, true, false, false, false]);
    });
});

describe('Web Vitals findings', () => {
    const diagnostics = {missingDims: 0, oversized: 0, totalBytes: 100, requests: 5, domNodes: 100, largest: []};

    it('raises nothing on a timing, however slow', () => {
        const recs = buildVitalsRecommendations({metrics: {lcp: 9000, cls: 0.9, ttfb: 5000}, diagnostics});
        expect(recs).toEqual([]);
    });

    it('still raises what weighs the page down', () => {
        const recs = buildVitalsRecommendations({
            metrics: {lcp: 100, cls: 0, ttfb: 10},
            diagnostics: {...diagnostics, missingDims: 2, totalBytes: 3 * 1024 * 1024, requests: 120, domNodes: 2000}
        });
        expect(recs.map(rec => rec.key)).toEqual(['missingDims', 'heavyPage', 'manyRequests', 'largeDom']);
    });

    it('drops the timing findings of an audit stored before 1.11, on the tab and in the lists', () => {
        const stored = [{key: 'slowLcp', severity: 'serious'}, {key: 'heavyPage', severity: 'moderate'}, {key: 'highCls'}, {key: 'slowTtfb'}];
        expect(withoutTimingFindings(stored).map(rec => rec.key)).toEqual(['heavyPage']);
        expect(withoutTimingFindings(undefined)).toEqual([]);
        expect(auditFindings({vitals: {recommendations: stored}}, t).map(finding => finding.key)).toEqual(['audit.vitals.heavyPage']);
    });
});
