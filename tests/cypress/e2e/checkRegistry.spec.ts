import {enableModule} from '@jahia/cypress';
import {
    addNodeTree, asRoot, assertRefused, createTestSite, deleteTestSite, frameCheck, FramePageFinding, framePageCheck,
    FrameViolation, gql, gqlOk, nodeExists
} from '../support/cg';

type RuleStatus = {id: string; evaluator: string; active: boolean; missingCheck: string | null; needs: string | null};
type CheckFinding = {ruleId: string; severity: string; text: string};
type CheckResult = {ruleId: string; passed: boolean; values: string; findings: CheckFinding[]};
type RuleRuntime = {id: string; evaluator: string; runtime: string; active: boolean; blocking: boolean};
type ScanIssue = {ruleId: string; term: string; matchedText: string; folder: string; severity: string};

const RULES = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator active missingCheck needs } } }';
const PAGE_CHECK = `query P($path: String!) {
    contentGovernance { checkPage(path: $path, language: "en") { path results { ruleId passed values findings { ruleId severity text } } } }
}`;
const SCAN_PAGE = `query P($path: String!) {
    complianceScan { scanPage(path: $path, language: "en") { status issues { ruleId term matchedText folder severity } } }
}`;

/** The findings of the results of checkPage, in the order of the rules. */
const findingsOf = (results: CheckResult[]): CheckFinding[] => results.flatMap(result => result.findings);
const RUNTIME = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator runtime active blocking } } }';
const CHECK = `query C($path: String!, $text: String!, $scope: String) {
    contentGovernance { checkText(path: $path, language: "en", text: $text, scope: $scope) { ruleId severity text } }
}`;
const SAVE_LAYOUT = `mutation S($siteKey: String!, $layout: String!) { contentGovernance {
    saveSiteLayout(siteKey: $siteKey, layout: $layout) { saved } } }`;
const RESET_LAYOUT = 'mutation R($siteKey: String!) { contentGovernance { resetSiteLayout(siteKey: $siteKey) { saved } } }';
/** The tree of the system site: the domains, their subdomains and their rules, for every site. */
const TREE = '/sites/systemsite/contents/content-governance';

/** A page with one image that has no text alternative. */
const PAGE_HTML = '<html lang="en"><head><title>Home page</title><meta name="description" content="Home"></head>'
    + '<body><h1>Home</h1><img src="a.png"></body></html>';

/**
 * The check registry: a module registers a check under an id, and a rule node names it as evaluator. The
 * test module (tests/jahia-module) registers `acme.erpPrice` and ships its rule, which the seeder writes in the
 * tree of the system site, and no code of Content Governance names it.
 */
describe('Content Governance - check registry', () => {
    const siteKey = 'cg-checks';
    const sitePath = `/sites/${siteKey}`;
    const imageAlt = `${TREE}/quality/accessibility/html.imageAltMissing`;
    const erpPrice = `${TREE}/compliance/writing/acme.erpPrice`;
    const lazyLoading = `${TREE}/quality/ecodesign/ecodesign.lazyLoading`;

    const rules = (): Cypress.Chainable<RuleStatus[]> => gqlOk(asRoot(), RULES, {siteKey})
        .then(data => data.contentGovernance.contentRules as RuleStatus[]);
    const check = (text: string, scope = 'VALUE'): Cypress.Chainable<CheckFinding[]> =>
        gqlOk(asRoot(), CHECK, {path: `${sitePath}/home`, text, scope})
            .then(data => data.contentGovernance.checkText as CheckFinding[]);
    const setBlocking = (path: string, blocking: boolean): void => {
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${path}") {
            mutateProperty(name: "blocking") { setValue(value: "${blocking}") } } } }`);
    };

    /** The seeder writes the tree when a module starts, so it may finish after the first spec starts. */
    const waitFor = (path: string, attempts = 20): void => {
        nodeExists(path).then(exists => {
            if (!exists && attempts > 0) {
                cy.wait(500);
                waitFor(path, attempts - 1);
            } else {
                expect(exists, `${path} is seeded`).to.eq(true);
            }
        });
    };

    before(() => {
        createTestSite(siteKey);
        enableModule('content-governance-test-module', siteKey);
        waitFor(imageAlt);
        waitFor(erpPrice);
    });

    after(() => {
        // The tree serves every site: put back what a failed test may have left in it
        setBlocking(lazyLoading, false);
        deleteTestSite(siteKey);
    });

    it('shows a rule whose check no module registers as inactive, with the missing check', () => {
        addNodeTree(`${sitePath}/contents`, {
            name: 'brand-rules', type: 'jnt:contentFolder', children: [{
                name: 'acme-tone', primaryNodeType: 'ccs:contentRule', mixins: ['ccsmix:aiCheck'], properties: [
                    {name: 'ruleId', value: 'acme.tone'},
                    {name: 'checkType', value: 'AI'},
                    {name: 'evaluator', value: 'model'},
                    {name: 'subdomain', value: 'writing'},
                    {name: 'advice', value: 'Write in a friendly tone.', language: 'en'}
                ]
            }]
        });
        rules().then(list => {
            const byId = Object.fromEntries(list.map(rule => [rule.id, rule]));
            expect(byId['acme.tone']).to.include({active: false, missingCheck: 'model', needs: 'ai-assistant'});
            expect(byId['acme.erpPrice']).to.include({evaluator: 'acme.erpPrice', active: true, missingCheck: null});
            expect(byId['html.imageAltMissing']).to.include({active: true});
            const inactive = list.filter(rule => !rule.active).map(rule => rule.id);
            expect(inactive, 'every rule of the module has its check').to.deep.eq(['acme.tone']);
        });
    });

    it('runs the check of another module on a value, with no change to Content Governance', () => {
        check('The kit costs 12.50 EUR today.').then(findings => {
            expect(findings).to.deep.include({ruleId: 'acme.erpPrice', severity: 'serious', text: '12.50 EUR'});
        });
        check('The kit is in stock.').then(findings => {
            expect(findings.map(finding => finding.ruleId)).not.to.include('acme.erpPrice');
        });
    });

    it('shows the finding of a check of another module in the Compliance scan, as frame.check gives it', () => {
        const text = 'The kit costs 12.50 EUR today.';
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-price', type: 'jnt:page',
            props: [{name: 'j:templateName', value: 'simple'}, {name: 'jcr:title', language: 'en', value: 'Price page'}],
            children: [{
                name: 'area-main', primaryNodeType: 'jnt:contentList', children: [
                    {name: 'intro', primaryNodeType: 'jnt:text', properties: [{name: 'text', language: 'en', value: text}]}
                ]
            }]
        });
        gqlOk(asRoot(), SCAN_PAGE, {path: `${sitePath}/home/cg-price`}).then(data => {
            const issues = data.complianceScan.scanPage.issues as ScanIssue[];
            // The rule acme.erpPrice belongs to the subdomain writing of Compliance; its term is its label
            expect(issues).to.deep.include({ruleId: 'acme.erpPrice', term: 'Prices from the ERP', matchedText: '12.50 EUR',
                folder: `${TREE}/compliance/writing`, severity: 'serious'});
            frameCheck({siteKey, locale: 'en', text}).then((violations: FrameViolation[]) => {
                expect(issues.map(issue => `${issue.ruleId} ${issue.matchedText}`).sort())
                    .to.deep.eq(violations.map(violation => `${violation.ruleId} ${violation.match}`).sort());
            });
        });
    });

    it('runs an HTML rule as a rule node, which the layout of the site can switch off', () => {
        check(PAGE_HTML, 'PAGE').then(findings => {
            expect(findings).to.deep.include({ruleId: 'html.imageAltMissing', severity: 'critical', text: '<img src="a.png">'});
        });
        gqlOk(asRoot(), SAVE_LAYOUT, {siteKey, layout: JSON.stringify({off: ['html.imageAltMissing']})});
        check(PAGE_HTML, 'PAGE').then(findings => {
            expect(findings.map(finding => finding.ruleId)).not.to.include('html.imageAltMissing');
        });
        gqlOk(asRoot(), RESET_LAYOUT, {siteKey});
        check(PAGE_HTML, 'PAGE').then(findings => {
            expect(findings.map(finding => finding.ruleId)).to.include('html.imageAltMissing');
        });
    });

    it('gives alt text one rule id, and each practice of the drawer one rule', () => {
        rules().then(list => {
            const ids = list.map(rule => rule.id);
            expect(ids).to.include('html.imageAltMissing');
            for (const removed of ['seo.imageAlt', 'geo.imageAlt', 'seo.lang', 'seo.langMatches', 'seo.singleH1',
                'seo.genericAnchors', 'readability.headingSkips', 'html.pageTitleMissing', 'html.pageDescriptionMissing']) {
                expect(ids, removed).not.to.include(removed);
            }
        });
    });

    it('checks a rendered item on the server, with the rules of the drawer and none of the browser', () => {
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-links', type: 'jnt:bigText', props: [{name: 'text', language: 'en',
                value: '<p><img src="a.png"> Read the <a href="http://example.com/report" target="_blank">here</a></p>'}]
        });
        gqlOk(asRoot(), RUNTIME, {siteKey}).then(data => {
            const byId = Object.fromEntries((data.contentGovernance.contentRules as RuleRuntime[]).map(rule => [rule.id, rule]));
            gqlOk(asRoot(), PAGE_CHECK, {path: `${sitePath}/home/cg-links`}).then(page => {
                const findings = findingsOf(page.contentGovernance.checkPage.results as CheckResult[]);
                expect(findings.map(finding => finding.ruleId)).to.include.members(['html.imageAltMissing',
                    'html.linkTextVague', 'links.mixedContent', 'links.blankNoopener', 'health.unpublishedChanges']);
                expect(findings).to.deep.include({ruleId: 'links.mixedContent', severity: 'serious',
                    text: 'http://example.com/report'});
                findings.forEach(finding => {
                    expect(byId[finding.ruleId], finding.ruleId).to.include({active: true});
                    expect(finding.ruleId).not.to.match(/^ecodesign\.|^seo\./);
                });
            });
            gqlOk(asRoot(), PAGE_CHECK, {path: `${sitePath}/home`}).then(page => {
                const results = page.contentGovernance.checkPage.results as CheckResult[];
                results.forEach(result => {
                    expect(byId[result.ruleId], result.ruleId).to.include({active: true});
                    expect(result.passed, result.ruleId).to.eq(result.findings.length === 0);
                    // The browser runs eco-design: the server gives no result for it
                    expect(result.ruleId).not.to.match(/^ecodesign\./);
                });
                const byRule = Object.fromEntries(results.map(result => [result.ruleId, result]));
                expect(byRule, 'the measure rules ran').to.include.keys('seo.tags', 'readability.statistics', 'readability.headings');
                expect(byRule['seo.tags'].passed).to.eq(true);
                expect(JSON.parse(byRule['seo.tags'].values).title.length).to.be.greaterThan(0);
                expect(JSON.parse(byRule['seo.title'].values)).to.have.all.keys('length', 'text');
                expect(JSON.parse(byRule['readability.statistics'].values)).to.include.keys('words', 'readingMinutes');
            });
        });
    });

    it('gives one page the same findings in checkPage and in frame.check', () => {
        const path = `${sitePath}/home`;
        gqlOk(asRoot(), PAGE_CHECK, {path}).then(page => {
            const fromGraphql = findingsOf(page.contentGovernance.checkPage.results as CheckResult[])
                .map(finding => `${finding.ruleId} ${finding.severity} ${finding.text}`);
            expect(fromGraphql, 'the page has findings').not.to.be.empty;
            framePageCheck({siteKey, locale: 'en', path}).then((findings: FramePageFinding[]) => {
                expect(findings.map(finding => `${finding.rule} ${finding.severity} ${finding.text}`)).to.deep.eq(fromGraphql);
            });
        });
    });

    it('refuses a path that climbs out of the site it names', () => {
        const climbing = `${sitePath}/../${siteKey}/home`;
        gql(asRoot(), PAGE_CHECK, {path: climbing})
            .then(body => assertRefused(body, ['contentGovernance', 'checkPage'], 'Invalid path'));
        gql(asRoot(), CHECK, {path: climbing, text: 'A text', scope: 'VALUE'})
            .then(body => assertRefused(body, ['contentGovernance', 'checkText'], 'Invalid path'));
    });

    it('never blocks on a rule of the browser, even when its node sets blocking', () => {
        setBlocking(lazyLoading, true);
        gqlOk(asRoot(), RUNTIME, {siteKey}).then(data => {
            const rule = (data.contentGovernance.contentRules as RuleRuntime[]).find(item => item.id === 'ecodesign.lazyLoading');
            expect(rule).to.include({runtime: 'browser', evaluator: 'ecodesign.lazyLoading', active: true, blocking: false});
        });
        setBlocking(lazyLoading, false);
    });
});
