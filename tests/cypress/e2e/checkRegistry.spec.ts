import {enableModule} from '@jahia/cypress';
import {
    addNodeTree, asRoot, assertRefused, createTestSite, deleteTestSite, FramePageFinding, framePageCheck, gql, gqlOk, nodeExists
} from '../support/cg';

type RuleStatus = {id: string; evaluator: string; active: boolean; missingCheck: string | null; needs: string | null};
type CheckFinding = {ruleId: string; severity: string; text: string};
type RuleRuntime = {id: string; evaluator: string; active: boolean; blocking: boolean};

const RULES = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator active missingCheck needs } } }';
const PAGE_CHECK = `query P($path: String!) {
    contentGovernance { checkPage(path: $path, language: "en") { path findings { ruleId severity text } facts } }
}`;
const RUNTIME = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator active blocking } } }';
const CHECK = `query C($path: String!, $text: String!, $scope: String) {
    contentGovernance { checkText(path: $path, language: "en", text: $text, scope: $scope) { ruleId severity text } }
}`;

/** A page with one image that has no text alternative. */
const PAGE_HTML = '<html lang="en"><head><title>Home page</title><meta name="description" content="Home"></head>'
    + '<body><h1>Home</h1><img src="a.png"></body></html>';

/**
 * The check registry: a module registers a check under an id, and a rule node names it as evaluator. The
 * test module (tests/jahia-module) registers `acme.erpPrice` and ships its rule, and no code of Content
 * Governance names it.
 */
describe('Content Governance - check registry', () => {
    const siteKey = 'cg-checks';
    const sitePath = `/sites/${siteKey}`;
    const insights = `${sitePath}/contents/content-insights`;
    const imageAlt = `${insights}/content-governance/rules/html.imageAltMissing`;

    const rules = (): Cypress.Chainable<RuleStatus[]> => gqlOk(asRoot(), RULES, {siteKey})
        .then(data => data.contentGovernance.contentRules as RuleStatus[]);
    const check = (text: string, scope = 'VALUE'): Cypress.Chainable<CheckFinding[]> =>
        gqlOk(asRoot(), CHECK, {path: `${sitePath}/home`, text, scope})
            .then(data => data.contentGovernance.checkText as CheckFinding[]);
    const setEnabled = (path: string, enabled: boolean): void => {
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${path}") {
            mutateProperty(name: "enabled") { setValue(value: "${enabled}") } } } }`);
    };

    /** The seeder runs on the event that enabling a module sends, so it may finish after the site does. */
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
        waitFor(`${insights}/content-governance-test-module/rules/acme.erpPrice`);
    });

    after(() => {
        deleteTestSite(siteKey);
    });

    it('shows a rule whose check no module registers as inactive, with the missing check', () => {
        addNodeTree(insights, {
            name: 'site', type: 'jnt:contentFolder', children: [{
                name: 'rules', primaryNodeType: 'jnt:contentFolder', children: [{
                    name: 'acme-tone', primaryNodeType: 'ccs:contentRule', properties: [
                        {name: 'ruleId', value: 'acme.tone'},
                        {name: 'evaluator', value: 'model'},
                        {name: 'advice', value: 'Write in a friendly tone.', language: 'en'}
                    ]
                }]
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

    it('runs an HTML rule as a rule node, which the site can switch off', () => {
        check(PAGE_HTML, 'PAGE').then(findings => {
            expect(findings).to.deep.include({ruleId: 'html.imageAltMissing', severity: 'critical', text: '<img src="a.png">'});
        });
        setEnabled(imageAlt, false);
        check(PAGE_HTML, 'PAGE').then(findings => {
            expect(findings.map(finding => finding.ruleId)).not.to.include('html.imageAltMissing');
        });
        setEnabled(imageAlt, true);
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
                const findings = page.contentGovernance.checkPage.findings as CheckFinding[];
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
                (page.contentGovernance.checkPage.findings as CheckFinding[]).forEach(finding => {
                    expect(byId[finding.ruleId], finding.ruleId).to.include({active: true});
                    expect(finding.ruleId).not.to.match(/^ecodesign\./);
                });
                const facts = JSON.parse(page.contentGovernance.checkPage.facts);
                expect(facts).to.have.all.keys('seo', 'readability', 'links', 'jahia');
                expect(facts.seo.title.length).to.be.greaterThan(0);
            });
        });
    });

    it('gives one page the same findings in checkPage and in frame.check', () => {
        const path = `${sitePath}/home`;
        gqlOk(asRoot(), PAGE_CHECK, {path}).then(page => {
            const fromGraphql = (page.contentGovernance.checkPage.findings as CheckFinding[])
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
        const lazyLoading = `${insights}/content-governance/rules/ecodesign.lazyLoading`;
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${lazyLoading}") {
            mutateProperty(name: "blocking") { setValue(value: "true") } } } }`);
        gqlOk(asRoot(), RUNTIME, {siteKey}).then(data => {
            const rule = (data.contentGovernance.contentRules as RuleRuntime[]).find(item => item.id === 'ecodesign.lazyLoading');
            expect(rule).to.include({active: true, blocking: false});
        });
    });
});
