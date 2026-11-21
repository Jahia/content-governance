import {addNodeTree, asRoot, createTestSite, deleteTestSite, gql, gqlOk, nodeExists} from '../support/cg';

type Rule = {id: string; evaluator: string; severity: string; blocking: boolean; minimum: number | null; maximum: number | null;
    subdomains: string[]; sites: string[]; unknownSubdomains: string[] | null};
type Subdomain = {id: string; domain: string; label: string; order: number};
type Domain = {id: string; label: string; panel: string; order: number; subdomains: Subdomain[]};

const RULES = 'query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) { id evaluator severity blocking minimum maximum subdomains sites unknownSubdomains } } }';
const DOMAINS = `query D($siteKey: String!, $language: String) { contentGovernance { contentDomains(siteKey: $siteKey, language: $language) {
    id label panel order subdomains { id domain label order } } } }`;
const SYSTEM_INSIGHTS = '/sites/systemsite/contents/content-insights';

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

    it('seeds the rules of the module as nodes, each with its evaluator, its subdomains and its sites', () => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${customFolder}/content-governance/rules/seo.title") {
            properties(names: ["evaluator", "minimum", "maximum"]) { name value }
            subdomains: property(name: "subdomains") { values } sites: property(name: "sites") { values } } } }`)
            .then(data => {
                const node = data.jcr.nodeByPath;
                const values = Object.fromEntries(node.properties.map((p: {name: string; value: string}) => [p.name, p.value]));
                expect(values).to.deep.eq({evaluator: 'threshold', minimum: '30.0', maximum: '60.0'});
                expect(node.subdomains.values).to.deep.eq(['seo', 'geo.score']);
                expect(node.sites.values).to.deep.eq(['*']);
            });
        rules().then(list => {
            expect(list.find(rule => rule.id === 'seo.title')).to.deep.include({evaluator: 'threshold', minimum: 30, maximum: 60,
                subdomains: ['seo', 'geo.score'], sites: ['*'], unknownSubdomains: null});
            expect(list.every(rule => Boolean(rule.evaluator)), 'every rule names its evaluator').to.eq(true);
            expect(list.filter(rule => rule.unknownSubdomains !== null).map(rule => rule.id), 'every rule has a place').to.deep.eq([]);
        });
    });

    it('seeds the domains and the subdomains in the system site, and gives the hierarchy in a language', () => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${SYSTEM_INSIGHTS}/content-governance/subdomains/seo") {
            primaryNodeType { name } domain: property(name: "domain") { value } } } }`)
            .then(data => {
                expect(data.jcr.nodeByPath.primaryNodeType.name).to.eq('ccs:contentSubdomain');
                expect(data.jcr.nodeByPath.domain.value).to.eq('geo');
            });
        gqlOk(asRoot(), DOMAINS, {siteKey, language: 'fr'}).then(data => {
            const domains = data.contentGovernance.contentDomains as Domain[];
            expect(domains.map(domain => domain.id)).to.deep.eq(['quality', 'geo', 'compliance']);
            expect(domains[1]).to.include({label: 'SEO et GEO', panel: 'governance'});
            expect(domains[1].subdomains.map(subdomain => subdomain.id)).to.deep.eq(
                ['geo.citations', 'seo', 'geo.score', 'geo.content', 'geo.files', 'geo.schema', 'geo.crawlers']);
            expect(domains[2].subdomains.map(subdomain => subdomain.id)).to.deep.eq(['readability', 'terms', 'glossary', 'writing']);
        });
    });

    it('drops a rule whose sites leave this site out, and takes a subdomain that the site adds', () => {
        const ruleNode = `${customFolder}/content-governance/rules/seo.canonical`;
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${ruleNode}") { mutateProperty(name: "sites") {
            setValues(values: ["another-site"]) } } } }`);
        rules().then(list => expect(list.map(rule => rule.id)).not.to.include('seo.canonical'));
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${ruleNode}") { mutateProperty(name: "sites") {
            setValues(values: ["*"]) } } } }`);
        rules().then(list => expect(list.map(rule => rule.id)).to.include('seo.canonical'));

        // A site adds a subdomain under a shipped domain with a node of its own, in jContent.
        addNodeTree(`/sites/${siteKey}/contents`, {
            name: 'brand-domains', type: 'jnt:contentFolder', children: [{
                name: 'tone', primaryNodeType: 'ccs:contentSubdomain', properties: [
                    {name: 'subdomainId', value: 'acme.tone'},
                    {name: 'domain', value: 'compliance'},
                    {name: 'label', value: 'Tone', language: 'en'}
                ]
            }]
        });
        gqlOk(asRoot(), DOMAINS, {siteKey}).then(data => {
            const compliance = (data.contentGovernance.contentDomains as Domain[]).find(domain => domain.id === 'compliance');
            expect(compliance?.subdomains.map(subdomain => subdomain.id)).to.include('acme.tone');
        });
    });

    it('applies a rule that the site creates, and refuses a second rule with the same id', () => {
        addNodeTree(customFolder, {
            name: 'site', type: 'jnt:contentFolder', children: [{
                name: 'rules', primaryNodeType: 'jnt:contentFolder', children: [{
                    name: 'acme-tone', primaryNodeType: 'ccs:contentRule', properties: [
                        {name: 'ruleId', value: 'acme.tone'},
                        {name: 'evaluator', value: 'model'},
                        {name: 'subdomains', values: ['writing']},
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
