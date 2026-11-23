import {addNodeTree, asRoot, createTestSite, deleteTestSite, gqlOk, nodeExists} from '../support/cg';

type CheckType = 'PATTERN' | 'AI' | 'CODE';

/** The fields of each check type, by the name that jContent gives a field: its type, then its property. */
const FIELDS: Record<CheckType, string[]> = {
    PATTERN: ['ccsmix:patternCheck_kind', 'ccsmix:patternCheck_pattern', 'ccsmix:patternCheck_matchMode',
        'ccsmix:patternCheck_replacement'],
    AI: ['ccsmix:aiCheck_minimum'],
    CODE: ['ccsmix:codeCheck_unit', 'ccsmix:codeCheck_minimum', 'ccsmix:codeCheck_maximum']
};
const CHECK_TYPE = 'ccs:contentRule_checkType';
const EDITOR = '[aria-labelledby="dialog-content-editor"]';

/**
 * The form of a content rule in jContent shows the fields of its check type only: each check type keeps
 * its fields on a mixin of jmix:dynamicFieldset, which the choice of the check type adds.
 */
describe('Content Governance - the form of a content rule', () => {
    const siteKey = 'cg-rule-form';
    const insights = `/sites/${siteKey}/contents/content-insights`;

    /** The seeder runs on the event that enabling the module sends, so it may finish after the site does. */
    const waitForSeed = (attempts = 20): void => {
        nodeExists(`${insights}/content-governance/rules/seo.title`).then(exists => {
            if (!exists && attempts > 0) {
                cy.wait(500);
                waitForSeed(attempts - 1);
            } else {
                expect(exists, 'the module seeded its rules').to.eq(true);
            }
        });
    };

    /**
     * Opens the editor of a node in jContent, as a link to the node in edit does, or the form that creates a
     * content rule in a folder.
     */
    const openEditor = (path: string, mode: 'edit' | 'create' = 'edit'): void => {
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${path}") { uuid } } }`).then(data => {
            const uuid: string = data.jcr.nodeByPath.uuid;
            const create = mode === 'create' ? ',contentType:\'ccs:contentRule\',name:acme-new' : '';
            cy.login();
            cy.visit(`/jahia/jcontent/${siteKey}/en/content-folders/contents#(contentEditor:!((formKey:modal_0,`
                + `isFullscreen:!t,lang:en,mode:${mode},site:${siteKey},uilang:en,uuid:'${uuid}'${create})))`);
        });
        cy.get(EDITOR, {timeout: 30000}).find(`[data-sel-content-editor-field="${CHECK_TYPE}"]`).should('be.visible');
    };

    /** The form shows the fields of one check type, and none of another. */
    const assertFieldsOf = (type: CheckType): void => {
        (Object.keys(FIELDS) as CheckType[]).forEach(other => {
            FIELDS[other].filter(field => !FIELDS[type].includes(field)).forEach(field => {
                cy.get(EDITOR).find(`[data-sel-content-editor-field="${field}"]`).should('not.exist');
            });
        });
        FIELDS[type].forEach(field => {
            cy.get(EDITOR).find(`[data-sel-content-editor-field="${field}"]`).scrollIntoView().should('be.visible');
        });
    };

    before(() => {
        createTestSite(siteKey);
        waitForSeed();
        addNodeTree(insights, {
            name: 'site', type: 'jnt:contentFolder', children: [{
                name: 'rules', primaryNodeType: 'jnt:contentFolder', children: [{
                    name: 'acme-mail', primaryNodeType: 'ccs:contentRule', mixins: ['ccsmix:patternCheck'], properties: [
                        {name: 'ruleId', value: 'acme.mail'},
                        {name: 'checkType', value: 'PATTERN'},
                        {name: 'evaluator', value: 'pattern'},
                        {name: 'subdomains', values: ['writing']},
                        {name: 'kind', value: 'replace'},
                        {name: 'pattern', value: 'mail'},
                        {name: 'matchMode', value: 'term'},
                        {name: 'replacement', value: 'e-mail'},
                        {name: 'advice', value: 'Write e-mail.', language: 'en'}
                    ]
                }, {
                    name: 'acme-tone', primaryNodeType: 'ccs:contentRule', mixins: ['ccsmix:aiCheck'], properties: [
                        {name: 'ruleId', value: 'acme.tone'},
                        {name: 'checkType', value: 'AI'},
                        {name: 'evaluator', value: 'model'},
                        {name: 'subdomains', values: ['writing']},
                        {name: 'minimum', value: '80'},
                        {name: 'advice', value: 'Write in a friendly tone.', language: 'en'}
                    ]
                }]
            }]
        });
    });

    after(() => deleteTestSite(siteKey));

    ([
        ['CODE', 'content-governance/rules/seo.title'],
        ['PATTERN', 'site/rules/acme-mail'],
        ['AI', 'site/rules/acme-tone']
    ] as [CheckType, string][]).forEach(([type, path]) => {
        it(`shows the fields of the check type ${type} only`, () => {
            openEditor(`${insights}/${path}`);
            cy.get(EDITOR).find(`[data-sel-content-editor-field="${CHECK_TYPE}"] .moonstone-dropdown`).should('contain.text',
                {PATTERN: 'Pattern', AI: 'AI', CODE: 'Code'}[type]);
            assertFieldsOf(type);
            // A headless run captures the top of the viewport, so the check type goes to the top of the form.
            cy.get(EDITOR).find(`[data-sel-content-editor-field="${CHECK_TYPE}"]`)
                .then($field => $field[0].scrollIntoView({block: 'start'}));
            cy.screenshot(`rule-form-${type}`, {capture: 'viewport'});
        });
    });

    it('creates a rule of the check type PATTERN by default, with its mixin', () => {
        openEditor(`${insights}/site/rules`, 'create');
        assertFieldsOf('PATTERN');
        cy.get(EDITOR).find('[data-sel-content-editor-field="ccs:contentRule_ruleId"] input').type('acme.new');
        cy.get(EDITOR).find('[data-sel-content-editor-field="ccsmix:patternCheck_pattern"] input').type('synergy');
        cy.get('[data-sel-role="createButton"]').click();
        cy.get('[role="alertdialog"]').should('contain', 'Content successfully created');

        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${insights}/site/rules/acme-new") { mixinTypes { name }
            properties(names: ["checkType", "pattern"]) { name value } } } }`).then(data => {
            const node = data.jcr.nodeByPath;
            const values = Object.fromEntries(node.properties.map((p: {name: string; value: string}) => [p.name, p.value]));
            expect(node.mixinTypes.map((m: {name: string}) => m.name)).to.include('ccsmix:patternCheck');
            expect(values).to.deep.eq({checkType: 'PATTERN', pattern: 'synergy'});
        });
    });

    it('shows the fields of another check type when the editor picks it, and saves the rule with its mixin', () => {
        const path = `${insights}/content-governance/rules/seo.title`;
        openEditor(path);
        assertFieldsOf('CODE');

        cy.get(EDITOR).find(`[data-sel-content-editor-field="${CHECK_TYPE}"] .moonstone-dropdown`).click();
        cy.get('.moonstone-menu:not(.moonstone-hidden) .moonstone-menuItem[data-value="AI"]').click();
        assertFieldsOf('AI');
        cy.get(EDITOR).find('[data-sel-content-editor-field="ccsmix:aiCheck_minimum"] input').type('75');
        cy.get('[data-sel-role="submitSave"]').click();
        cy.get('[role="alertdialog"]').should('contain', 'Content successfully saved');

        // AI and CODE both declare minimum: jContent takes the mixin of CODE off before it adds the one of AI.
        gqlOk(asRoot(), `{ jcr { nodeByPath(path: "${path}") { mixinTypes { name }
            properties(names: ["checkType", "minimum", "unit"]) { name value } } } }`).then(data => {
            const node = data.jcr.nodeByPath;
            const values = Object.fromEntries(node.properties.map((p: {name: string; value: string}) => [p.name, p.value]));
            expect(node.mixinTypes.map((m: {name: string}) => m.name)).to.include('ccsmix:aiCheck').and.not.include('ccsmix:codeCheck');
            expect(values).to.deep.eq({checkType: 'AI', minimum: '75.0'});
        });
    });
});
