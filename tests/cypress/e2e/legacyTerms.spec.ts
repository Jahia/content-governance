import {enableModule} from '@jahia/cypress';
import {MODULE_NAME, addNodeTree, asRoot, createTestSite, deleteTestSite, gql, gqlOk, nodeExists} from '../support/cg';

const NODE = `query N($path: String!) { jcr { nodeByPath(path: $path) {
    uuid primaryNodeType { name }
    contributeTypes: property(name: "j:contributeTypes") { values }
    description: property(name: "jcr:description") { value }
    ruleId: property(name: "ruleId") { value }
    kind: property(name: "kind") { value }
    pattern: property(name: "pattern") { value }
    patternForms: property(name: "patternForms") { values }
    patternLanguage: property(name: "patternLanguage") { value }
    replacement: property(name: "replacement") { value }
    replacementForms: property(name: "replacementForms") { values }
    ruleLanguage: property(name: "ruleLanguage") { value }
    adviceFr: property(name: "advice", language: "fr") { value }
    adviceEn: property(name: "advice", language: "en") { value }
} } }`;

const SCAN_PAGE = `query P($path: String!) {
    complianceScan { scanPage(path: $path, language: "fr") { issues { ruleId folder matchedText alternative } } }
}`;

type Issue = {ruleId: string; folder: string; matchedText: string; alternative: string | null};

/**
 * The upgrade from a version that kept the terms in glossaries: the module converts each Compliance term and
 * each term of the frame into a content rule in place, when it starts, and the converted rules apply.
 */
describe('Content Governance - conversion of the glossaries of an older version', () => {
    const siteKey = 'cg-legacy';
    const sitePath = `/sites/${siteKey}`;
    const legal = `${sitePath}/contents/legacy/legal-fr`;
    const frame = `${sitePath}/contents/legacy/frame`;
    const pagePath = `${sitePath}/home/cg-legacy-page`;
    const node = (path: string) => gqlOk(asRoot(), NODE, {path}).its('jcr.nodeByPath');

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/contents`, {
            name: 'legacy', type: 'jnt:contentFolder', children: [
                {name: 'legal-fr', primaryNodeType: 'ccs:glossary', properties: [
                    {name: 'jcr:title', language: 'fr', value: 'Juridique'},
                    {name: 'language', value: 'fr'},
                    {name: 'description', value: 'Kept by the legal team'}
                ], children: [
                    {name: 'garanti', primaryNodeType: 'ccs:glossaryTerm', properties: [
                        {name: 'term', value: 'garanti'},
                        {name: 'matchMode', value: 'word'},
                        {name: 'reason', value: 'Promesse de résultat'}
                    ]},
                    {name: 'rembours', primaryNodeType: 'ccs:glossaryTerm', properties: [
                        {name: 'term', value: 'rembours'},
                        {name: 'matchMode', value: 'prefix'},
                        {name: 'alternative', value: 'satisfait'}
                    ]}
                ]},
                {name: 'frame', primaryNodeType: 'jnt:contentFolder', mixins: ['jmix:contributeMode'], properties: [
                    {name: 'j:contributeTypes', values: ['efnt:glossaryTerm']}
                ], children: [
                    {name: 'access-token', primaryNodeType: 'efnt:glossaryTerm', properties: [
                        {name: 'term', value: 'access token'},
                        {name: 'termLanguage', value: 'en'},
                        {name: 'termForms', values: ['access tokens']},
                        {name: 'translation', value: 'jeton d\'accès'},
                        {name: 'translationLanguage', value: 'fr'},
                        {name: 'translationForms', values: ['jetons d\'accès']},
                        {name: 'note', value: 'Use the official term.'}
                    ]},
                    {name: 'auth-token', primaryNodeType: 'efnt:glossaryTerm', properties: [
                        {name: 'term', value: 'access token'},
                        {name: 'termLanguage', value: 'en'},
                        {name: 'translation', value: 'jeton d\'authentification'},
                        {name: 'translationLanguage', value: 'fr'},
                        {name: 'status', value: 'forbidden'}
                    ]}
                ]}
            ]
        });
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-legacy-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'fr', value: 'Page'}
            ],
            children: [{
                name: 'area-main', primaryNodeType: 'jnt:contentList',
                children: [{name: 'intro', primaryNodeType: 'jnt:text', properties: [
                    {name: 'text', language: 'fr', value: 'Satisfait ou remboursé, garanti. Créez des access tokens, pas un jeton d\'authentification.'}
                ]}]
            }]
        });
        // The module converts the terms of an older version when it starts.
        cy.runProvisioningScript({script: [{stopBundle: 'content-governance'}, {startBundle: 'content-governance'}]});
        cy.waitUntil(() => gql(asRoot(), NODE, {path: `${legal}/garanti`})
            .then(body => (body.data?.jcr as {nodeByPath?: {primaryNodeType: {name: string}}})?.nodeByPath?.primaryNodeType.name === 'ccs:contentRule'),
        {timeout: 60000, interval: 2000, errorMsg: 'the module did not convert the glossary terms'});
        // The GraphQL fields of the module come back once the module registered them again.
        cy.waitUntil(() => gql(asRoot(), SCAN_PAGE, {path: pagePath}).then(body => !body.errors),
            {timeout: 60000, interval: 2000, errorMsg: 'the compliance scan did not answer after the restart'});
    });

    after(() => deleteTestSite(siteKey));

    it('converts a Compliance term into a rule of the language of its glossary, with the stars of its match mode', () => {
        node(`${legal}/garanti`).should(rule => {
            expect(rule.ruleId.value).to.eq('compliance.garanti');
            expect(rule.kind.value).to.eq('forbidden');
            expect(rule.pattern.value).to.eq('garanti');
            expect(rule.ruleLanguage.value).to.eq('fr');
            expect(rule.adviceFr.value, 'the reason stays in the language of the glossary').to.eq('Promesse de résultat');
            expect(rule.adviceEn).to.be.null;
        });
        node(`${legal}/rembours`).should(rule => {
            expect(rule.kind.value).to.eq('replace');
            expect(rule.pattern.value).to.eq('rembours*');
            expect(rule.replacement.value).to.eq('satisfait');
        });
    });

    it('converts the glossary into a folder that takes rules, with its description', () => {
        node(legal).should(folder => {
            expect(folder.primaryNodeType.name).to.eq('jnt:contentFolder');
            expect(folder.contributeTypes.values).to.deep.eq(['ccs:contentRule']);
            expect(folder.description.value).to.eq('Kept by the legal team');
        });
        node(frame).its('contributeTypes.values').should('deep.eq', ['ccs:contentRule']);
    });

    it('converts a translation of the frame into a replace rule of its language, and a forbidden one toward it', () => {
        node(`${frame}/access-token`).should(rule => {
            expect(rule.ruleId.value).to.eq('glossary.access-token');
            expect(rule.kind.value).to.eq('replace');
            expect(rule.pattern.value).to.eq('access token');
            expect(rule.patternForms.values).to.deep.eq(['access tokens']);
            expect(rule.patternLanguage.value).to.eq('en');
            expect(rule.replacement.value).to.eq('jeton d\'accès');
            expect(rule.replacementForms.values).to.deep.eq(['jetons d\'accès']);
            expect(rule.ruleLanguage.value).to.eq('fr');
            expect(rule.adviceEn.value).to.eq('Use the official term.');
        });
        node(`${frame}/auth-token`).should(rule => {
            expect(rule.kind.value).to.eq('replace');
            expect(rule.pattern.value).to.eq('jeton d\'authentification');
            expect(rule.replacement.value).to.eq('jeton d\'accès');
            expect(rule.patternLanguage).to.be.null;
        });
    });

    it('applies the converted rules in the Compliance section', () => {
        gqlOk(asRoot(), SCAN_PAGE, {path: pagePath}).its('complianceScan.scanPage.issues').should((issues: Issue[]) => {
            const found = Object.fromEntries(issues.map(issue => [issue.ruleId, issue]));
            expect(found['compliance.garanti']).to.include({folder: legal, matchedText: 'garanti'});
            expect(found['compliance.rembours']).to.include({matchedText: 'remboursé', alternative: 'satisfait'});
            expect(found['glossary.access-token']).to.include({folder: frame, matchedText: 'access tokens'});
            expect(found['glossary.auth-token']).to.include({matchedText: 'jeton d\'authentification', alternative: 'jeton d\'accès'});
        });
    });
});

/**
 * The example rule of a new site: a site that keeps compliance terms when it enables the module gets none,
 * wherever its terms are.
 */
describe('Content Governance - example rule of a site that keeps terms', () => {
    const siteKey = 'cg-legacy-seed';
    const sitePath = `/sites/${siteKey}`;
    const SEEDED = `query S($path: String!) { jcr { nodeByPath(path: $path) { seeded: property(name: "glossarySeeded") { value } } } }`;

    before(() => {
        createTestSite(siteKey, false);
        addNodeTree(`${sitePath}/contents`, {
            name: 'legal', type: 'jnt:contentFolder', children: [{
                name: 'free', primaryNodeType: 'ccs:contentRule', properties: [
                    {name: 'ruleId', value: 'compliance.free'},
                    {name: 'domain', value: 'compliance'},
                    {name: 'pattern', value: 'free'}
                ]
            }]
        });
        enableModule(MODULE_NAME, siteKey);
    });

    after(() => deleteTestSite(siteKey));

    it('marks the site as seeded and creates no example', () => {
        // The seeder runs on the event of the enabled module, so the run waits for its flag.
        cy.waitUntil(() => gql(asRoot(), SEEDED, {path: `${sitePath}/content-governance`})
            .then(body => (body.data?.jcr as {nodeByPath?: {seeded?: {value: string}}})?.nodeByPath?.seeded?.value === 'true'),
        {timeout: 30000, interval: 1000, errorMsg: 'the seeder did not flag the site'});
        nodeExists(`${sitePath}/contents/compliance-glossaries`).should('eq', false);
    });
});
