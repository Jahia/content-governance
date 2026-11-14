import {deleteUser, publishAndWaitJobEnding} from '@jahia/cypress';
import {TestUser, addNodeTree, createTestSite, createTestUser, deleteTestSite, guardToken, servlet} from '../support/cg';

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

/**
 * A content language with a region (`zh_CN`, `pt_BR`): Jahia writes it the Java way, the browser's
 * date and number formatters take `zh-CN` and threw `RangeError: Invalid language tag: zh_CN`
 * as soon as the drawer showed an audit date (1.9.0).
 */
describe('Content Governance - content language with a region', () => {
    const siteKey = 'cg-region';
    const admin: TestUser = {username: 'cg-region-admin', password: 'cgAdmin1234'};

    before(() => {
        createTestSite(siteKey, true, 'en,zh_CN');
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
    });

    beforeEach(() => {
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => cy.logout());

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('audits a page in zh_CN and shows the date of the audit', () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/zh_CN/pages/home`);
        cy.contains('button', 'Content governance', {timeout: 30000}).click();
        cy.get(DRAWER).contains('button', 'Re-run', {timeout: 120000}).should('not.be.disabled').click();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 120000}).should('exist');
        cy.get(DRAWER).contains(/Last audit: .*2\d{3}/).should('exist');
        cy.get(DRAWER).find('#content-governance-section-compliance').click();
        cy.get(DRAWER).contains('Chinese (China)', {timeout: 60000}).should('exist');
    });

    // The servlets turned the language into a Java locale with Locale.forLanguageTag, which reads
    // "zh-CN" and gives the empty locale for "zh_CN": the live workspace then had no such page.
    it('finds a page published in zh_CN published, in GEO readiness', () => {
        const pagePath = `/sites/${siteKey}/home/cg-region-page`;
        addNodeTree(`/sites/${siteKey}/home`, {
            name: 'cg-region-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Region page'},
                {name: 'jcr:title', language: 'zh_CN', value: '地区页面'}
            ]
        });
        publishAndWaitJobEnding(`/sites/${siteKey}/home`, ['en', 'zh_CN']);
        cy.login(admin.username, admin.password);
        guardToken().then(token => {
            ['en', 'zh_CN'].forEach(language => {
                servlet('/geo/crawler-check', {path: pagePath, language}, {[token.name]: token.value}).then(response => {
                    expect(response.status, language).to.eq(200);
                    expect(response.body.published, `published in ${language}`).to.eq(true);
                });
            });
        });
    });
});
