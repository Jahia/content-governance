import {deleteUser} from '@jahia/cypress';
import {
    TestUser,
    addNodeTree,
    asRoot,
    assertEffectiveUser,
    assertRefused,
    createTestSite,
    createTestUser,
    deleteTestSite,
    gql,
    gqlOk,
    nodeExists
} from '../support/cg';

type Rule = {id: string; evaluator: string; severity: string; blocking: boolean; enabled: boolean; minimum: number | null;
    maximum: number | null; subdomain: string | null; order: number; isNew: boolean; sites: string[]; unknownSubdomain: string | null};
type Subdomain = {id: string; domain: string; label: string; order: number; on: boolean; isNew: boolean; counts: string[]};
type Domain = {id: string; label: string; panel: string; order: number; on: boolean; subdomains: Subdomain[]};

const RULES = `query R($siteKey: String!) { contentGovernance { contentRules(siteKey: $siteKey) {
    id evaluator severity blocking enabled minimum maximum subdomain order isNew sites unknownSubdomain } } }`;
const DOMAINS = `query D($siteKey: String!, $language: String, $includeOff: Boolean) { contentGovernance {
    contentDomains(siteKey: $siteKey, language: $language, includeOff: $includeOff) {
        id label panel order on subdomains { id domain label order on isNew counts } } } }`;
const LAYOUT = 'query L($siteKey: String!) { contentGovernance { siteLayout(siteKey: $siteKey) { saved json off } } }';
const SAVE_LAYOUT = `mutation S($siteKey: String!, $layout: String!) { contentGovernance {
    saveSiteLayout(siteKey: $siteKey, layout: $layout) { saved off } } }`;
const RESET_LAYOUT = 'mutation R($siteKey: String!) { contentGovernance { resetSiteLayout(siteKey: $siteKey) { saved } } }';
const SAVE_LAYOUT_PATH = ['contentGovernance', 'saveSiteLayout'];
const RESET_LAYOUT_PATH = ['contentGovernance', 'resetSiteLayout'];
/** The tree of the system site: the domains, their subdomains and their rules, for every site. */
const TREE = '/sites/systemsite/contents/content-governance';

/**
 * The rules of a site are content: the module seeds its domains, subdomains and rules once, in the tree of the
 * system site, a site arranges that tree with its layout, a site adds a rule of its own anywhere under
 * contents, and one rule id names one rule per site.
 */
describe('Content Governance - content rules', () => {
    const siteKey = 'cg-rules';
    const customFolder = `/sites/${siteKey}/contents/brand-rules`;
    const canonical = `${TREE}/geo/seo/seo.canonical`;
    const addedSubdomain = `${TREE}/compliance/acme.tone`;
    const editor: TestUser = {username: 'cg-rules-editor', password: 'cgEditor1234'};

    const rules = (): Cypress.Chainable<Rule[]> => gqlOk(asRoot(), RULES, {siteKey})
        .then(data => data.contentGovernance.contentRules as Rule[]);
    const rule = (id: string): Cypress.Chainable<Rule | undefined> => rules().then(list => list.find(item => item.id === id));
    const domains = (includeOff = false): Cypress.Chainable<Domain[]> => gqlOk(asRoot(), DOMAINS, {siteKey, includeOff})
        .then(data => data.contentGovernance.contentDomains as Domain[]);
    const setSites = (path: string, sites: string[]): void => {
        gqlOk(asRoot(), `mutation { jcr { mutateNode(pathOrId: "${path}") { mutateProperty(name: "sites") {
            setValues(values: ${JSON.stringify(sites)}) } } } }`);
    };
    const removeIfThere = (path: string): void => {
        nodeExists(path).then(exists => {
            if (exists) {
                gqlOk(asRoot(), `mutation { jcr { deleteNode(pathOrId: "${path}") } }`);
            }
        });
    };

    /** The seeder writes the tree when the module starts, so it may finish after the first spec starts. */
    const waitForSeed = (attempts = 20): void => {
        nodeExists(`${TREE}/geo/seo/seo.title`).then(exists => {
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
        // privileged lets the editor through the GraphQL API filter, so the module's own check is what refuses
        createTestUser(editor, `/sites/${siteKey}`, ['editor', 'privileged']);
        waitForSeed();
    });

    after(() => {
        // The tree serves every site: put back what a failed test may have left in it
        setSites(canonical, ['*']);
        removeIfThere(addedSubdomain);
        deleteUser(editor.username);
        deleteTestSite(siteKey);
    });

    it('seeds the rules of the module once, in the tree of the system site, each under its subdomain with its evaluator, its check type and its sites', () => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${TREE}/geo/seo/seo.title") {
            properties(names: ["evaluator", "checkType", "minimum", "maximum"]) { name value } mixinTypes { name }
            subdomain: property(name: "subdomain") { value } sites: property(name: "sites") { values }
            parent { primaryNodeType { name } subdomainId: property(name: "subdomainId") { value } } } } }`)
            .then(data => {
                const node = data.jcr.nodeByPath;
                const values = Object.fromEntries(node.properties.map((p: {name: string; value: string}) => [p.name, p.value]));
                expect(values).to.deep.eq({evaluator: 'threshold', checkType: 'CODE', minimum: '30.0', maximum: '60.0'});
                expect(node.mixinTypes.map((m: {name: string}) => m.name), 'the mixin of the check type holds the limits')
                    .to.include('ccsmix:codeCheck');
                expect(node.subdomain, 'the parent node gives the subdomain').to.eq(null);
                expect(node.parent.primaryNodeType.name).to.eq('ccs:contentSubdomain');
                expect(node.parent.subdomainId.value).to.eq('seo');
                expect(node.sites.values).to.deep.eq(['*']);
            });
        nodeExists(`/sites/${siteKey}/contents/content-insights/content-governance/rules/seo.title`)
            .should('eq', false);
        rules().then(list => {
            expect(list.find(item => item.id === 'seo.title')).to.deep.include({evaluator: 'threshold', minimum: 30, maximum: 60,
                subdomain: 'seo', sites: ['*'], unknownSubdomain: null, enabled: true, isNew: false});
            expect(list.every(item => Boolean(item.evaluator)), 'every rule names its evaluator').to.eq(true);
            expect(list.filter(item => item.unknownSubdomain !== null).map(item => item.id), 'every rule has a place').to.deep.eq([]);
        });
    });

    it('seeds the domains and the subdomains in the tree of the system site, and gives the hierarchy in a language', () => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${TREE}/geo/seo") {
            primaryNodeType { name } domain: property(name: "domain") { value }
            parent { name primaryNodeType { name } } } } }`)
            .then(data => {
                const node = data.jcr.nodeByPath;
                expect(node.primaryNodeType.name).to.eq('ccs:contentSubdomain');
                expect(node.domain, 'the parent node gives the domain').to.eq(null);
                expect(node.parent).to.deep.eq({name: 'geo', primaryNodeType: {name: 'ccs:contentDomain'}});
            });
        gqlOk(asRoot(), DOMAINS, {siteKey, language: 'fr'}).then(data => {
            const list = data.contentGovernance.contentDomains as Domain[];
            expect(list.map(domain => domain.id)).to.deep.eq(['quality', 'geo', 'compliance']);
            expect(list[1]).to.include({label: 'SEO et GEO', panel: 'governance', on: true});
            expect(list[1].subdomains.map(subdomain => subdomain.id)).to.deep.eq(
                ['geo.citations', 'seo', 'geo.score', 'geo.content', 'geo.files', 'geo.schema', 'geo.crawlers']);
            expect(list[1].subdomains.find(subdomain => subdomain.id === 'geo.score')?.counts, 'the rules of the GEO score')
                .to.include('seo.title');
            expect(list[2].subdomains.map(subdomain => subdomain.id)).to.deep.eq(['readability', 'terms', 'glossary', 'writing']);
        });
    });

    it('keeps a rule whose sites leave the site out, switched off', () => {
        setSites(canonical, ['another-site']);
        rule('seo.canonical').then(found => expect(found).to.include({enabled: false, subdomain: 'seo'}));
        setSites(canonical, ['*']);
        rule('seo.canonical').its('enabled').should('eq', true);
    });

    it('switches a rule or a subdomain off with the layout of the site, which a reset removes', () => {
        gqlOk(asRoot(), SAVE_LAYOUT, {siteKey, layout: JSON.stringify({off: ['seo.canonical', 'links']})})
            .its('contentGovernance.saveSiteLayout').should('deep.eq', {saved: true, off: ['seo.canonical', 'links']});
        gqlOk(asRoot(), LAYOUT, {siteKey}).its('contentGovernance.siteLayout.saved').should('eq', true);
        rules().then(list => {
            expect(list.find(item => item.id === 'seo.canonical')).to.include({enabled: false});
            expect(list.find(item => item.id === 'links.broken'), 'its subdomain is off').to.include({enabled: false});
            expect(list.find(item => item.id === 'seo.title')).to.include({enabled: true});
        });
        domains().then(list => expect(list.find(domain => domain.id === 'quality')?.subdomains.map(subdomain => subdomain.id))
            .not.to.include('links'));
        domains(true).then(list => expect(list.find(domain => domain.id === 'quality')?.subdomains
            .find(subdomain => subdomain.id === 'links')).to.include({on: false}));

        gql(asRoot(), SAVE_LAYOUT, {siteKey, layout: '{"off": ["a b"]}'})
            .then(body => expect(JSON.stringify(body.errors), 'an id that is not one').to.contain('a b'));

        gqlOk(asRoot(), RESET_LAYOUT, {siteKey}).its('contentGovernance.resetSiteLayout.saved').should('eq', false);
        gqlOk(asRoot(), LAYOUT, {siteKey}).its('contentGovernance.siteLayout').should('deep.eq', {saved: false, json: null, off: []});
        rule('seo.canonical').its('enabled').should('eq', true);
    });

    it('orders and moves the items with the layout of the site, and marks what the layout does not place as new', () => {
        const layout = {domains: [{id: 'compliance', subdomains: [{id: 'seo', rules: ['seo.metaDescription', 'seo.title']}]}]};
        gqlOk(asRoot(), SAVE_LAYOUT, {siteKey, layout: JSON.stringify(layout)});

        domains().then(list => {
            expect(list[0].id, 'the domain that the layout places comes first').to.eq('compliance');
            expect(list[0].subdomains[0]).to.include({id: 'seo', domain: 'compliance', isNew: false});
            expect(list.find(domain => domain.id === 'geo')?.subdomains.map(subdomain => subdomain.id)).not.to.include('seo');
            expect(list.find(domain => domain.id === 'quality')?.subdomains.find(subdomain => subdomain.id === 'links'))
                .to.include({isNew: true});
        });
        rules().then(list => {
            expect(list.find(item => item.id === 'seo.metaDescription')).to.include({subdomain: 'seo', order: 0, isNew: false, enabled: true});
            expect(list.find(item => item.id === 'seo.title')).to.include({order: 1, isNew: false});
            expect(list.find(item => item.id === 'seo.canonical')).to.include({isNew: true, enabled: true});
        });

        gqlOk(asRoot(), RESET_LAYOUT, {siteKey});
        domains().then(list => expect(list.map(domain => domain.id)).to.deep.eq(['quality', 'geo', 'compliance']));
    });

    it('lets only a site administrator save or reset the layout, and keeps the layout of the site', () => {
        gqlOk(asRoot(), SAVE_LAYOUT, {siteKey, layout: JSON.stringify({off: ['seo.canonical']})});

        assertEffectiveUser(editor);
        gql(editor, SAVE_LAYOUT, {siteKey, layout: JSON.stringify({off: ['seo.title']})})
            .then(body => assertRefused(body, SAVE_LAYOUT_PATH));
        gql(editor, RESET_LAYOUT, {siteKey}).then(body => assertRefused(body, RESET_LAYOUT_PATH));

        gqlOk(asRoot(), LAYOUT, {siteKey}).its('contentGovernance.siteLayout')
            .should('deep.include', {saved: true, off: ['seo.canonical']});
        gqlOk(asRoot(), RESET_LAYOUT, {siteKey});
    });

    it('leaves the tree of the system site to a server administrator, and has no reset for it', () => {
        assertEffectiveUser(editor);
        gql(editor, SAVE_LAYOUT, {siteKey: 'systemsite', layout: JSON.stringify({off: []})})
            .then(body => assertRefused(body, SAVE_LAYOUT_PATH));
        // A layout that names nothing changes no node of the tree
        gqlOk(asRoot(), SAVE_LAYOUT, {siteKey: 'systemsite', layout: JSON.stringify({off: []})});
        gql(asRoot(), RESET_LAYOUT, {siteKey: 'systemsite'}).then(body => assertRefused(body, RESET_LAYOUT_PATH,
            'The defaults of the system site have no reset: save the order and the switches'));
    });

    it('refuses a layout that names an id twice, and saves nothing', () => {
        const layout = {domains: [{id: 'geo', subdomains: [{id: 'seo', rules: ['seo.title', 'seo.title']}]}]};
        gql(asRoot(), SAVE_LAYOUT, {siteKey, layout: JSON.stringify(layout)})
            .then(body => assertRefused(body, SAVE_LAYOUT_PATH, 'The layout names seo.title twice'));
        gqlOk(asRoot(), LAYOUT, {siteKey}).its('contentGovernance.siteLayout.saved').should('eq', false);
    });

    it('shows a subdomain that an administrator adds to the tree, and ignores one that a site writes outside it', () => {
        addNodeTree(`${TREE}/compliance`, {
            name: 'acme.tone', type: 'ccs:contentSubdomain', props: [
                {name: 'subdomainId', value: 'acme.tone'},
                {name: 'label', value: 'Tone', language: 'en'}
            ]
        });
        // A site arranges the tree with its layout: a subdomain node of its own changes nothing
        addNodeTree(`/sites/${siteKey}/contents`, {
            name: 'brand-domains', type: 'jnt:contentFolder', children: [{
                name: 'voice', primaryNodeType: 'ccs:contentSubdomain', properties: [
                    {name: 'subdomainId', value: 'acme.voice'},
                    {name: 'label', value: 'Voice', language: 'en'}
                ]
            }]
        });
        domains().then(list => {
            const compliance = list.find(domain => domain.id === 'compliance');
            expect(compliance?.subdomains.find(subdomain => subdomain.id === 'acme.tone')).to.include({domain: 'compliance'});
            expect(list.flatMap(domain => domain.subdomains.map(subdomain => subdomain.id))).not.to.include('acme.voice');
        });
        removeIfThere(addedSubdomain);
    });

    it('applies a rule that the site creates, and refuses a second rule with the same id', () => {
        addNodeTree(`/sites/${siteKey}/contents`, {
            name: 'brand-rules', type: 'jnt:contentFolder', children: [{
                name: 'acme-tone', primaryNodeType: 'ccs:contentRule', mixins: ['ccsmix:aiCheck'], properties: [
                    {name: 'ruleId', value: 'acme.tone'},
                    {name: 'checkType', value: 'AI'},
                    {name: 'evaluator', value: 'model'},
                    {name: 'subdomain', value: 'writing'},
                    {name: 'severity', value: 'minor'},
                    {name: 'blocking', value: 'true'},
                    {name: 'advice', value: 'Write in a friendly tone.', language: 'en'}
                ]
            }]
        });
        rule('acme.tone').then(found => {
            expect(found).to.include({evaluator: 'model', severity: 'minor', blocking: true, subdomain: 'writing'});
        });

        gql(asRoot(), `mutation { jcr { addNode(parentPathOrId: "${customFolder}", name: "again",
            primaryNodeType: "ccs:contentRule", properties: [{name: "ruleId", value: "acme.tone"}]) { uuid } } }`)
            .then(body => {
                expect(body.errors, 'the duplicate is refused').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('The rule acme.tone already exists');
            });

        gql(asRoot(), `mutation { jcr { addNode(parentPathOrId: "${customFolder}", name: "title",
            primaryNodeType: "ccs:contentRule", properties: [{name: "ruleId", value: "seo.title"}]) { uuid } } }`)
            .then(body => {
                expect(body.errors, 'a rule of the system site is refused in a site').to.have.length.greaterThan(0);
                expect(body.errors[0].message).to.contain('The rule seo.title is a rule of the system site');
            });
    });
});
