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

type Section = {id: string; enabled: boolean; tabs: {id: string; enabled: boolean}[]};

const SAVE_PATH = ['contentGovernance', 'saveSiteSettings'];

/**
 * Per-site switches: any user who can read the site reads them (every editor's drawer does), only a
 * site administrator of a site that enables the module changes them, and unknown ids are dropped.
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

    it('starts with every section and tab switched on', () => {
        readSettings(admin, siteKey).its('data.contentGovernance.siteSettings').should(settings => {
            expect(settings.moduleEnabled).to.eq(true);
            expect(settings.canEdit).to.eq(true);
            expect(settings.disabled).to.be.empty;
            expect(settings.sections.map((s: Section) => s.id)).to.deep.eq(['audit', 'geo', 'compliance']);
            settings.sections.forEach((s: Section) => {
                expect(s.enabled, s.id).to.eq(true);
                s.tabs.forEach(tab => expect(tab.enabled, `${s.id}.${tab.id}`).to.eq(true));
            });
        });
    });

    it('lets a site administrator switch a section and a tab off, and drops unknown ids', () => {
        assertEffectiveUser(admin);
        saveSettings(admin, siteKey, ['geo.citations', 'compliance', 'nope', 'geo.nope'])
            .its('data.contentGovernance.saveSiteSettings')
            .should(settings => {
                expect(settings.disabled).to.have.members(['geo.citations', 'compliance']);
            });
        readSettings(editor, siteKey).its('data.contentGovernance.siteSettings').should(settings => {
            const byId = Object.fromEntries(settings.sections.map((s: Section) => [s.id, s]));
            expect(byId.compliance.enabled).to.eq(false);
            expect(byId.geo.enabled).to.eq(true);
            expect(byId.geo.tabs.find((tab: {id: string}) => tab.id === 'citations').enabled).to.eq(false);
            expect(byId.geo.tabs.find((tab: {id: string}) => tab.id === 'score').enabled).to.eq(true);
        });
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
            settings.sections.forEach((s: Section) => expect(s.enabled, s.id).to.eq(false));
        });
        saveSettings(asRoot(), plainSiteKey, ['geo']).then(body => assertRefused(body, SAVE_PATH));
    });

    it('refuses a malformed site key without reading the repository', () => {
        readSettings(asRoot(), '../sites/x').then(body => assertRefused(body, ['contentGovernance', 'siteSettings']));
    });
});
