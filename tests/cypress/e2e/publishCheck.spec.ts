import {deleteUser} from '@jahia/cypress';
import {TestUser, createTestSite, createTestUser, deleteTestSite, gqlOk, switchOff} from '../support/cg';

const SAVE = `mutation S($path: String!, $language: String!, $results: String!) {
    contentGovernance { saveAudit(path: $path, language: $language, results: $results) { summary { total seriousAccessibility } } }
}`;

const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
const ITEM = '[data-sel-role="contentGovernancePublishCheck"]';

/** Results as the drawer sends them, with one critical and one minor accessibility violation. */
const RESULTS = JSON.stringify({
    page: {title: 'Home', description: '', imagesWithoutAlt: 0},
    a11y: {violations: [{id: 'image-alt', impact: 'critical', nodes: []}, {id: 'region', impact: 'minor', nodes: []}], passes: [], incomplete: []},
    seo: {recommendations: [{key: 'shortTitle', severity: 'minor', params: {}}]}
});

/**
 * The item Content Governance adds at the top of jContent's Publish menu, for the page in view: what
 * the latest stored audit found (all issues, serious accessibility), opening the drawer. jContent's
 * own publish actions stay as they are.
 */
describe('Content Governance - check before publishing', () => {
    const siteKey = 'cg-publish';
    const homePath = `/sites/${siteKey}/home`;
    const admin: TestUser = {username: 'cg-publish-admin', password: 'cgAdmin1234'};

    before(() => {
        createTestSite(siteKey);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
    });

    beforeEach(() => {
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => {
        cy.logout();
        switchOff(siteKey, []);
    });

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    const openPublishMenu = () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', 'Content governance', {timeout: 30000}).should('exist');
        cy.get('[data-sel-role="publishMenu"]').first().click({force: true});
    };

    it('says the page was not audited yet', () => {
        openPublishMenu();
        cy.get(ITEM, {timeout: 30000}).should('contain.text', 'Not audited yet - open Content governance before publishing');
    });

    it('counts the issues of the latest audit, first in the menu, and opens the drawer', () => {
        gqlOk(admin, SAVE, {path: homePath, language: 'en', results: RESULTS})
            .its('contentGovernance.saveAudit.summary').should('deep.equal', {total: 3, seriousAccessibility: 1});
        openPublishMenu();
        cy.get(ITEM, {timeout: 30000}).should('contain.text', 'audit issues: 3, serious accessibility: 1');
        // Above jContent's own "Publish" in the menu
        cy.get('li[data-sel-role="publish"]').then(publish => {
            cy.get(ITEM).should(item => {
                expect(item[0].compareDocumentPosition(publish[0]) & Node.DOCUMENT_POSITION_FOLLOWING).to.be.greaterThan(0);
            });
        });
        cy.get(ITEM).click();
        cy.get(DRAWER).should('be.visible');
        cy.get(DRAWER).find('#content-governance-section-audit').should('have.attr', 'aria-selected', 'true');
    });

    it('adds nothing once the site switched Page quality and Compliance off', () => {
        switchOff(siteKey, ['audit', 'compliance']);
        openPublishMenu();
        cy.get('[data-sel-role="publish"]', {timeout: 30000}).should('exist');
        cy.get(ITEM).should('not.exist');
    });
});
