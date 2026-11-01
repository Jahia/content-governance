import {describe, expect, it} from 'vitest';
import {readFindings, subRowsOf} from '../../javascript/audit/dashboard/pageFindings.js';

const t = (key, params) => (params ? `${key} ${JSON.stringify(params)}` : key);
const item = {path: '/sites/s/home'};

const clientAnswering = runs => ({
    query: () => Promise.resolve({data: {contentGovernance: {pageAudits: runs}}})
});

describe('readFindings', () => {
    it('reads the newest stored audit, most serious first, with what to do', async () => {
        const results = {
            seo: {recommendations: [{key: 'noTitle', severity: 'moderate'}]},
            a11y: {violations: [{id: 'image-alt', impact: 'critical', help: 'Images must have alternative text', description: 'Ensure images have alt', totalNodes: 2}]}
        };
        const older = {results: JSON.stringify({seo: {recommendations: [{key: 'old', severity: 'critical'}]}})};
        const findings = await readFindings(clientAnswering([{results: JSON.stringify(results)}, older]), item.path, 'en', t);
        expect(findings.map(f => [f.key, f.severity, f.detail, f.count])).toEqual([
            ['audit.a11y.image-alt', 'critical', 'Ensure images have alt', 2],
            ['audit.seo.noTitle', 'moderate', 'seo.recs.noTitle.detail', undefined]
        ]);
    });

    it('finds nothing when the audit results were not stored', async () => {
        expect(await readFindings(clientAnswering([{results: null}]), item.path, 'en', t)).toEqual([]);
        expect(await readFindings(clientAnswering([]), item.path, 'en', t)).toEqual([]);
    });
});

describe('subRowsOf', () => {
    it('says the findings are being read until they are', () => {
        expect(subRowsOf(item, {}).map(row => row.kind)).toEqual(['loading']);
    });

    it('gives one sub-row per finding, each column pointing at it', () => {
        const rows = subRowsOf(item, {[item.path]: {findings: [{key: 'a'}, {key: 'b'}]}});
        expect(rows.map(row => row.id)).toEqual(['/sites/s/home#a', '/sites/s/home#b']);
        expect(rows[0].page).toEqual({id: '/sites/s/home#a', kind: 'finding', item, finding: {key: 'a'}});
        expect(rows[0].actions.finding).toEqual({key: 'a'});
    });

    it('says when the findings were not stored or could not be read', () => {
        expect(subRowsOf(item, {[item.path]: {findings: []}})[0].kind).toBe('missing');
        expect(subRowsOf(item, {[item.path]: {kind: 'error'}})[0].kind).toBe('error');
    });
});
