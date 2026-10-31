import {deleteUser} from '@jahia/cypress';
import {
    TestUser,
    assertGuardRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    guardToken,
    servlet,
    switchOff
} from '../support/cg';

/**
 * The module's servlets sit under Jahia's CSRF guard, which exempts guests only: a signed-in caller
 * sends the guard's token, so a basic-auth or API-token call is refused. Past the guard, the GEO
 * endpoints answer users holding `publish` on a site that keeps the GEO section on.
 */
describe('Content Governance - servlets behind the CSRF guard', () => {
    const siteKey = 'cg-guard';
    const sitePath = `/sites/${siteKey}`;
    const status = {action: 'scanStatus', path: sitePath, language: 'en'};
    const admin: TestUser = {username: 'cg-guard-admin', password: 'cgAdmin1234'};
    const editor: TestUser = {username: 'cg-guard-editor', password: 'cgEditor1234'};

    before(() => {
        createTestSite(siteKey);
        createTestUser(admin, sitePath, ['site-administrator']);
        createTestUser(editor, sitePath, ['editor']);
    });

    // A back-office login holds a licence seat until its session expires: sign out.
    afterEach(() => {
        cy.logout();
        switchOff(siteKey, []);
    });

    after(() => {
        [admin, editor].forEach(user => deleteUser(user.username));
        deleteTestSite(siteKey);
    });

    it('refuses basic auth, which carries no token', () => {
        cy.clearCookies();
        servlet('/geo/site-scan', status, {}, admin).then(assertGuardRefused);
    });

    it('refuses a signed-in session without the token, or with a wrong one', () => {
        cy.login(admin.username, admin.password);
        servlet('/geo/site-scan', status).then(assertGuardRefused);
        guardToken().then(token => {
            servlet('/geo/site-scan', status, {[token.name]: 'not-the-token'}).then(assertGuardRefused);
        });
    });

    it('answers a signed-in session that sends the token', () => {
        cy.login(admin.username, admin.password);
        guardToken().then(token => {
            servlet('/geo/site-scan', status, {[token.name]: token.value}).then(response => {
                expect(response.status).to.eq(200);
                expect(response.headers['content-type']).to.contain('application/json');
            });
        });
    });

    it('refuses an editor without publish on the site, past the guard', () => {
        cy.login(editor.username, editor.password);
        guardToken().then(token => {
            servlet('/geo/site-scan', status, {[token.name]: token.value}).its('status').should('eq', 403);
        });
    });

    it('answers as for a missing site once the site switches GEO off', () => {
        switchOff(siteKey, ['geo']);
        cy.login(admin.username, admin.password);
        guardToken().then(token => {
            servlet('/geo/site-scan', status, {[token.name]: token.value}).its('status').should('eq', 403);
        });
    });
});
