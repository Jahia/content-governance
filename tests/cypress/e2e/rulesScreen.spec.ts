import {deleteUser} from '@jahia/cypress';
import {
    TestUser,
    asRoot,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gqlOk,
    resetLayout,
    saveLayout
} from '../support/cg';

const LAYOUT = `query L($siteKey: String!) { contentGovernance {
    siteLayout(siteKey: $siteKey) { saved json off }
    contentDomains(siteKey: $siteKey, includeOff: true) { id on switchedOn subdomains { id on switchedOn } }
    contentRules(siteKey: $siteKey) { id subdomain order switchedOn isNew }
} }`;

type Hierarchy = {
    contentDomains: Array<{id: string; on: boolean; switchedOn: boolean; subdomains: Array<{id: string; on: boolean; switchedOn: boolean}>}>;
    contentRules: Array<{id: string; subdomain: string; order: number; switchedOn: boolean; isNew: boolean}>;
    siteLayout: {saved: boolean; json: string; off: string[]};
};

const hierarchy = (siteKey: string): Cypress.Chainable<Hierarchy> =>
    gqlOk(asRoot(), LAYOUT, {siteKey}).its('contentGovernance');

/**
 * The layout that names every domain, subdomain and rule in its order, and switches off the items whose switch is
 * off, and the ids of `alsoOff`.
 */
const fullLayout = (data: Hierarchy, alsoOff: string[] = []) => ({
    domains: data.contentDomains.map(domain => ({
        id: domain.id,
        subdomains: domain.subdomains.map(subdomain => ({
            id: subdomain.id,
            rules: data.contentRules.filter(rule => rule.subdomain === subdomain.id)
                .sort((a, b) => a.order - b.order).map(rule => rule.id)
        }))
    })),
    off: [
        ...data.contentDomains.filter(domain => !domain.switchedOn).map(domain => domain.id),
        ...data.contentDomains.flatMap(domain => domain.subdomains).filter(subdomain => !subdomain.switchedOn).map(subdomain => subdomain.id),
        ...data.contentRules.filter(rule => !rule.switchedOn).map(rule => rule.id),
        ...alsoOff
    ]
});

/** jContent's client may batch its operations: match the operation in the body, whatever its shape. */
const interceptScreen = () => cy.intercept('POST', '**/modules/graphql', req => {
    const body = JSON.stringify(req.body);
    if (body.includes('ContentGovernanceSaveLayout')) {
        req.alias = 'save';
    } else if (body.includes('ContentGovernanceResetLayout')) {
        req.alias = 'reset';
    } else if (body.includes('ContentGovernanceRulesScreen')) {
        req.alias = 'screen';
    }
});

const openRules = (subdomain: string) => cy.get(`[data-subdomain="${subdomain}"]`).contains('button', /rules?$/).click();

/**
 * jContent > Additional > Rules & domains: a site administrator orders, moves and switches the domains, the
 * subdomains and the rules of the site, and a server administrator does the same for the defaults of every site.
 */
describe('Content Governance - Rules & domains', () => {
    const siteKey = 'cg-rules-screen';
    const admin: TestUser = {username: 'cg-rules-admin', password: 'cgRules1234'};

    before(() => {
        createTestSite(siteKey);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
    });

    afterEach(() => {
        resetLayout(asRoot(), siteKey);
    });

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('moves a subdomain with its Move to menu, switches off a rule, and saves the layout of the site', () => {
        interceptScreen();
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/contentGovernanceRules`);
        cy.wait('@screen');
        cy.get('[data-domain="quality"] [data-subdomain="links"]', {timeout: 30000}).should('exist');

        cy.get('[aria-label="Move Links"] [role="listbox"]').click();
        cy.contains('li[role="option"]', 'SEO & GEO').click();
        cy.get('[data-domain="geo"] [data-subdomain="links"]').should('exist');
        openRules('seo');
        cy.get('[data-rule="seo.indexable"] input[type="checkbox"]').click({force: true});
        cy.contains('button', 'Save').click();
        cy.wait('@save');
        cy.wait('@screen');
        cy.contains('Editors see the change').should('be.visible');

        hierarchy(siteKey).then(data => {
            expect(data.siteLayout.saved).to.eq(true);
            const geo = data.contentDomains.find(domain => domain.id === 'geo');
            expect(geo?.subdomains.map(subdomain => subdomain.id)).to.include('links');
            expect(data.siteLayout.off).to.include('seo.indexable');
            expect(data.contentRules.find(rule => rule.id === 'seo.indexable')?.switchedOn).to.eq(false);
            expect(data.contentRules.filter(rule => rule.isNew), 'a save names every rule').to.be.empty;
        });
    });

    it('marks a rule that the saved layout does not name as new, and resets the site to the defaults', () => {
        saveLayout(asRoot(), siteKey, {domains: [{id: 'geo', subdomains: [{id: 'seo', rules: ['seo.canonical']}]}], off: []})
            .its('errors').should('be.undefined');
        interceptScreen();
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/contentGovernanceRules`);
        cy.wait('@screen');
        openRules('seo');
        cy.get('[data-rule="seo.title"]', {timeout: 30000}).should('contain.text', 'New since your last save');
        cy.get('[data-rule="seo.canonical"]').should('not.contain.text', 'New since your last save');

        cy.contains('button', 'Reset to defaults').click();
        cy.contains('button', /^Reset$/).click();
        cy.wait('@reset');
        cy.contains('The site follows the defaults again').should('be.visible');
        hierarchy(siteKey).its('siteLayout.saved').should('eq', false);
    });

    it('lets a server administrator save the defaults of every site, which a site without a layout follows', () => {
        hierarchy('systemsite').then(defaults => {
            saveLayout(asRoot(), 'systemsite', fullLayout(defaults, ['ecodesign'])).its('errors').should('be.undefined');
            hierarchy(siteKey).then(data => {
                const quality = data.contentDomains.find(domain => domain.id === 'quality');
                expect(quality?.subdomains.find(subdomain => subdomain.id === 'ecodesign')?.on, 'off by default').to.eq(false);
            });
            hierarchy('systemsite').then(changed => {
                const quality = changed.contentDomains.find(domain => domain.id === 'quality');
                expect(quality?.subdomains.find(subdomain => subdomain.id === 'ecodesign')?.switchedOn).to.eq(false);
            });
            // The defaults are shared by every spec: put them back
            saveLayout(asRoot(), 'systemsite', fullLayout(defaults)).its('errors').should('be.undefined');
        });

        cy.login();
        cy.visit('/jahia/administration/contentGovernanceDefaultRules');
        cy.get('[data-domain="quality"]', {timeout: 30000}).should('exist');
        cy.get('[data-panels]').should('not.exist');
        cy.contains('button', 'Reset to defaults').should('not.exist');
    });
});
