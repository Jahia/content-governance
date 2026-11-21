/**
 * Helpers shared by the Content Governance specs.
 *
 * GraphQL calls go through `gql(user, ...)`: a plain POST with basic auth for that user and the
 * `Origin` header Jahia requires, so who the call runs as is written at each call site. The calls of
 * a test share one server session (the cookie), which holds a licence seat until it expires (a trial
 * licence then refuses every sign-in), so every test ends with `signOut` (support/e2e.ts). The module's
 * servlets sit under Jahia's CSRF guard, which answers browser sessions only: `servlet(...)` signs in
 * with a session (cookie) and sends the guard's token, as the drawer does.
 */

import {createSite, createUser, deleteSite, deleteUser, enableModule, grantRoles} from '@jahia/cypress';

export type TestUser = {
    username: string;
    password: string;
};

export type GqlError = {message: string; path?: string[]};

export type GqlBody = {
    data?: Record<string, unknown> | null;
    errors?: GqlError[];
};

/** The module under test, as Jahia names its bundle. */
export const MODULE_NAME = 'content-governance';

/**
 * The template set fixture sites are built from. The suite asserts nothing about rendering; any set
 * that creates a home page will do. Override with CYPRESS_CG_TEMPLATE_SET on an instance without it.
 */
export const TEMPLATE_SET: string = Cypress.env('CG_TEMPLATE_SET') || 'dx-base-demo-templates';

export const SETTINGS_FIELDS = 'siteKey moduleEnabled canEdit disabled';

/**
 * The tab that the test module (tests/jahia-module) adds to the drawer. Every site has it on until a
 * spec switches it off, so a spec that switches every tab off names it too.
 */
export const TEST_TAB = 'cgTestTab';

export function asRoot(): TestUser {
    return {username: 'root', password: Cypress.env('SUPER_USER_PASSWORD') || 'root1234'};
}

export function jahiaOrigin(): string {
    const baseUrl = Cypress.config('baseUrl');
    if (!baseUrl) {
        throw new Error('baseUrl is not set: the @jahia/cypress env plugin did not run');
    }
    return new URL(baseUrl).origin;
}

/**
 * Sends one GraphQL operation as `user` and yields the response body.
 *
 * @param {TestUser} user who the call runs as.
 * @param {string} query the operation.
 * @param {object} variables its variables.
 */
export function gql(user: TestUser, query: string, variables: Record<string, unknown> = {}): Cypress.Chainable<GqlBody> {
    return cy.request({
        method: 'POST',
        url: '/modules/graphql',
        auth: {username: user.username, password: user.password},
        headers: {Origin: jahiaOrigin(), 'Content-Type': 'application/json'},
        body: {query, variables},
        failOnStatusCode: false
    }).then(response => {
        expect(response.status, 'GraphQL answered').to.eq(200);
        return response.body as GqlBody;
    });
}

/**
 * Ends the server session of the current cookies, freeing its licence seat, and drops the guest
 * session cookie the logout answers with: sent along, it would win over the next call's basic auth.
 */
export function signOut(): Cypress.Chainable<null> {
    cy.request({url: '/cms/logout', followRedirect: false, failOnStatusCode: false});
    return cy.clearCookies();
}

/** Sends an operation as `user` and fails on any GraphQL error. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function gqlOk(user: TestUser, query: string, variables: Record<string, unknown> = {}): Cypress.Chainable<any> {
    return gql(user, query, variables).then(body => {
        expect(body.errors, `no GraphQL error for ${user.username}`).to.be.undefined;
        return body.data;
    });
}

/** Asserts that the call runs as `user`, not as a leftover identity. */
export function assertEffectiveUser(user: TestUser): void {
    gqlOk(user, '{ currentUser { username } }').its('currentUser.username').should('eq', user.username);
}

/** Asserts that the field at `path` refused the call with `message`, past the API filter. */
export function assertRefused(body: GqlBody, path: string[], message = 'Permission denied'): void {
    expect(body.errors, 'the call is refused').to.have.length.greaterThan(0);
    const [error] = body.errors ?? [];
    expect(error.message).to.eq(message);
    expect(error.path, 'refused by the module, past the API filter').to.deep.eq(path);
}

export function readSettings(user: TestUser, siteKey: string): Cypress.Chainable<GqlBody> {
    return gql(user, `query S($siteKey: String!) { contentGovernance { siteSettings(siteKey: $siteKey) { ${SETTINGS_FIELDS} } } }`,
        {siteKey});
}

export function saveSettings(user: TestUser, siteKey: string, disabled: string[]): Cypress.Chainable<GqlBody> {
    return gql(user, `mutation S($siteKey: String!, $disabled: [String]!) {
        contentGovernance { saveSiteSettings(siteKey: $siteKey, disabled: $disabled) { ${SETTINGS_FIELDS} } }
    }`, {siteKey, disabled});
}

/** Switches tabs of the drawer, and parts of tabs, off on a site as root (every id not listed is switched on). */
export function switchOff(siteKey: string, disabled: string[]): void {
    saveSettings(asRoot(), siteKey, disabled).its('errors').should('be.undefined');
}

/**
 * Creates a node and its children as root, and fails on any GraphQL error (the @jahia/cypress
 * `addNode` helper resolves even when the mutation is refused).
 */
export function addNodeTree(parentPath: string, node: Record<string, unknown>): void {
    gqlOk(asRoot(), `mutation Add($parent: String!, $name: String!, $type: String!, $props: [InputJCRProperty],
            $children: [InputJCRNode], $mixins: [String]) {
        jcr { addNode(parentPathOrId: $parent, name: $name, primaryNodeType: $type, properties: $props,
                children: $children, mixins: $mixins) { uuid } }
    }`, {parent: parentPath, ...node});
}

/**
 * Adds HTML to the main area of a page, so that the page renders it as written, on the server as in the
 * preview of the drawer: a `jnt:bigText` in a free zone (`ctpl:freeZone`) of classic-templates, the
 * template set of the CI (CYPRESS_CG_TEMPLATE_SET), whose page templates hold the area `main`. A rich
 * text of the template set rewrites its HTML, such as a heading level that skips one.
 *
 * @param {string} pagePath the path of the page.
 * @param {string} name the name of the zone.
 * @param {Record<string, string>} bodies the HTML, by language.
 */
export function addPageText(pagePath: string, name: string, bodies: Record<string, string>): void {
    const properties = Object.entries(bodies).map(([language, value]) => ({name: 'text', language, value}));
    addNodeTree(`${pagePath}/main`, {name, type: 'ctpl:freeZone', children: [{name: 'text', primaryNodeType: 'jnt:bigText', properties}]});
}

/** Whether a node exists, read as root. */
export function nodeExists(path: string): Cypress.Chainable<boolean> {
    return gql(asRoot(), 'query N($path: String!) { jcr { nodeByPath(path: $path) { path } } }', {path})
        .then(body => Boolean((body.data?.jcr as {nodeByPath?: unknown} | undefined)?.nodeByPath));
}

/**
 * Creates a site, after removing what a killed run may have left, with the module enabled unless
 * `withModule` is false.
 *
 * @param {string} siteKey the site key.
 * @param {boolean} withModule whether the site enables content-governance.
 */
export function createTestSite(siteKey: string, withModule = true, languages = 'en,fr'): void {
    deleteTestSite(siteKey);
    createSite(siteKey, {templateSet: TEMPLATE_SET, serverName: 'localhost', locale: 'en', languages});
    if (withModule) {
        enableModule(MODULE_NAME, siteKey);
    }
    nodeExists(`/sites/${siteKey}/home`).should('eq', true);
}

export function deleteTestSite(siteKey: string): void {
    nodeExists(`/sites/${siteKey}`).then(exists => {
        if (exists) {
            deleteSite(siteKey);
        }
    });
}

/**
 * Creates a user granted `roles` on `sitePath`, after deleting a leftover with the same name; `uiLanguage`
 * sets the user's preferred interface language (Jahia's `preferredLanguage`).
 */
export function createTestUser(user: TestUser, sitePath: string, roles: string[], uiLanguage?: string): void {
    deleteUser(user.username);
    createUser(user.username, user.password, uiLanguage ? [{name: 'preferredLanguage', value: uiLanguage}] : []);
    if (roles.length > 0) {
        grantRoles(sitePath, roles, user.username, 'USER');
    }
}

/** The guard's header name and master token of the signed-in session (jahia-csrf-guard 4.x). */
const MASTER_TOKEN = /var\s+[\w$]+\s*=\s*'([A-Za-z][A-Za-z0-9_-]*)'\s*,\s*[\w$]+\s*=\s*'([A-Za-z0-9-]{8,})'/;

export type GuardToken = {name: string; value: string};

/** Reads the guard's token of the session `cy.login` opened. */
export function guardToken(): Cypress.Chainable<GuardToken> {
    return cy.request({url: '/modules/CsrfServlet', headers: {'Cache-Control': 'no-store'}}).then(response => {
        const match = MASTER_TOKEN.exec(String(response.body));
        expect(match, 'the CSRF guard serves a token to the session').to.not.be.null;
        const [, name, value] = match ?? [];
        return {name, value};
    });
}

/**
 * POSTs a JSON body to one of the module's servlets with the current cookies, without following the
 * guard's redirect, and yields the response.
 *
 * @param {string} endpoint the servlet path below /modules/content-governance (`/geo/site-scan`).
 * @param {object} body the JSON body.
 * @param {object} headers extra headers (the guard's token).
 * @param {TestUser} basicAuth credentials sent as basic auth instead of a session.
 */
export function servlet(endpoint: string, body: Record<string, unknown>, headers: Record<string, string> = {},
    basicAuth?: TestUser): Cypress.Chainable<Cypress.Response<Record<string, unknown>>> {
    return cy.request({
        method: 'POST',
        url: `/modules/content-governance${endpoint}`,
        headers: {'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest', Origin: jahiaOrigin(), ...headers},
        auth: basicAuth ? {username: basicAuth.username, password: basicAuth.password} : undefined,
        body,
        followRedirect: false,
        failOnStatusCode: false
    });
}

/** Asserts that the CSRF guard refused a request: a redirect to its error page. */
export function assertGuardRefused(response: Cypress.Response<unknown>): void {
    expect(response.status, 'the CSRF guard redirects').to.eq(302);
    expect(String(response.headers.location), 'to its error page').to.contain('/error.html');
}

/** One violation that the tool frame.check returns. */
export type FrameViolation = {ruleId: string; folder: string | null; kind: string; match?: string; replacement?: string};

function mcpRequest(method: string, params: Record<string, unknown>, session?: string): Cypress.Chainable<Cypress.Response<unknown>> {
    const root = asRoot();
    return cy.request({
        method: 'POST',
        url: '/modules/mcp',
        auth: {username: root.username, password: root.password},
        headers: {
            Origin: jahiaOrigin(),
            'Content-Type': 'application/json',
            Accept: 'application/json, text/event-stream',
            ...(session ? {'Mcp-Session-Id': session} : {})
        },
        body: {jsonrpc: '2.0', id: 1, method, params}
    });
}

/** The JSON-RPC message of an answer of the MCP server, sent as JSON or as server-sent events. */
function mcpMessage(body: unknown): Record<string, any> {
    if (typeof body !== 'string') {
        return body as Record<string, any>;
    }
    const data = body.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).join('');
    return JSON.parse(data || body);
}

/** One finding that the tool frame.check returns for a path. */
export type FramePageFinding = {rule: string; severity: string; text: string; context?: string};

/**
 * Calls the tool frame.check of mcp-server as root, as an agent does, and yields its result.
 *
 * @param {object} args the arguments of the tool: siteKey, locale, and text or path.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function callFrameCheck(args: Record<string, unknown>): Cypress.Chainable<Record<string, any>> {
    return mcpRequest('initialize', {protocolVersion: '2025-03-26', capabilities: {}, clientInfo: {name: 'cg-cypress', version: '1'}})
        .then(init => mcpRequest('tools/call', {name: 'frame.check', arguments: args}, init.headers['mcp-session-id'] as string | undefined))
        .then(response => {
            const result = mcpMessage(response.body).result;
            return result.structuredContent || JSON.parse(result.content[0].text);
        });
}

/**
 * Calls frame.check on a text, and yields its violations.
 *
 * @param {object} args the arguments of the tool: siteKey, locale and text.
 */
export function frameCheck(args: Record<string, unknown>): Cypress.Chainable<FrameViolation[]> {
    return callFrameCheck(args).then(payload => payload.violations as FrameViolation[]);
}

/**
 * Calls frame.check on a page or a content item, and yields the findings of the checks of the server.
 *
 * @param {object} args the arguments of the tool: siteKey, locale and path.
 */
export function framePageCheck(args: Record<string, unknown>): Cypress.Chainable<FramePageFinding[]> {
    return callFrameCheck(args).then(payload => payload.findings as FramePageFinding[]);
}
