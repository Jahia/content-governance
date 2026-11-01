import {deleteUser} from '@jahia/cypress';
import {
    GqlBody,
    TestUser,
    addNodeTree,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gql,
    gqlOk,
    switchOff
} from '../support/cg';

const SAVE = `mutation S($path: String!, $language: String!, $results: String!) {
    contentGovernance { saveAudit(path: $path, language: $language, results: $results) {
        auditedAt auditedBy summary { accessibility seo total }
    } }
}`;

const HISTORY = `query H($path: String!, $language: String!) {
    contentGovernance { pageAudits(path: $path, language: $language) { auditedAt auditedBy summary { total } results } }
}`;

const SITE = `query D($siteKey: String!, $language: String!) {
    contentGovernance { siteAudits(siteKey: $siteKey, language: $language) {
        pageCount pages { path title previousTotal lastAudit { auditedBy summary { accessibility seo vitals total } } }
    } }
}`;

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';

/** Results as the drawer sends them: `seo` issues as recommendations, accessibility as violations. */
const results = (violations: number, seo: number) => JSON.stringify({
    page: {title: 'Shared page', description: '', imagesWithoutAlt: 0},
    a11y: {violations: Array.from({length: violations}, (_, i) => ({id: `rule-${i}`, impact: 'serious', nodes: []})), passes: [], incomplete: []},
    seo: {recommendations: Array.from({length: seo}, (_, i) => ({key: `rec-${i}`, severity: 'minor', params: {}}))}
});

/**
 * Page quality audits shared by the editors of a site: stored when an editor runs one, read by the
 * others, the last ten kept, listed in the site dashboard, refused to whoever may not open jContent
 * on the site or read the page.
 */
describe('Content Governance - shared Page quality audits', () => {
    const siteKey = 'cg-quality';
    const sitePath = `/sites/${siteKey}`;
    const otherSiteKey = 'cg-quality-other';
    const pagePath = `${sitePath}/home/cg-shared`;
    const author: TestUser = {username: 'cg-quality-author', password: 'cgAuthor1234'};
    const colleague: TestUser = {username: 'cg-quality-colleague', password: 'cgColleague1234'};
    const outsider: TestUser = {username: 'cg-quality-outsider', password: 'cgOutsider1234'};

    before(() => {
        createTestSite(siteKey);
        createTestSite(otherSiteKey);
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-shared', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Shared page'}
            ]
        });
        // privileged lets the editors through the GraphQL API filter, so the module's own check is what refuses
        createTestUser(author, sitePath, ['editor', 'privileged']);
        createTestUser(colleague, sitePath, ['editor', 'privileged']);
        createTestUser(outsider, `/sites/${otherSiteKey}`, ['editor', 'privileged']);
    });

    beforeEach(() => {
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => switchOff(siteKey, []));

    after(() => {
        [author, colleague, outsider].forEach(user => deleteUser(user.username));
        deleteTestSite(siteKey);
        deleteTestSite(otherSiteKey);
    });

    it('stores an audit with counts computed on the server, and shows it to a colleague', () => {
        gqlOk(author, SAVE, {path: pagePath, language: 'en', results: results(2, 3)}).its('contentGovernance.saveAudit').should(run => {
            expect(run.auditedBy).to.eq(author.username);
            expect(run.summary).to.deep.include({accessibility: 2, seo: 3, total: 5});
        });
        gqlOk(colleague, HISTORY, {path: pagePath, language: 'en'}).its('contentGovernance.pageAudits').should(runs => {
            expect(runs).to.have.length(1);
            expect(runs[0].auditedBy).to.eq(author.username);
            expect(JSON.parse(runs[0].results).seo.recommendations).to.have.length(3);
        });
    });

    it('keeps the last ten audits of a page, the newest with its results only', () => {
        for (let i = 0; i < 11; i++) {
            gqlOk(author, SAVE, {path: pagePath, language: 'en', results: results(1, i)});
        }
        gqlOk(colleague, HISTORY, {path: pagePath, language: 'en'}).its('contentGovernance.pageAudits').should(runs => {
            expect(runs).to.have.length(10);
            expect(runs[0].summary.total).to.eq(11);
            expect(runs[0].results).to.be.a('string');
            expect(runs.slice(1).every((run: {results: string | null}) => run.results === null)).to.eq(true);
        });
    });

    it('lists the audited page in the site dashboard with its trend', () => {
        gqlOk(colleague, SITE, {siteKey, language: 'en'}).its('contentGovernance.siteAudits').should(report => {
            expect(report.pageCount).to.be.greaterThan(1);
            const page = report.pages.find((p: {path: string}) => p.path === pagePath);
            expect(page.title).to.eq('Shared page');
            expect(page.lastAudit.summary).to.deep.include({accessibility: 1, seo: 10, vitals: null, total: 11});
            expect(page.previousTotal).to.eq(10);
        });
    });

    it('shows the findings of a page\'s latest audit when its dashboard row is expanded, most serious first', () => {
        const audit = JSON.stringify({
            page: {title: 'Shared page', description: '', imagesWithoutAlt: 3},
            seo: {recommendations: [{key: 'noTitle', severity: 'serious', params: {}}]},
            a11y: {
                violations: [{
                    id: 'image-alt', impact: 'critical', help: 'Images must have alternative text',
                    description: 'Ensure img elements have alternative text', totalNodes: 3, nodes: []
                }],
                passes: [], incomplete: []
            }
        });
        gqlOk(author, SAVE, {path: pagePath, language: 'en', results: audit});
        cy.login(colleague.username, colleague.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/contentGovernancePageQuality`);
        cy.contains('pages audited in English', {timeout: 30000}).should('exist');
        cy.contains('Images must have alternative text').should('not.exist');
        cy.contains('tbody tr', 'Shared page').find('td[aria-expanded="false"] svg').first().click();
        cy.contains('tbody tr', 'Images must have alternative text', {timeout: 15000})
            .should('contain.text', 'Ensure img elements have alternative text')
            .and('contain.text', 'Critical')
            .and('contain.text', 'Accessibility')
            .and('contain.text', '3 elements')
            .next()
            .should('contain.text', 'The page has no title')
            .and('contain.text', 'the strongest on-page SEO signal')
            .and('contain.text', 'Serious')
            .and('contain.text', 'SEO');
        cy.logout();
    });

    it('refuses results that are not an audit, and two saves in one request', () => {
        gql(author, SAVE, {path: pagePath, language: 'en', results: '{"page": {}}'})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'saveAudit'], 'The results hold no tab'));
        const twice = `mutation { contentGovernance {
            a: saveAudit(path: "${pagePath}", language: "en", results: ${JSON.stringify(results(0, 0))}) { total: auditedAt }
            b: saveAudit(path: "${pagePath}", language: "en", results: ${JSON.stringify(results(0, 0))}) { total: auditedAt }
        } }`;
        gql(author, twice, {}).then((body: GqlBody) => {
            expect(JSON.stringify(body.errors)).to.contain('Too many saveAudit in one request');
        });
    });

    it('refuses a user who may not open jContent on the site, and a site that switched Page quality off', () => {
        gql(outsider, HISTORY, {path: pagePath, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'pageAudits']));
        gql(outsider, SAVE, {path: pagePath, language: 'en', results: results(0, 1)})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'saveAudit']));
        gql(outsider, SITE, {siteKey, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'siteAudits']));
        gql(author, HISTORY, {path: pagePath, language: 'xx'})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'pageAudits']));
        switchOff(siteKey, ['audit']);
        gql(author, SAVE, {path: pagePath, language: 'en', results: results(0, 1)})
            .then((body: GqlBody) => assertRefused(body, ['contentGovernance', 'saveAudit']));
    });

    it('saves the audit an editor runs in the drawer, opens a colleague\'s drawer on it, and lists the page', () => {
        // The home page renders with the site's own template; the shared page only holds stored audits.
        const pageUrl = `/jahia/jcontent/${siteKey}/en/pages/home`;
        cy.login(author.username, author.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 90000}).should('exist');
        cy.get(DRAWER).contains(`by ${author.username}`, {timeout: 30000}).should('exist');
        cy.logout();

        cy.login(colleague.username, colleague.password);
        cy.visit(pageUrl);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).contains(`by ${author.username}`, {timeout: 30000}).should('exist');
        cy.get(DRAWER).contains('automated rules passed').should('exist');
        cy.get(DRAWER).contains('Analyzing page').should('not.exist');
        cy.focused().type('{esc}');
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps/contentGovernancePageQuality`);
        cy.contains('pages audited in English', {timeout: 30000}).should('contain.text', '2 of');
        cy.contains('Shared page').should('exist');
        cy.contains(`${sitePath}/home`).should('exist');
        cy.contains('button', 'Export CSV').should('not.be.disabled');
        cy.contains('button', 'Export JSON').should('not.be.disabled');
        cy.logout();
    });
});
