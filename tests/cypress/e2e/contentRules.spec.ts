import {addNodeTree, asRoot, createTestSite, deleteTestSite, gql, gqlOk, nodeExists} from '../support/cg';

type Rule = {id: string; evaluator: string; severity: string; blocking: boolean; minimum: number | null; maximum: number | null};

const RULES = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator severity blocking minimum maximum } } }';

/**
 * The rules of a site are content: the module seeds its own rules as nodes, a site adds a rule of its
 * own anywhere under contents, and one rule id names one rule per site.
 */
describe('Content Governance - content rules', () => {
    const siteKey = 'cg-rules';
    const customFolder = `/sites/${siteKey}/contents/content-insights`;

    const rules = (): Cypress.Chainable<Rule[]> => gqlOk(asRoot(), RULES, {siteKey})
        .then(data => data.contentGovernance.contentRules as Rule[]);

    /** The seeder runs on the event that enabling the module sends, so it may finish after the site does. */
    const waitForSeed = (attempts = 20): void => {
        nodeExists(`${customFolder}/content-governance/rules/seo.title`).then(exists => {
            if (!exists && attempts > 0) {
                cy.wait(500);
                waitForSeed(attempts - 1);
            } else {
                expect(exists, 'the module seeded its rules').to.eq(true);
            }
        });
    };

    before(() => {
        createTestSite(siteKey);
        waitForSeed();
    });

    after(() => {
        deleteTestSite(siteKey);
    });

    it('seeds the rules of the module as nodes, each with its evaluator', () => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${customFolder}/content-governance/rules/seo.title") {
            properties(names: ["evaluator", "minimum", "maximum", "domain"]) { name value } } } }`)
            .then(data => {
                const values = Object.fromEntries(data.jcr.nodeByPath.properties.map(p => [p.name, p.value]));
                expect(values).to.deep.eq({evaluator: 'threshold', minimum: '30.0', maximum: '60.0', domain: 'seo'});
            });
        rules().then(list => {
            expect(list.find(rule => rule.id === 'seo.title')).to.include({evaluator: 'threshold', minimum: 30, maximum: 60});
            expect(list.every(rule => Boolean(rule.evaluator)), 'every rule names its evaluator').to.eq(true);
        });
    });

    it('applies a rule that the site creates, and refuses a second rule with the same id', () => {
        addNodeTree(customFolder, {
            name: 'site', type: 'jnt:contentFolder', children: [{
                name: 'rules', primaryNodeType: 'jnt:contentFolder', children: [{
                    name: 'acme-tone', primaryNodeType: 'ccs:contentRule', properties: [
                        {name: 'ruleId', value: 'acme.tone'},
                        {name: 'evaluator', value: 'model'},
                        {name: 'severity', value: 'minor'},
                        {name: 'blocking', value: 'true'},
                        {name: 'advice', value: 'Write in a friendly tone.', language: 'en'}
                    ]
                }]
            }]
        });
        rules().then(list => {
            expect(list.find(rule => rule.id === 'acme.tone')).to.include({evaluator: 'model', severity: 'minor', blocking: true});
        });

        gql(asRoot(), `mutation { jcr { addNode(parentPathOrId: "${customFolder}/site/rules", name: "again",
            primaryNodeType: "ccs:contentRule", properties: [{name: "ruleId", value: "seo.title"}]) { uuid } } }`)
            .then(body => {
                expect(body.errors, 'the duplicate is refused').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('The rule seo.title already exists');
            });
    });
});
