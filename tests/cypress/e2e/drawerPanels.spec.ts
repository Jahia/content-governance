import {deleteUser} from '@jahia/cypress';
import {
    TEST_PANEL,
    TEST_TAB,
    TestUser,
    addNodeTree,
    asRoot,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gql,
    readSettings,
    setDrawerDisplay,
    switchOff
} from '../support/cg';

const GOVERNANCE = 'Content governance';
const PANEL = 'Test panel';
// The header buttons: jContent gives a button the key of its action, and each other panel its own
const GOVERNANCE_BUTTON = '[data-sel-role="contentGovernance"]';
const PANEL_BUTTON = `[data-sel-role="contentGovernancePanel-${TEST_PANEL}"]`;
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
const TITLE = '#content-governance-drawer-title';
const FIX_FIRST = 'section[aria-label="Fix first"]';
const panelView = (key: string) => `#content-governance-drawer-panel-${key}`;

const SAVE_FINDINGS = `mutation F($path: String!, $language: String!, $tab: String!, $findings: String!) {
    contentGovernance { saveFindings(path: $path, language: $language, tab: $tab, findings: $findings) { tab summary { total } } }
}`;

/**
 * The panels of the drawer. The test module (tests/jahia-module) registers `cgTestPanel`, which no
 * code of Content Governance names, after the governance panel, and an action on a finding that
 * opens it with the finding, as "Fix with the assistant" opens the chat of AI Assistant.
 */
describe('Content Governance - the panels of the drawer', () => {
    const siteKey = 'cg-panels';
    const sitePath = `/sites/${siteKey}`;
    const pagePath = `${sitePath}/home/cg-panels-page`;
    const admin: TestUser = {username: 'cg-panels-admin', password: 'cgAdmin1234'};
    const pageUrl = `/jahia/jcontent/${siteKey}/en/pages/home/cg-panels-page`;

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-panels-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Panels page'}
            ]
        });
        createTestUser(admin, sitePath, ['site-administrator']);
        // The cfg of the server hides "Fix first" until it shows it
        setDrawerDisplay({fixFirst: true, previewButton: false});
    });

    beforeEach(() => {
        // jContent's own ResizeObserver reads an unmounted node when the page goes away at sign-out
        // (jcontent 3.7): only that error is ignored, any error of the module still fails the test.
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
        // Page quality and GEO readiness measure the page: off, so "Fix first" holds the findings of the test tab only
        switchOff(siteKey, ['audit', 'geo']);
    });

    afterEach(() => {
        cy.logout();
        switchOff(siteKey, []);
    });

    after(() => {
        setDrawerDisplay({fixFirst: false, previewButton: false});
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    const visitPage = (button = GOVERNANCE_BUTTON) => {
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        cy.get(button, {timeout: 30000}).should('exist');
    };

    it('gives each panel a button that opens the one drawer on it: the buttons are the switch between the panels', () => {
        visitPage();
        cy.get(PANEL_BUTTON).should('contain.text', PANEL).and('have.attr', 'aria-pressed', 'false');

        cy.get(GOVERNANCE_BUTTON).should('contain.text', GOVERNANCE).click();
        cy.get(GOVERNANCE_BUTTON).should('have.attr', 'aria-pressed', 'true');
        cy.get(PANEL_BUTTON).should('have.attr', 'aria-pressed', 'false');
        cy.get(DRAWER).should('have.length', 1).within(() => {
            cy.get(TITLE).should('have.text', GOVERNANCE);
            // The domains are the only tab bar of the drawer
            cy.get('[role="tablist"]').should('have.length', 1).and('have.attr', 'aria-label', 'Sections');
            cy.get(`#content-governance-section-${TEST_TAB}`).should('exist');
        });

        // The button of the other panel switches the drawer that is open
        cy.get(PANEL_BUTTON).click();
        cy.get(PANEL_BUTTON).should('have.attr', 'aria-pressed', 'true');
        cy.get(GOVERNANCE_BUTTON).should('have.attr', 'aria-pressed', 'false');
        cy.get(DRAWER).should('have.length', 1).within(() => {
            cy.get(TITLE).should('have.text', PANEL);
            cy.get(`[data-cg-test-panel="${pagePath}"]`).should('be.visible');
            cy.get(panelView('governance')).should('not.be.visible');
        });

        // Its button shows governance again, as it was left
        cy.get(GOVERNANCE_BUTTON).click();
        cy.get(DRAWER).should('have.length', 1).within(() => {
            cy.get(TITLE).should('have.text', GOVERNANCE);
            cy.get(`#content-governance-section-${TEST_TAB}`).should('be.visible');
        });

        // A second press on the button of the panel in view closes the drawer
        cy.get(GOVERNANCE_BUTTON).click();
        cy.get(DRAWER).should('not.exist');
        cy.get(GOVERNANCE_BUTTON).should('have.attr', 'aria-pressed', 'false');
    });

    it('opens a panel with the context that an action on a finding gives it', () => {
        visitPage();
        cy.get(GOVERNANCE_BUTTON).click();
        cy.get(DRAWER).within(() => {
            // A tab mounts, and reports its findings to "Fix first", on its first visit
            cy.get(`#content-governance-section-${TEST_TAB}`).click();
            cy.get(FIX_FIRST).contains('button', 'Show').click();
            cy.get(FIX_FIRST).contains('li', 'Test tab: a critical finding').contains('button', 'Open in the test panel').click();
            cy.get(TITLE).should('have.text', PANEL);
            cy.get('[data-cg-test-context="cgTestTab.critical"]')
                .should('contain.text', 'Opened on Test tab: a critical finding')
                .and('have.attr', 'data-cg-test-language', 'en');
        });
    });

    it('lists each panel in the site settings, where a site administrator switches it off', () => {
        cy.intercept('POST', '**/modules/graphql', req => {
            const body = JSON.stringify(req.body);
            if (body.includes('ContentGovernanceSaveSiteSettings')) {
                req.alias = 'save';
            } else if (body.includes('ContentGovernanceSiteSettings')) {
                req.alias = 'settings';
            }
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/administration/${siteKey}/contentGovernanceSettings`);
        cy.wait('@settings');
        cy.get('[data-panel="governance"]', {timeout: 30000}).should('contain.text', GOVERNANCE)
            .find(`[data-tab="${TEST_TAB}"]`).should('exist');
        cy.get(`[data-panel="${TEST_PANEL}"]`).should('contain.text', 'A panel that the test module adds')
            .within(() => cy.get(`[aria-label="${PANEL} panel"]`).click({force: true}));
        cy.contains('button', 'Save').click();
        cy.wait('@save');
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('include', TEST_PANEL);

        cy.visit(pageUrl);
        cy.get(GOVERNANCE_BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).should('be.visible');
        cy.get('[role="tablist"][aria-label="Panels"]').should('not.exist');
        cy.get(PANEL_BUTTON).should('not.exist');
    });

    it('switches every tab off with the governance panel, and keeps the other panels', () => {
        switchOff(siteKey, ['governance']);
        // The button of the other panel shows first: an absence is asserted once the header is drawn
        visitPage(PANEL_BUTTON);
        cy.get(PANEL_BUTTON).click();
        cy.get(GOVERNANCE_BUTTON).should('not.exist');
        cy.get(DRAWER).within(() => {
            cy.get(TITLE).should('have.text', PANEL);
            cy.get('[role="tablist"][aria-label="Panels"]').should('not.exist');
        });
        const findings = JSON.stringify([{key: 'cgTestTab.one', severity: 'serious', title: 'One'}]);
        gql(asRoot(), SAVE_FINDINGS, {path: pagePath, language: 'en', tab: TEST_TAB, findings})
            .then(body => assertRefused(body, ['contentGovernance', 'saveFindings']));
    });
});
