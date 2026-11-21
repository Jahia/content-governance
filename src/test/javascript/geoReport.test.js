import {afterEach, describe, expect, it, vi} from 'vitest';

const answer = vi.fn();
vi.mock('../../javascript/common/guardedFetch.js', () => ({guardedFetch: (...args) => answer(...args)}));

const {fetchReportStatus, generateReport} = await import('../../javascript/geo/api/geoReport.js');

const json = (status, body) => ({ok: status < 400, status, json: async () => body});

describe('the client of the GEO report', () => {
    afterEach(() => answer.mockReset());

    it('reads a refusal of the CSRF guard as refused, not as a 403 of access', async () => {
        answer.mockResolvedValue(json(403, {error: 'refused'}));
        await expect(fetchReportStatus({path: '/sites/digitall', language: 'en'})).rejects.toMatchObject({code: 'refused'});
        await expect(generateReport({path: '/sites/digitall', language: 'en', reportLanguage: 'en'}))
            .rejects.toMatchObject({code: 'refused'});
    });

    it('keeps the status of an answer of the server', async () => {
        answer.mockResolvedValue(json(403, {error: 'access required'}));
        await expect(fetchReportStatus({path: '/sites/digitall', language: 'en'})).rejects.toMatchObject({code: 403});
        answer.mockResolvedValue({ok: false, status: 503, json: async () => {
            throw new SyntaxError('not JSON');
        }});
        await expect(fetchReportStatus({path: '/sites/digitall', language: 'en'})).rejects.toMatchObject({code: 503});
    });
});
