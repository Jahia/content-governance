import {deleteUser} from '@jahia/cypress';
import {TestUser, createTestSite, createTestUser, deleteTestSite} from '../support/cg';

const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';

/**
 * The module's own screens in the editor's interface language: init.js loads that language's file
 * only, so a language it does not list falls back to English, and one it lists must be complete
 * (locales.test.js checks the keys).
 */
describe('Content Governance - interface languages', () => {
    const siteKey = 'cg-languages';
    const editors: Array<{user: TestUser; lang: string; button: string; section: string}> = [
        {user: {username: 'cg-languages-de', password: 'cgEditor1234'}, lang: 'de', button: 'Content-Governance', section: 'Seitenqualität'},
        {user: {username: 'cg-languages-es', password: 'cgEditor1234'}, lang: 'es', button: 'Gobernanza de contenidos', section: 'Calidad de la página'}
    ];

    before(() => {
        createTestSite(siteKey);
        editors.forEach(({user, lang}) => createTestUser(user, `/sites/${siteKey}`, ['site-administrator'], lang));
    });

    beforeEach(() => {
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => cy.logout());

    after(() => {
        editors.forEach(({user}) => deleteUser(user.username));
        deleteTestSite(siteKey);
    });

    editors.forEach(({user, lang, button, section}) => {
        it(`shows the drawer in ${lang} to an editor whose interface is in ${lang}`, () => {
            cy.login(user.username, user.password);
            cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
            cy.contains('button', button, {timeout: 30000}).click();
            cy.get(DRAWER).find('#content-governance-section-audit').should('contain.text', section);
        });
    });
});
