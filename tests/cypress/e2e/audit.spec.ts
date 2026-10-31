import {deleteUser} from '@jahia/cypress';
import {TestUser, createTestSite, createTestUser, deleteTestSite} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';

/**
 * What axe-core reports must reach the Page quality audit. Every axe-core update (always to its latest
 * release: .agents/README.md, "Invariants") adds here a case for each rule its release notes turn on
 * by default or extend, so a rule the module stops running fails this spec.
 *
 * | axe-core | rule                        | case in FIXTURE                              |
 * |----------|-----------------------------|----------------------------------------------|
 * | any      | image-alt                   | an image without alt                         |
 * | any      | link-name                   | a link with no text                          |
 * | 4.14     | label-content-name-mismatch | a link whose aria-label leaves out its text  |
 *
 * The fixture is appended to the page the audit's preview frame loads, not stored as content: a
 * template set may sanitise rich text when rendering it (classic-templates drops aria-label and adds
 * alt=""), and the cases must reach axe as written.
 */
const FIXTURE = '<div id="cg-a11y-fixture">'
    + '<a href="#cg-report" aria-label="Open the annual report">Download</a>'
    + '<a href="#cg-empty"></a>'
    + '<img src="/files/cg-missing.png">'
    + '</div>';
const EXPECTED_RULES = ['image-alt', 'link-name', 'label-content-name-mismatch'];

describe('Content Governance - Page quality audit', () => {
    const siteKey = 'cg-audit';
    const admin: TestUser = {username: 'cg-audit-admin', password: 'cgAdmin1234'};

    before(() => {
        createTestSite(siteKey);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
    });

    beforeEach(() => {
        // jContent's own ResizeObserver reads an unmounted node when the page goes away at sign-out
        // (jcontent 3.7): only that error is ignored, any error of the module still fails the test.
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => cy.logout());

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('runs the axe-core version the module declares, and reports what it finds', () => {
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${FIXTURE}</body>`);
            });
        }).as('preview');
        cy.readFile('../package.json').its('dependencies.axe-core').then((range: string) => {
            const declared = range.replace(/^\D+/, '');
            cy.login(admin.username, admin.password);
            cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
            cy.contains('button', BUTTON, {timeout: 30000}).click();
            cy.get(DRAWER).should('be.visible');
            cy.wait('@preview', {timeout: 30000});
            // The Accessibility tab opens first. The bundled engine is the declared minimum: a lockfile
            // ahead of package.json fails here.
            cy.get(DRAWER).contains(`axe-core ${declared}`, {timeout: 60000}).should('exist');
            EXPECTED_RULES.forEach(rule => {
                cy.get(DRAWER).contains('a', new RegExp(`^${rule}$`)).should('exist');
            });
        });
    });
});
