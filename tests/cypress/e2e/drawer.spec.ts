import {deleteUser} from '@jahia/cypress';
import type {Interception} from 'cypress/types/net-stubbing';
import {TestUser, addNodeTree, createTestSite, createTestUser, deleteTestSite, switchOff} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
const section = (id: string) => `#content-governance-section-${id}`;

/**
 * The drawer in jContent, as a site administrator: one header button on a page, a tab per section
 * the site keeps on, and no button once every section is off. Kept apart from the API specs: a
 * back-office login takes a licence seat.
 */
describe('Content Governance - drawer', () => {
    const siteKey = 'cg-drawer';
    const sitePath = `/sites/${siteKey}`;
    const admin: TestUser = {username: 'cg-drawer-admin', password: 'cgAdmin1234'};
    const pageUrl = `/jahia/jcontent/${siteKey}/en/pages/home/cg-drawer-page`;

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-drawer-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Drawer page'}
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

    afterEach(() => {
        cy.logout();
        switchOff(siteKey, []);
    });

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    // jContent loads the module's entry, the button and one language when it starts; the drawer,
    // the dashboards and the settings load on first use (622 kB at start before 1.5.0).
    it('loads less than 200 kB of the module where its button does not show', () => {
        cy.intercept('GET', '**/modules/content-governance/javascript/apps/*.js').as('apps');
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/media`);
        cy.wait('@apps', {timeout: 30000});
        // Room for anything loaded late, the background fetch of the drawer included
        cy.wait(6000);
        cy.get<Interception[]>('@apps.all').then(calls => {
            const files = calls.map(call => call.request.url.split('/').pop());
            const bytes = calls.reduce((sum, call) => sum + String(call.response?.body ?? '').length, 0);
            expect(files, 'axe-core loads with an audit only').not.to.include('axe.min.js');
            expect(bytes, `${files.join(', ')}`).to.be.lessThan(200000);
        });
    });

    it('opens on a page with a tab per section, and closes with Escape', () => {
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).should('be.visible').within(() => {
            cy.get(section('audit')).should('contain.text', 'Page quality');
            cy.get(section('geo')).should('contain.text', 'GEO readiness');
            cy.get(section('compliance')).should('contain.text', 'Compliance');
            cy.get(section('geo')).click();
            cy.get(section('geo')).should('have.attr', 'aria-selected', 'true');
        });
        cy.focused().type('{esc}');
        cy.get(DRAWER).should('not.exist');
    });

    it('leaves out the tab of a section the site switched off', () => {
        switchOff(siteKey, ['geo']);
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).should('be.visible');
        cy.get(section('audit')).should('exist');
        cy.get(section('compliance')).should('exist');
        cy.get(section('geo')).should('not.exist');
    });

    it('asks the CSRF guard for its token once per page, whatever the parts of the dashboard', () => {
        cy.intercept('GET', '**/modules/CsrfServlet').as('guardToken');
        cy.intercept('POST', '**/modules/content-governance/geo/**').as('geo');
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/siteSettingsSeo/geoReadiness`);
        cy.wait('@geo', {timeout: 30000}).its('response.statusCode').should('eq', 200);
        // Let every part of the dashboard send its first request before counting.
        cy.wait(3000);
        // jContent's page also loads the guard as a <script> of its own: count the module's fetches only.
        cy.get<Interception[]>('@guardToken.all')
            .then(calls => calls.filter(call => call.request.headers['sec-fetch-dest'] !== 'script'))
            .should('have.length', 1);
        cy.get('@geo.all').should('have.length.greaterThan', 1);
    });

    it('shows no button once every section is off', () => {
        switchOff(siteKey, ['audit', 'geo', 'compliance']);
        cy.login(admin.username, admin.password);
        cy.visit(pageUrl);
        // The header is drawn once its other actions are: wait for one before asserting an absence.
        cy.contains('Drawer page', {timeout: 30000}).should('exist');
        cy.contains('button', BUTTON).should('not.exist');
    });
});
