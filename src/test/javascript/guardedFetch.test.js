import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

// A fake jahia-csrf-guard 4.x: the master token is accepted once per address, the answer carries the
// address's page token, and a refused request is a redirect (opaqueredirect with redirect: 'manual'),
// or with refuseWith a status and an HTML page, as a Jahia Cloud node answered 503.
function fakeGuard({refuseWith = null} = {}) {
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
        const address = new URL(url, 'https://jahia').pathname;
        const token = init.headers['X-CSRF'];
        if (token === 'stale-token' || (token === 'master-token-1' && used.has(address))) {
            calls.refused++;
            if (refuseWith) {
                return {ok: false, type: 'basic', status: refuseWith,
                    headers: {get: name => (name.toLowerCase() === 'content-type' ? 'text/html' : null)},
                    text: async () => '<html>Service Unavailable</html>'};
            }
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
        vi.stubGlobal('location', {origin: 'https://jahia'});
        // One tab: the channel that tells other tabs to drop their tokens has no one to tell
        vi.stubGlobal('BroadcastChannel', class {
            postMessage() {}
        });
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

    it('recovers from a refusal that the guard answers with a status, as a token of another session', async () => {
        guard = fakeGuard({refuseWith: 503});
        const guardFetch = guard.fetch.getMockImplementation();
        let staleLoads = 0;
        // The first load gives a token that the session no longer holds, as after a token rotation elsewhere.
        guard.fetch.mockImplementation(async (address, init = {}) => {
            if (staleLoads === 0 && address.endsWith('/modules/CsrfServlet') && (init.method || 'GET') === 'GET') {
                staleLoads++;
                return {ok: true, type: 'basic', status: 200, headers: {get: () => null},
                    text: async () => "var a = 'X-CSRF', b = 'stale-token';"};
            }
            return guardFetch(address, init);
        });
        vi.stubGlobal('fetch', guard.fetch);
        const {guardedFetch} = await import('../../javascript/common/guardedFetch.js');

        const res = await guardedFetch('/modules/content-governance/geo/report?path=%2Fsites%2Fdigitall&language=en');

        expect(res.ok).toBe(true);
        expect(guard.calls).toMatchObject({refused: 1, tokenLoads: 1, servlet: 2});
    });

    it('returns an error of the servlet as it is, with no second request', async () => {
        const {guardedFetch} = await import('../../javascript/common/guardedFetch.js');
        const servletError = {ok: false, type: 'basic', status: 503,
            headers: {get: name => (name.toLowerCase() === 'content-type' ? 'application/json' : null)},
            json: async () => ({error: 'notConfigured'})};
        await guardedFetch('/modules/content-governance/geo/report');
        const calls = guard.calls.servlet;
        guard.fetch.mockImplementation(async address => (address.endsWith('/modules/CsrfServlet')
            ? {ok: true, type: 'basic', status: 200, headers: {get: () => null}, text: async () => '', json: async () => ({})}
            : servletError));

        const res = await guardedFetch('/modules/content-governance/geo/report');

        expect(res.status).toBe(503);
        expect(guard.fetch.mock.calls.filter(([address]) => !address.endsWith('/modules/CsrfServlet')).length)
            .toBe(calls + 1);
    });
});
