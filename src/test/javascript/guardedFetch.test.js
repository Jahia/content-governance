import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

// A fake jahia-csrf-guard 4.x: the master token is accepted once per address, the answer carries the
// address's page token, and a refused request is a redirect (opaqueredirect with redirect: 'manual').
function fakeGuard() {
    const used = new Set();
    const calls = {tokenLoads: 0, refused: 0, servlet: 0};
    const response = (body, headers = {}) => ({
        ok: true, type: 'basic', status: 200,
        headers: {get: name => headers[name] ?? null},
        text: async () => body, json: async () => JSON.parse(body)
    });
    const fetch = vi.fn(async (url, init = {}) => {
        if (url.endsWith('/modules/CsrfServlet') && (init.method || 'GET') === 'GET') {
            calls.tokenLoads++;
            return response("var a = 'X-CSRF', b = 'master-token-1';");
        }
        if (url.endsWith('/modules/CsrfServlet')) {
            return response('{"pageTokens": {}}');
        }
        calls.servlet++;
        const address = new URL(url, 'http://jahia').pathname;
        const token = init.headers['X-CSRF'];
        if (token === 'master-token-1' && used.has(address)) {
            calls.refused++;
            return {ok: false, type: 'opaqueredirect', status: 0, headers: {get: () => null}};
        }
        used.add(address);
        // Answers after a while, as a busy server does
        await new Promise(resolve => setTimeout(resolve, 20));
        return response('{}', {'X-CSRF': JSON.stringify({pageTokens: {[address]: `page-${address}`}})});
    });
    return {fetch, calls};
}

describe('guardedFetch', () => {
    let guard;
    beforeEach(() => {
        vi.resetModules();
        guard = fakeGuard();
        vi.stubGlobal('fetch', guard.fetch);
        vi.stubGlobal('location', {origin: 'http://jahia'});
        vi.stubGlobal('BroadcastChannel', undefined);
    });
    afterEach(() => vi.unstubAllGlobals());

    it('sends parallel first requests to one address without a refusal or a second token load', async () => {
        const {guardedFetch} = await import('../../javascript/common/guardedFetch.js');
        const url = '/modules/content-governance/geo/site-scan';
        const answers = await Promise.all([guardedFetch(url, {method: 'POST'}), guardedFetch(url, {method: 'POST'}), guardedFetch(url, {method: 'POST'})]);
        expect(answers.every(res => res.ok)).toBe(true);
        expect(guard.calls).toMatchObject({tokenLoads: 1, refused: 0, servlet: 3});
    });

    it('does not hold back requests to different addresses', async () => {
        const {guardedFetch} = await import('../../javascript/common/guardedFetch.js');
        await Promise.all(['/a', '/b', '/c'].map(path => guardedFetch(`/modules/content-governance${path}`, {method: 'POST'})));
        expect(guard.calls).toMatchObject({tokenLoads: 1, refused: 0, servlet: 3});
    });
});
