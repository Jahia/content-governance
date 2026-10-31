import {deleteUser} from '@jahia/cypress';
import {
    GqlBody,
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
    switchOff
} from '../support/cg';

const SCAN_PAGE = `query P($path: String!, $language: String!) {
    complianceScan { scanPage(path: $path, language: $language) {
        pagePath status scannedContents glossaries { path termCount }
        issues { nodePath propertyName term matchedText reason alternative glossaryPath }
    } }
}`;

const SCAN_SITE = `query S($siteKey: String!, $language: String!) {
    complianceScan { scanSite(siteKey: $siteKey, language: $language) {
        siteKey status scannedPages locations { path kind issueCount } terms { term uses locations }
    } }
}`;

const GLOSSARY = `query G($path: String!) { jcr { nodeByPath(path: $path) {
    primaryNodeType { name }
    contributeTypes: property(name: "j:contributeTypes") { values }
    children(typesFilter: {types: ["ccs:glossary"]}) { nodes {
        name language: property(name: "language") { value }
        terms: children(typesFilter: {types: ["ccs:glossaryTerm"]}) { nodes { term: property(name: "term") { value } } }
    } }
} } }`;

type Issue = {nodePath: string; term: string; matchedText: string; glossaryPath: string};

/**
 * The compliance scan: the example glossary created when a site enables the module, a page scan as
 * someone who may edit the page, a site scan as someone who may open jContent, and the refusals.
 */
describe('Content Governance - compliance scan', () => {
    const siteKey = 'cg-compliance';
    const sitePath = `/sites/${siteKey}`;
    const otherSiteKey = 'cg-compliance-other';
    const folderPath = `${sitePath}/contents/compliance-glossaries`;
    const pagePath = `${sitePath}/home/cg-page`;
    const textPath = `${pagePath}/area-main/intro`;
    const editor: TestUser = {username: 'cg-compliance-editor', password: 'cgEditor1234'};
    const outsider: TestUser = {username: 'cg-compliance-outsider', password: 'cgOutsider1234'};
    let exampleTerm = '';

    before(() => {
        createTestSite(siteKey);
        createTestUser(editor, sitePath, ['editor', 'privileged']);
        // An editor elsewhere passes the GraphQL API filter, so the module's own check is what refuses
        createTestSite(otherSiteKey);
        createTestUser(outsider, `/sites/${otherSiteKey}`, ['editor', 'privileged']);
        // The example glossary is created by a listener once the module is enabled, so the run waits for it.
        cy.waitUntil(() => gql(asRoot(), GLOSSARY, {path: folderPath})
            .then(body => Boolean((body.data?.jcr as {nodeByPath?: unknown} | undefined)?.nodeByPath)),
        {timeout: 30000, interval: 1000, errorMsg: 'the example glossary was not created'});
        gqlOk(asRoot(), GLOSSARY, {path: folderPath}).then(data => {
            exampleTerm = data.jcr.nodeByPath.children.nodes[0].terms.nodes[0].term.value;
            addNodeTree(`${sitePath}/home`, {
                name: 'cg-page', type: 'jnt:page',
                props: [
                    {name: 'j:templateName', value: 'simple'},
                    {name: 'jcr:title', language: 'en', value: 'Compliance page'}
                ],
                children: [{
                    name: 'area-main', primaryNodeType: 'jnt:contentList',
                    children: [{name: 'intro', primaryNodeType: 'jnt:text', properties: [
                        {name: 'text', language: 'en', value: `To learn more, ${exampleTerm} and read on.`}
                    ]}]
                }]
            });
        });
    });

    afterEach(() => switchOff(siteKey, []));

    after(() => {
        [editor, outsider].forEach(user => deleteUser(user.username));
        deleteTestSite(siteKey);
        deleteTestSite(otherSiteKey);
    });

    it('creates an example glossary in the default language when the module is enabled', () => {
        gqlOk(asRoot(), GLOSSARY, {path: folderPath}).its('jcr.nodeByPath').should(folder => {
            expect(folder.primaryNodeType.name).to.eq('jnt:contentFolder');
            expect(folder.contributeTypes.values, 'the folder offers glossaries only').to.deep.eq(['ccs:glossary']);
            expect(folder.children.nodes).to.have.length(1);
            const [glossary] = folder.children.nodes;
            expect(glossary.name).to.eq('glossary-en');
            expect(glossary.language.value).to.eq('en');
            expect(glossary.terms.nodes).to.have.length(1);
            expect(glossary.terms.nodes[0].term.value).to.not.be.empty;
        });
    });

    it('finds the example term on a page, for an editor of the page', () => {
        assertEffectiveUser(editor);
        gqlOk(editor, SCAN_PAGE, {path: pagePath, language: 'en'}).its('complianceScan.scanPage').should(report => {
            expect(report.pagePath).to.eq(pagePath);
            expect(report.glossaries.map((g: {path: string}) => g.path)).to.deep.eq([`${folderPath}/glossary-en`]);
            const issue = report.issues.find((i: Issue) => i.nodePath === textPath);
            expect(issue, 'the text holding the term is reported').to.exist;
            expect(issue.term).to.eq(exampleTerm);
            expect(issue.matchedText.toLowerCase()).to.eq(exampleTerm.toLowerCase());
            expect(issue.glossaryPath).to.eq(`${folderPath}/glossary-en`);
        });
    });

    it('applies the glossary of the language scanned only', () => {
        gqlOk(editor, SCAN_PAGE, {path: pagePath, language: 'fr'}).its('complianceScan.scanPage').should(report => {
            expect(report.glossaries).to.be.empty;
            expect(report.issues).to.be.empty;
        });
    });

    it('reports the page in the site scan, for a user who may open jContent', () => {
        gqlOk(editor, SCAN_SITE, {siteKey, language: 'en'}).its('complianceScan.scanSite').should(report => {
            const page = report.locations.find((l: {path: string}) => l.path === pagePath);
            expect(page, 'the page is a location of the report').to.exist;
            expect(page.issueCount).to.be.greaterThan(0);
            const usage = report.terms.find((t: {term: string}) => t.term === exampleTerm);
            expect(usage.uses).to.be.greaterThan(0);
        });
    });

    it('refuses a user who cannot edit the page or open jContent', () => {
        assertEffectiveUser(outsider);
        gql(outsider, SCAN_PAGE, {path: pagePath, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['complianceScan', 'scanPage']));
        gql(outsider, SCAN_SITE, {siteKey, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['complianceScan', 'scanSite']));
    });

    it('refuses to scan once the site switches the compliance section off', () => {
        switchOff(siteKey, ['compliance']);
        const off = 'The compliance scan is not enabled on this site';
        gql(editor, SCAN_PAGE, {path: pagePath, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['complianceScan', 'scanPage'], off));
        gql(editor, SCAN_SITE, {siteKey, language: 'en'})
            .then((body: GqlBody) => assertRefused(body, ['complianceScan', 'scanSite'], off));
    });
});
