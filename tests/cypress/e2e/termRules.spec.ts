import {deleteUser, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {
    FrameViolation,
    GqlBody,
    TestUser,
    addNodeTree,
    asRoot,
    createTestSite,
    createTestUser,
    deleteTestSite,
    frameCheck,
    gql,
    gqlOk
} from '../support/cg';

const SCAN_PAGE = `query P($path: String!, $language: String!) {
    complianceScan { scanPage(path: $path, language: $language) {
        status truncated glossaries { path title termCount }
        issues { nodePath ruleId severity folder term matchedText reason alternative }
    } }
}`;

const CHECK = `query C($path: String!, $text: String!) {
    contentGovernance { checkText(path: $path, language: "en", text: $text, scope: "VALUE") { ruleId text } }
}`;

const SET_ADVICE = `mutation A($path: String!, $advice: String!) { jcr { mutateNode(pathOrId: $path) {
    mutateProperty(name: "advice") { setValue(value: $advice, language: "en") }
} } }`;

type Issue = {nodePath: string; ruleId: string; severity: string; folder: string; term: string; matchedText: string; reason: string};

/** A forbidden term of the check pattern, in English, whose mixin holds the fields of the check type PATTERN. */
const termRule = (name: string, ruleId: string, pattern: string, advice: string) => ({
    name, primaryNodeType: 'ccs:contentRule', mixins: ['ccsmix:patternCheck'], properties: [
        {name: 'ruleId', value: ruleId},
        {name: 'checkType', value: 'PATTERN'},
        {name: 'evaluator', value: 'pattern'},
        {name: 'severity', value: 'serious'},
        {name: 'kind', value: 'forbidden'},
        {name: 'pattern', value: pattern},
        {name: 'matchMode', value: 'term'},
        {name: 'ruleLanguage', value: 'en'},
        {name: 'advice', value: advice, language: 'en'}
    ]
});

const TEXT = 'Guaranteed results, seamlessly integrated.';

/**
 * Two lists of terms in one rule model: the legal team and the brand team each keep their terms in a
 * folder of rules, with the permissions of that folder. The Compliance section, the evaluator pattern and
 * the tool frame.check report the terms of both folders with their rule ids and their folders.
 */
describe('Content Governance - term rules of two teams', () => {
    const siteKey = 'cg-terms';
    const sitePath = `/sites/${siteKey}`;
    const lists = `${sitePath}/contents/term-lists`;
    const legal = `${lists}/legal`;
    const brand = `${lists}/brand`;
    const pagePath = `${sitePath}/home/cg-terms-page`;
    const textPath = `${pagePath}/area-main/intro`;
    const brandEditor: TestUser = {username: 'cg-brand-editor', password: 'cgBrand1234'};

    before(() => {
        createTestSite(siteKey);
        addNodeTree(`${sitePath}/contents`, {
            name: 'term-lists', type: 'jnt:contentFolder', children: [
                {name: 'legal', primaryNodeType: 'jnt:contentFolder', children: [
                    termRule('guaranteed', 'compliance.guaranteed', 'guarantee*', 'A promise of result needs the approval of the legal team.')
                ]},
                {name: 'brand', primaryNodeType: 'jnt:contentFolder', children: [
                    termRule('seamless', 'brand.seamless', 'seamless*', 'Say what the integration does instead.')
                ]}
            ]
        });
        addNodeTree(`${sitePath}/home`, {
            name: 'cg-terms-page', type: 'jnt:page',
            props: [
                {name: 'j:templateName', value: 'simple'},
                {name: 'jcr:title', language: 'en', value: 'Terms page'}
            ],
            children: [{
                name: 'area-main', primaryNodeType: 'jnt:contentList',
                children: [{name: 'intro', primaryNodeType: 'jnt:text', properties: [
                    {name: 'text', language: 'en', value: TEXT}
                ]}]
            }]
        });
        // frame.check reads the published rules.
        publishAndWaitJobEnding(lists, ['en']);
        // The editor of the brand team has a role on the folder of the brand only.
        createTestUser(brandEditor, brand, []);
        grantRoles(brand, ['editor'], brandEditor.username, 'USER');
    });

    after(() => {
        deleteUser(brandEditor.username);
        deleteTestSite(siteKey);
    });

    it('reports the term of each folder in the Compliance section, with its rule id and its folder', () => {
        gqlOk(asRoot(), SCAN_PAGE, {path: pagePath, language: 'en'}).its('complianceScan.scanPage').should(report => {
            expect(report.status).to.eq('ISSUES');
            const issues = (report.issues as Issue[]).filter(issue => issue.nodePath === textPath);
            const byRule = Object.fromEntries(issues.map(issue => [issue.ruleId, issue]));
            expect(byRule['compliance.guaranteed']).to.include({folder: legal, severity: 'serious', matchedText: 'Guaranteed'});
            expect(byRule['compliance.guaranteed'].reason).to.contain('legal team');
            expect(byRule['brand.seamless']).to.include({folder: brand, matchedText: 'seamlessly'});
            const folders = Object.fromEntries(report.glossaries.map((folder: {path: string; termCount: number}) =>
                [folder.path, folder.termCount]));
            expect(folders).to.deep.include({[legal]: 1, [brand]: 1});
        });
    });

    it('reports the same terms in frame.check, with their rule ids and their folders', () => {
        gqlOk(asRoot(), SCAN_PAGE, {path: pagePath, language: 'en'}).then(data => {
            const scanned = (data.complianceScan.scanPage.issues as Issue[])
                .filter(issue => issue.nodePath === textPath)
                .map(issue => `${issue.ruleId} ${issue.folder}`)
                .sort();
            frameCheck({siteKey, locale: 'en', text: TEXT}).should((violations: FrameViolation[]) => {
                const checked = violations.map(violation => `${violation.ruleId} ${violation.folder}`).sort();
                expect(checked).to.deep.eq([`brand.seamless ${brand}`, `compliance.guaranteed ${legal}`]);
                expect(checked, 'frame.check and the Compliance section report the same rules').to.deep.eq(scanned);
            });
        });
    });

    it('reports the same terms through the evaluator pattern of the check registry', () => {
        gqlOk(asRoot(), CHECK, {path: textPath, text: TEXT})
            .its('contentGovernance.checkText').should((findings: {ruleId: string; text: string}[]) => {
                const texts = Object.fromEntries(findings.map(finding => [finding.ruleId, finding.text]));
                expect(texts).to.deep.include({'compliance.guaranteed': 'Guaranteed', 'brand.seamless': 'seamlessly'});
            });
    });

    it('says that the report is truncated when a value is longer than the rules read', () => {
        addNodeTree(`${pagePath}/area-main`, {name: 'long', type: 'jnt:text', props: [
            {name: 'text', language: 'en', value: `${'Lorem ipsum. '.repeat(8000)} Guaranteed.`}
        ]});
        gqlOk(asRoot(), SCAN_PAGE, {path: pagePath, language: 'en'}).its('complianceScan.scanPage.truncated').should('eq', true);
    });

    it('lets an editor of the brand folder change its rules, and not the rules of the legal folder', () => {
        gql(brandEditor, SET_ADVICE, {path: `${brand}/seamless`, advice: 'Name what the integration does.'})
            .then((body: GqlBody) => expect(body.errors, 'the brand editor edits the brand folder').to.be.undefined);
        gql(brandEditor, SET_ADVICE, {path: `${legal}/guaranteed`, advice: 'Anything goes.'})
            .then((body: GqlBody) => expect(body.errors, 'the brand editor cannot edit the legal folder').to.not.be.undefined);
        gqlOk(asRoot(), `query { jcr { nodeByPath(path: "${legal}/guaranteed") {
            advice: property(name: "advice", language: "en") { value } } } }`)
            .its('jcr.nodeByPath.advice.value').should('contain', 'legal team');
    });
});
