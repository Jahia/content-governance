import {deleteUser} from '@jahia/cypress';
import {TestUser, addNodeTree, createTestSite, createTestUser, deleteTestSite, gql, guardToken, servlet} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
// The subdomains of the domain Page quality in the layout that the module ships
const PAGE_QUALITY_TABS = ['health', 'links', 'accessibility', 'vitals', 'ecodesign'];

/**
 * The module calls no model: the AI features live in ai-assistant, which adds its own tab to the
 * drawer. On a Jahia with no ai-assistant, as in this suite, nothing of the module names AI.
 */
describe('Content Governance - no model call', () => {
    const siteKey = 'cg-no-model';
    const sitePath = `/sites/${siteKey}`;
    const admin: TestUser = {username: 'cg-no-model-admin', password: 'cgAdmin1234'};
    const pageUrl = `/jahia/jcontent/${siteKey}/en/pages/home/cg-no-model-page`;

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-no-model-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'No model page'}
            ]
        });
        createTestUser(admin, sitePath, ['site-administrator']);
    });

    beforeEach(() => {
        // jContent's own ResizeObserver reads an unmounted node when the page goes away at sign-out
        // (jcontent 3.7): only that error is ignored, any error of the module still fails the test.
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    // A back-office login holds a licence seat until its session expires: sign out.
    afterEach(() => {
        cy.logout();
    });

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('gives the site settings no AI status', () => {
        gql(admin, `query { contentGovernance { siteSettings(siteKey: "${siteKey}") { disabled ai { configured } } } }`)
            .then(body => {
                expect(body.errors, 'the field ai is gone').to.have.length.greaterThan(0);
                expect(JSON.stringify(body.errors)).to.contain("Field 'ai'").and.to.contain('undefined');
            });
    });

    it('serves no AI endpoint', () => {
        cy.login(admin.username, admin.password);
        guardToken().then(token => {
            servlet('/audit/ai-review', {task: 'review', path: `${sitePath}/home`, language: 'en'}, {[token.name]: token.value})
                .its('status').should('eq', 404);
        });
    });

    it('shows no AI tab in Page quality, and no AI model tab in the site settings', () => {
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).should('be.visible').within(() => {
            // The sections show once the layout of the site is read
            cy.get('#content-governance-panel-quality button[id^="content-governance-subdomain-"]', {timeout: 90000}).should($headers => {
                expect($headers.toArray().map(header => header.id)).to.deep.eq(PAGE_QUALITY_TABS.map(id => `content-governance-subdomain-${id}`));
            });
            cy.contains(/\bAI review\b/).should('not.exist');
        });
        cy.visit(`/jahia/administration/${siteKey}/contentGovernanceSettings`);
        cy.contains('Features', {timeout: 30000}).should('be.visible');
        cy.contains('BotRank').should('be.visible');
        cy.contains('AI model').should('not.exist');
    });

    it('offers no Report tab on the GEO dashboard while no module writes the report', () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/siteSettingsSeo/geoReadiness`);
        cy.get('#geo-dash-group-overview', {timeout: 30000}).should('exist');
        cy.get('#geo-dash-tab-report').should('not.exist');
    });
});
