import {deleteUser} from '@jahia/cypress';
import {
    TEST_TAB,
    TestUser,
    addNodeTree,
    asRoot,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gql,
    gqlOk,
    readSettings,
    switchOff
} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
const FIX_FIRST = 'section[aria-label="Fix first"]';
const section = (id: string) => `#content-governance-section-${id}`;

const HISTORY = `query H($path: String!, $language: String!, $tab: String) {
    contentGovernance { pageAudits(path: $path, language: $language, tab: $tab) {
        tab auditedBy summary { counts { tab count } total } results
    } }
}`;

const SAVE_FINDINGS = `mutation F($path: String!, $language: String!, $tab: String!, $findings: String!) {
    contentGovernance { saveFindings(path: $path, language: $language, tab: $tab, findings: $findings) { tab summary { total } } }
}`;

/**
 * A tab that another module adds to the drawer. The test module (tests/jahia-module) registers
 * `cgTestTab`, which no code of Content Governance names, after the tabs of the module. It gets a
 * site switch, a "Fix first" rank and a history, as the tabs of the module do.
 */
describe('Content Governance - a tab of another module', () => {
    const siteKey = 'cg-tabs';
    const sitePath = `/sites/${siteKey}`;
    const pagePath = `${sitePath}/home/cg-tabs-page`;
    const admin: TestUser = {username: 'cg-tabs-admin', password: 'cgAdmin1234'};
    const pageUrl = `/jahia/jcontent/${siteKey}/en/pages/home/cg-tabs-page`;

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-tabs-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Tabs page'}
            ]
        });
        createTestUser(admin, sitePath, ['site-administrator']);
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
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    const openDrawer = () => {
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        return cy.get(DRAWER).should('be.visible');
    };

    it('shows the tab after the tabs of the module, with its count, and ranks its findings in "Fix first"', () => {
        openDrawer().within(() => {
            cy.get('[role="tab"]').should(tabs => {
                expect([...tabs].map(tab => tab.id)).to.deep.eq([section('compliance'), section(TEST_TAB)].map(id => id.slice(1)));
            });
            cy.get(section(TEST_TAB)).should('contain.text', 'Test tab').click();
            cy.get('[data-cg-test-summary]').should('exist');
            cy.get(section(TEST_TAB)).should('contain.text', '2');
            cy.get(FIX_FIRST).should('contain.text', 'Test tab: a critical finding').and('not.contain.text', 'a minor finding');

            // "Fix first" opens the tab that found the finding
            cy.get(section('compliance')).click();
            cy.get(FIX_FIRST).contains('button', 'Show').click();
            cy.get('button[aria-label="Open: Test tab: a critical finding"]').click();
            cy.get(section(TEST_TAB)).should('have.attr', 'aria-selected', 'true');
        });
    });

    it('stores the findings of the tab as its history, apart from the Page quality audits', () => {
        openDrawer().within(() => {
            cy.get(section(TEST_TAB)).click();
            cy.contains('button', 'Store the findings').click();
            cy.get('[data-cg-test-stored="2"]').should('exist');
            cy.get('[data-cg-test-runs="1"]').should('exist');
        });
        gqlOk(asRoot(), HISTORY, {path: pagePath, language: 'en', tab: TEST_TAB}).its('contentGovernance.pageAudits').should(runs => {
            expect(runs).to.have.length(1);
            expect(runs[0].tab).to.eq(TEST_TAB);
            expect(runs[0].auditedBy).to.eq(admin.username);
            expect(runs[0].summary).to.deep.eq({counts: [{tab: TEST_TAB, count: 2}], total: 2});
            const stored = JSON.parse(runs[0].results).findings;
            expect(stored.map((finding: {key: string}) => finding.key)).to.deep.eq(['cgTestTab.critical', 'cgTestTab.minor']);
            expect(stored[0].severity).to.eq('critical');
        });
        // Page quality keeps its own runs: the site switches it on again to read them
        switchOff(siteKey, []);
        gqlOk(asRoot(), HISTORY, {path: pagePath, language: 'en'}).its('contentGovernance.pageAudits').should('be.empty');
    });

    it('lists the tab and its part in the site settings, where a site administrator switches them off', () => {
        // jContent's client may batch its operations: match the operation in the body, whatever its shape
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
        cy.get(`[data-tab="${TEST_TAB}"]`, {timeout: 30000}).should('contain.text', 'Test tab').and('contain.text', 'A tab that the test module adds')
            .within(() => cy.get('[aria-label="Summary"]').click({force: true}));
        cy.contains('button', 'Save').click();
        // The panel reads the settings again once saved, and starts its next change from them
        cy.wait('@save');
        cy.wait('@settings');
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('include', `${TEST_TAB}.summary`);
        cy.get('[aria-label="Summary"]').should('not.be.checked');

        cy.get(`[data-tab="${TEST_TAB}"]`).within(() => cy.get('[aria-label="Test tab section"]').click({force: true}));
        cy.contains('button', 'Save').click();
        cy.wait('@save');
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('include', TEST_TAB);

        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).should('be.visible');
        cy.get(section('compliance')).should('exist');
        cy.get(section(TEST_TAB)).should('not.exist');
    });

    it('hides the part of the tab that the site switched off', () => {
        switchOff(siteKey, ['audit', 'geo', `${TEST_TAB}.summary`]);
        openDrawer().within(() => {
            cy.get(section(TEST_TAB)).click();
            cy.contains('button', 'Store the findings').should('exist');
            cy.get('[data-cg-test-summary]').should('not.exist');
        });
    });

    it('refuses to store or read the runs of a tab the site switched off, and stores Page quality audits apart', () => {
        switchOff(siteKey, [TEST_TAB]);
        const findings = JSON.stringify([{key: 'cgTestTab.one', severity: 'serious', title: 'One'}]);
        gql(asRoot(), SAVE_FINDINGS, {path: pagePath, language: 'en', tab: TEST_TAB, findings})
            .then(body => assertRefused(body, ['contentGovernance', 'saveFindings']));
        gql(asRoot(), HISTORY, {path: pagePath, language: 'en', tab: TEST_TAB})
            .then(body => assertRefused(body, ['contentGovernance', 'pageAudits']));
        gql(asRoot(), SAVE_FINDINGS, {path: pagePath, language: 'en', tab: 'my tab', findings})
            .then(body => assertRefused(body, ['contentGovernance', 'saveFindings']));
        gql(asRoot(), SAVE_FINDINGS, {path: pagePath, language: 'en', tab: 'audit', findings})
            .then(body => assertRefused(body, ['contentGovernance', 'saveFindings'], 'Page quality stores its audits with saveAudit'));
    });
});
