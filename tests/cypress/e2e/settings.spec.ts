import {deleteUser} from '@jahia/cypress';
import {
    TestUser,
    asRoot,
    assertEffectiveUser,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gqlOk,
    readSettings,
    resetLayout,
    saveLayout,
    setDrawerDisplay
} from '../support/cg';

const SAVE_PATH = ['contentGovernance', 'saveSiteLayout'];
const RESET_PATH = ['contentGovernance', 'resetSiteLayout'];
const DRAWER_DISPLAY = '{ contentGovernance { drawerDisplay { fixFirst previewButton } } }';

/**
 * Reads drawerDisplay as `user` until it gives `expected`: Config Admin hands a cfg change to the
 * module a moment after the provisioning API answers.
 */
function expectDrawerDisplay(user: TestUser, expected: {fixFirst: boolean; previewButton: boolean}, tries = 10): void {
    gqlOk(user, DRAWER_DISPLAY).then(data => {
        const display = data.contentGovernance.drawerDisplay;
        if (tries > 1 && JSON.stringify(display) !== JSON.stringify(expected)) {
            cy.wait(500);
            expectDrawerDisplay(user, expected, tries - 1);
        } else {
            expect(display, 'what the drawer shows').to.deep.eq(expected);
        }
    });
}

/**
 * The switches of a site live in the off list of its layout: any user who can read the site reads what is off
 * (every editor's drawer does), only a site administrator of a site that enables the module saves a layout, and
 * only a server administrator saves the defaults of systemsite.
 */
describe('Content Governance - site settings', () => {
    const siteKey = 'cg-settings';
    const plainSiteKey = 'cg-settings-plain';
    const admin: TestUser = {username: 'cg-settings-admin', password: 'cgAdmin1234'};
    const editor: TestUser = {username: 'cg-settings-editor', password: 'cgEditor1234'};
    const outsider: TestUser = {username: 'cg-settings-outsider', password: 'cgOutsider1234'};

    before(() => {
        createTestSite(siteKey);
        createTestSite(plainSiteKey, false);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
        // privileged lets the editor through the GraphQL API filter, so the module's own check is what refuses
        createTestUser(editor, `/sites/${siteKey}`, ['editor', 'privileged']);
        createTestUser(outsider, `/sites/${plainSiteKey}`, ['editor', 'privileged']);
    });

    afterEach(() => {
        resetLayout(asRoot(), siteKey);
    });

    after(() => {
        [admin, editor, outsider].forEach(user => deleteUser(user.username));
        deleteTestSite(siteKey);
        deleteTestSite(plainSiteKey);
    });

    it('starts with everything switched on', () => {
        readSettings(admin, siteKey).its('data.contentGovernance.siteSettings').should(settings => {
            expect(settings.moduleEnabled).to.eq(true);
            expect(settings.canEdit).to.eq(true);
            expect(settings.disabled).to.be.empty;
        });
    });

    it('lets a site administrator switch off a subdomain, a domain with its subdomains, a panel and a tab of another module', () => {
        assertEffectiveUser(admin);
        saveLayout(admin, siteKey, {domains: [], off: ['geo.citations', 'compliance', 'chat', 'aiAssistantReview']})
            .its('data.contentGovernance.saveSiteLayout')
            .should(layout => {
                expect(layout.saved).to.eq(true);
                expect(layout.off).to.deep.eq(['geo.citations', 'compliance', 'chat', 'aiAssistantReview']);
            });
        readSettings(editor, siteKey).its('data.contentGovernance.siteSettings.disabled')
            .should('include.members', ['geo.citations', 'compliance', 'chat', 'aiAssistantReview', 'readability', 'terms'])
            .and('not.include', 'seo');
    });

    it('refuses a layout that names an id of another form, or an id twice', () => {
        saveLayout(admin, siteKey, {domains: [], off: ['my tab']}).its('errors.0.message')
            .should('contain', 'my tab');
        saveLayout(admin, siteKey, {domains: [{id: 'geo', subdomains: [{id: 'seo', rules: ['seo.title', 'seo.title']}]}]})
            .its('errors.0.message').should('contain', 'twice');
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('be.empty');
    });

    it('lets an editor read the settings but not change them', () => {
        assertEffectiveUser(editor);
        readSettings(editor, siteKey).its('data.contentGovernance.siteSettings.canEdit').should('eq', false);
        saveLayout(editor, siteKey, {domains: [], off: ['geo']}).then(body => assertRefused(body, SAVE_PATH));
        resetLayout(editor, siteKey).then(body => assertRefused(body, RESET_PATH));
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('be.empty');
    });

    it('refuses a user who cannot read the site', () => {
        assertEffectiveUser(outsider);
        readSettings(outsider, siteKey).then(body => assertRefused(body, ['contentGovernance', 'siteSettings']));
        saveLayout(outsider, siteKey, {domains: [], off: ['geo']}).then(body => assertRefused(body, SAVE_PATH));
    });

    it('keeps a site that does not enable the module switched off, even for root', () => {
        readSettings(asRoot(), plainSiteKey).its('data.contentGovernance.siteSettings').should(settings => {
            expect(settings.moduleEnabled).to.eq(false);
            expect(settings.canEdit).to.eq(false);
        });
        saveLayout(asRoot(), plainSiteKey, {domains: [], off: ['geo']}).then(body => assertRefused(body, SAVE_PATH));
    });

    it('leaves the defaults of systemsite to a server administrator, and has no reset for them', () => {
        saveLayout(admin, 'systemsite', {domains: [], off: []}).then(body => {
            expect(body.errors, 'the call is refused').to.have.length.greaterThan(0);
        });
        resetLayout(asRoot(), 'systemsite').its('errors.0.message').should('contain', 'no reset');
    });

    it('gives any signed-in user what the cfg of the server shows in the drawer', () => {
        setDrawerDisplay({fixFirst: true, previewButton: false});
        expectDrawerDisplay(outsider, {fixFirst: true, previewButton: false});
        setDrawerDisplay({fixFirst: false, previewButton: true});
        expectDrawerDisplay(outsider, {fixFirst: false, previewButton: true});
        setDrawerDisplay({fixFirst: false, previewButton: false});
        expectDrawerDisplay(outsider, {fixFirst: false, previewButton: false});
    });

    it('refuses a malformed site key without reading the repository', () => {
        readSettings(asRoot(), '../sites/x').then(body => assertRefused(body, ['contentGovernance', 'siteSettings']));
    });
});
