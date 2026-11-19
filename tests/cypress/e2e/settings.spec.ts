import {deleteUser} from '@jahia/cypress';
import {
    TestUser,
    asRoot,
    assertEffectiveUser,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    readSettings,
    saveSettings
} from '../support/cg';

const SAVE_PATH = ['contentGovernance', 'saveSiteSettings'];

/**
 * Per-site switches: any user who can read the site reads them (every editor's drawer does), only a
 * site administrator of a site that enables the module changes them, and an id of another form than
 * a tab id is dropped. The drawer's registry lists the tabs: the server stores the id of any of them.
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
        saveSettings(asRoot(), siteKey, []);
    });

    after(() => {
        [admin, editor, outsider].forEach(user => deleteUser(user.username));
        deleteTestSite(siteKey);
        deleteTestSite(plainSiteKey);
    });

    it('starts with every tab switched on', () => {
        readSettings(admin, siteKey).its('data.contentGovernance.siteSettings').should(settings => {
            expect(settings.moduleEnabled).to.eq(true);
            expect(settings.canEdit).to.eq(true);
            expect(settings.disabled).to.be.empty;
        });
    });

    it('lets a site administrator switch off a tab of the module, a part of it and a tab of another module, and drops ids of another form', () => {
        assertEffectiveUser(admin);
        saveSettings(admin, siteKey, ['geo.citations', 'compliance', 'aiAssistantReview', 'compliance', 'my tab', 'geo.score.x', '../audits'])
            .its('data.contentGovernance.saveSiteSettings')
            .should(settings => {
                expect(settings.disabled).to.deep.eq(['geo.citations', 'compliance', 'aiAssistantReview']);
            });
        readSettings(editor, siteKey).its('data.contentGovernance.siteSettings.disabled')
            .should('deep.eq', ['geo.citations', 'compliance', 'aiAssistantReview']);
    });

    it('lets an editor read the settings but not change them', () => {
        assertEffectiveUser(editor);
        readSettings(editor, siteKey).its('data.contentGovernance.siteSettings.canEdit').should('eq', false);
        saveSettings(editor, siteKey, ['geo']).then(body => assertRefused(body, SAVE_PATH));
        readSettings(asRoot(), siteKey).its('data.contentGovernance.siteSettings.disabled').should('be.empty');
    });

    it('refuses a user who cannot read the site', () => {
        assertEffectiveUser(outsider);
        readSettings(outsider, siteKey).then(body => assertRefused(body, ['contentGovernance', 'siteSettings']));
        saveSettings(outsider, siteKey, ['geo']).then(body => assertRefused(body, SAVE_PATH));
    });

    it('keeps a site that does not enable the module switched off, even for root', () => {
        readSettings(asRoot(), plainSiteKey).its('data.contentGovernance.siteSettings').should(settings => {
            expect(settings.moduleEnabled).to.eq(false);
            expect(settings.canEdit).to.eq(false);
        });
        saveSettings(asRoot(), plainSiteKey, ['geo']).then(body => assertRefused(body, SAVE_PATH));
    });

    it('refuses a malformed site key without reading the repository', () => {
        readSettings(asRoot(), '../sites/x').then(body => assertRefused(body, ['contentGovernance', 'siteSettings']));
    });
});
