import {deleteUser} from '@jahia/cypress';
import {GuardToken, TestUser, createTestSite, createTestUser, deleteTestSite, guardToken, jahiaOrigin, servlet} from '../support/cg';

/**
 * The written GEO report comes from a writer that another module registers. The test module registers
 * a stub that answers a fixed report on the sites whose key starts with cg-geo-report, and refuses the
 * others as not configured. The module itself calls no model.
 */
describe('Content Governance - GEO report through a writer of another module', () => {
    const siteKey = 'cg-geo-report';
    const otherSiteKey = 'cg-geo-plain';
    const sitePath = `/sites/${siteKey}`;
    const admin: TestUser = {username: 'cg-geo-report-admin', password: 'cgAdmin1234'};

    const status = (path: string, token: GuardToken) => cy.request({
        url: `/modules/content-governance/geo/report?path=${encodeURIComponent(path)}&language=en`,
        headers: {Origin: jahiaOrigin(), 'X-Requested-With': 'XMLHttpRequest', [token.name]: token.value},
        failOnStatusCode: false
    });

    before(() => {
        createTestSite(siteKey);
        createTestSite(otherSiteKey);
        createTestUser(admin, sitePath, ['site-administrator']);
        createTestUser({username: `${admin.username}-other`, password: admin.password}, `/sites/${otherSiteKey}`,
            ['site-administrator']);
    });

    // A back-office login holds a licence seat until its session expires: sign out.
    afterEach(() => {
        cy.logout();
    });

    after(() => {
        deleteUser(admin.username);
        deleteUser(`${admin.username}-other`);
        deleteTestSite(siteKey);
        deleteTestSite(otherSiteKey);
    });

    it('offers the report with the model of the writer, then writes, stores and reads it back', () => {
        cy.login(admin.username, admin.password);
        guardToken().then(token => {
            status(sitePath, token).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.include({enabled: true, reason: '', provider: 'stub', model: 'stub-model'});
            });
            servlet('/geo/report', {action: 'generate', path: sitePath, language: 'en', reportLanguage: 'fr'},
                {[token.name]: token.value}).then(response => {
                expect(response.status).to.eq(200);
                const report = response.body.report as Record<string, unknown>;
                expect(report.summary).to.eq(`Stub report of ${siteKey} in French (fr).`);
                expect(report.model).to.eq('stub-model');
                expect(report.reportLanguage).to.eq('fr');
            });
            servlet('/geo/report', {action: 'read', path: sitePath, language: 'en'}, {[token.name]: token.value})
                .its('body.report.summary').should('eq', `Stub report of ${siteKey} in French (fr).`);
        });
    });

    it('offers the Report tab on the GEO dashboard where the writer answers', () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/siteSettingsSeo/geoReadiness`);
        cy.get('#geo-dash-tab-report', {timeout: 30000}).should('exist');
    });

    it('offers no report where the writer does not answer, and writes none', () => {
        cy.login(`${admin.username}-other`, admin.password);
        guardToken().then(token => {
            status(`/sites/${otherSiteKey}`, token).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.include({enabled: false, reason: 'notConfigured', provider: '', model: ''});
            });
            servlet('/geo/report', {action: 'generate', path: `/sites/${otherSiteKey}`, language: 'en', reportLanguage: 'en'},
                {[token.name]: token.value}).its('status').should('eq', 409);
        });
    });
});
