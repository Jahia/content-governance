import {deleteUser} from '@jahia/cypress';
import {TestUser, addNodeTree, asRoot, createTestSite, createTestUser, deleteTestSite, gqlOk} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';

/**
 * What axe-core reports must reach the Page quality audit. Every axe-core update (always to its latest
 * release: .agents/README.md, "Invariants") adds here a case for each rule its release notes turn on
 * by default or extend, so a rule the module stops running fails this spec.
 *
 * | axe-core | rule                        | case in FIXTURE                              |
 * |----------|-----------------------------|----------------------------------------------|
 * | any      | image-alt                   | an image without alt                         |
 * | any      | link-name                   | a link with no text                          |
 * | 4.14     | label-content-name-mismatch | a link whose aria-label leaves out its text  |
 *
 * The fixture is appended to the page the audit's preview frame loads, not stored as content: a
 * template set may sanitise rich text when rendering it (classic-templates drops aria-label and adds
 * alt=""), and the cases must reach axe as written.
 */
const FIXTURE = '<div id="cg-a11y-fixture">'
    + '<a href="#cg-report" aria-label="Open the annual report">Download</a>'
    + '<a href="#cg-empty"></a>'
    + '<img src="/files/cg-missing.png">'
    + '</div>';
const EXPECTED_RULES = ['image-alt', 'link-name', 'label-content-name-mismatch'];

/**
 * Audits are shared: a page audited by an earlier test opens on that stored audit. A test that needs
 * its own measurement presses Re-run once the drawer is ready (enabled once nothing is measuring).
 */
const freshAudit = () => {
    cy.get(DRAWER).contains('button', 'Re-run', {timeout: 120000}).should('not.be.disabled').click();
};

/**
 * Readability per page language: the formula of a language that has one, no score (rather than
 * the English one) for a language without, characters instead of words where words are not
 * spaced. The scores themselves are unit-tested (PageChecksTest). The server checks the page, so
 * each text is content of the page, in its language, and not a change of the preview.
 */
const READABILITY = [
    {lang: 'en', expect: 'Readability score (Flesch)', text: 'The team writes short pages. Each page answers one question. Readers find what they need and leave. Short sentences help everyone read faster. Plain words work better than jargon. We test every page with real readers before it goes live.'},
    {lang: 'de', expect: 'Readability score (Amstad)', text: 'Das Team schreibt kurze Seiten. Jede Seite beantwortet eine Frage. Die Leser finden schnell, was sie suchen. Kurze Sätze helfen allen Menschen beim Lesen. Einfache Wörter sind besser als Fachsprache. Wir testen jede Seite mit echten Lesern.'},
    {lang: 'sv', expect: 'Readability score (LIX)', text: 'Teamet skriver korta sidor. Varje sida svarar på en enda fråga. Läsarna hittar snabbt det de söker. Korta meningar hjälper alla att läsa snabbare. Enkla ord fungerar bättre än fackspråk. Vi testar varje sida med riktiga läsare.'},
    {lang: 'ru', expect: 'No readability formula exists for Russian here', text: 'Команда пишет короткие страницы. Каждая страница отвечает на один вопрос. Читатели быстро находят то, что ищут. Короткие предложения помогают всем читать быстрее. Простые слова лучше, чем жаргон. Мы проверяем каждую страницу с настоящими читателями.'},
    {lang: 'ja', expect: 'Japanese is written without spaces between words', text: '私たちは短いページを書きます。各ページは一つの質問に答えます。読者は必要な情報をすぐに見つけます。'}
];

/** Headings that skip a level and a heading without text, as content of a page the server renders. */
const OUTLINE = '<h2>Outline section</h2><h4>Outline detail</h4><h3></h3>';

const CHECK_PAGE = `query P($path: String!) {
    contentGovernance { checkPage(path: $path, language: "en") { findings { ruleId } } }
}`;

/** A page of the test site, with the given content items in its main area. */
const page = (name: string, items: Record<string, unknown>[]) => ({
    name, type: 'jnt:page',
    props: [{name: 'j:templateName', value: 'simple'}, {name: 'jcr:title', language: 'en', value: name}],
    children: [{name: 'area-main', primaryNodeType: 'jnt:contentList', children: items}]
});

describe('Content Governance - Page quality audit', () => {
    const siteKey = 'cg-audit';
    const admin: TestUser = {username: 'cg-audit-admin', password: 'cgAdmin1234'};

    before(() => {
        createTestSite(siteKey, true, `en,fr,${READABILITY.map(r => r.lang).filter(l => l !== 'en').join(',')}`);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
        addNodeTree(`/sites/${siteKey}/home`, page('cg-outline', [{name: 'outline', primaryNodeType: 'jnt:bigText',
            properties: [{name: 'text', language: 'en', value: OUTLINE}]}]));
        addNodeTree(`/sites/${siteKey}/home`, page('cg-readability', [{name: 'text', primaryNodeType: 'jnt:text',
            properties: READABILITY.map(({lang, text}) => ({name: 'text', language: lang, value: text}))}]));
    });

    beforeEach(() => {
        // jContent's own ResizeObserver reads an unmounted node when the page goes away at sign-out
        // (jcontent 3.7): only that error is ignored, any error of the module still fails the test.
        cy.on('uncaught:exception', error => !(error.message.includes('offsetWidth')
            && String(error.stack).includes('/modules/jcontent/')));
    });

    afterEach(() => cy.logout());

    after(() => {
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('runs the axe-core version the module declares, and reports what it finds', () => {
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${FIXTURE}</body>`);
            });
        }).as('preview');
        cy.wrap<string>(Cypress.env('AXE_CORE_RANGE'), {log: false}).should('be.a', 'string').then((range: string) => {
            const declared = range.replace(/^\D+/, '');
            cy.login(admin.username, admin.password);
            cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
            cy.contains('button', BUTTON, {timeout: 30000}).click();
            cy.get(DRAWER).should('be.visible');
            cy.wait('@preview', {timeout: 30000});
            // The Accessibility tab opens first. The bundled engine is the declared minimum: a lockfile
            // ahead of package.json fails here.
            cy.get(DRAWER).contains(`axe-core ${declared}`, {timeout: 60000}).should('exist');
            EXPECTED_RULES.forEach(rule => {
                cy.get(DRAWER).contains('a', new RegExp(`^${rule}$`)).should('exist');
            });
        });
    });

    it('puts the most serious finding first in "Fix first", and opens its tab', () => {
        const fixFirst = 'section[aria-label="Fix first"]';
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${FIXTURE}</body>`);
            });
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 90000}).should('exist');
        cy.get('#audit-tab-seo').click();
        cy.get(DRAWER).contains('automated rules passed').should('not.exist');
        cy.get(fixFirst).contains('button', 'Show').click();
        // The image without alt of the fixture: a critical violation, ahead of every other finding
        cy.get('#content-governance-fix-first li').first().should('contain.text', 'Critical').and('contain.text', 'alternative text');
        // Red is for what is critical or serious: the fixture's image without alt, on its tab and its section
        cy.get('#audit-tab-accessibility').find('.moonstone-badge').should('exist');
        cy.get('#content-governance-section-audit').find('.moonstone-badge').should('exist');
        cy.get('#content-governance-fix-first li').each($finding => {
            expect($finding.text(), 'Fix first lists critical and serious findings only').to.match(/Critical|Serious/);
        });
        cy.get('#content-governance-fix-first li').first().contains('button', 'Open').click();
        // The docked drawer is as tall as the page under edit: the tab's results may start below its fold
        cy.get(DRAWER).contains('automated rules passed').scrollIntoView().should('be.visible');
    });

    // Beside Page Builder the page is on screen: the preview starts hidden (behind the results, where
    // it is still measured) and a finding is marked in the page under edit when it is there.
    it('hides the preview beside Page Builder and highlights a finding in the page under edit, else in the preview', () => {
        const LINK = '<div id="cg-a11y-fixture"><a href="#cg-report" aria-label="Open the annual report">Download</a></div>';
        const marked = (el: HTMLElement) => expect(el.style.outline, 'marked').to.contain('3px');
        const ruleDetails = (rule: string) => cy.get(DRAWER).contains('a', new RegExp(`^${rule}$`)).closest('details')
            .then($details => {
                ($details[0] as HTMLDetailsElement).open = true;
            });
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${FIXTURE}</body>`);
            });
        });
        // The page under edit gets the link only: the image without alt is in the preview alone
        cy.intercept('GET', `**/cms/editframe/default/en/sites/${siteKey}/home.html*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${LINK}</body>`);
            });
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.get('iframe[data-sel-role="page-builder-frame-active"]', {timeout: 30000})
            .its('0.contentDocument.body').find('#cg-a11y-fixture', {timeout: 30000}).should('exist');
        cy.contains('button', BUTTON).click();
        cy.get(DRAWER).contains('button', 'Show preview').should('exist');
        freshAudit();
        cy.get(DRAWER).contains('a', /^image-alt$/, {timeout: 90000}).should('exist');
        cy.get(DRAWER).contains('button', 'Show preview').should('exist');

        ruleDetails('label-content-name-mismatch').contains('button', 'Highlight on the page').click();
        cy.get('iframe[data-sel-role="page-builder-frame-active"]').its('0.contentDocument.body')
            .find('#cg-a11y-fixture a').should($a => marked($a[0]));
        cy.get(DRAWER).contains('button', 'Show preview').should('exist');

        ruleDetails('image-alt').contains('button', 'Highlight on the page').click();
        cy.get(DRAWER).contains('button', 'Hide preview').should('exist');
        cy.get(`${DRAWER} iframe`).its('0.contentDocument.body').find('#cg-a11y-fixture img').should($img => marked($img[0]));
        cy.get('iframe[data-sel-role="page-builder-frame-active"]').its('0.contentDocument.body')
            .find('#cg-a11y-fixture a').should($a => expect($a[0].style.outline, 'one mark at a time').to.eq(''));
    });

    // Timings are measured on the editing preview, in the editor's browser: they cannot match the live
    // site, so they are plain figures, with no colour band and no finding of their own.
    it('shows the Web Vitals timings as indicative figures, without a band or a finding', () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 90000}).should('exist');
        cy.get('#audit-tab-vitals').click();
        cy.get('#audit-tab-panel').should('contain.text', 'Indicative figures').and('contain.text', 'LCP').within(() => {
            ['Needs improvement', 'Poor', 'Main content renders slowly', 'Slow server response', 'Layout shifts while loading'].forEach(text => {
                cy.contains(text).should('not.exist');
            });
        });
    });

    // The Readability tab counted skipped levels without saying which heading skips: the outline lists
    // every heading of the main content, and what is wrong with it.
    it('lists the headings as an outline, with the level a heading skips and a heading without text', () => {
        const outline = `${DRAWER} ol[aria-label="Heading outline"]`;
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home/cg-outline`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 90000}).should('exist');
        cy.get('#audit-tab-readability').click();
        cy.contains(`${outline} li`, 'Outline section', {timeout: 30000})
            .should('contain.text', 'H2').and('not.contain.text', 'Skips');
        cy.contains(`${outline} li`, 'Outline detail')
            .should('contain.text', 'H4').and('contain.text', 'Skips from H2 to H4');
        cy.contains(`${outline} li`, '(no text)').should('contain.text', 'H3').and('contain.text', 'Empty');
        cy.get(DRAWER).contains('li', 'Skipped heading levels').find('strong')
            .should($count => expect(Number($count.text())).to.be.greaterThan(0));

        // The heading is content of the page: it is marked in the page under edit
        cy.contains(`${outline} li`, 'Outline detail').find('button').click();
        cy.get('iframe[data-sel-role="page-builder-frame-active"]').its('0.contentDocument.body').contains('h4', 'Outline detail')
            .should($heading => expect($heading[0].style.outline, 'marked').to.contain('3px'));
    });

    READABILITY.forEach(({lang, expect}) => {
        it(`scores readability for a page in ${lang}`, () => {
            cy.login(admin.username, admin.password);
            cy.visit(`/jahia/jcontent/${siteKey}/${lang}/pages/home/cg-readability`);
            cy.contains('button', BUTTON, {timeout: 30000}).click();
            freshAudit();
            cy.get('#audit-tab-readability', {timeout: 60000}).click();
            cy.get(DRAWER).contains(expect, {timeout: 60000}).should('exist');
            if (lang === 'ja') {
                cy.get(DRAWER).contains('Characters').should('exist');
                cy.get(DRAWER).contains('Readability score').should('not.exist');
            }
        });
    });

    // The drawer lists what the server finds on the page, under the same rule ids as checkPage and
    // frame.check: each tab shows the id of each rule it reports.
    it('shows every finding of the server check of the page, under its rule id', () => {
        const path = `/sites/${siteKey}/home/cg-outline`;
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home/cg-outline`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 90000}).should('exist');
        gqlOk(asRoot(), CHECK_PAGE, {path}).then(data => {
            const expected = [...new Set((data.contentGovernance.checkPage.findings as {ruleId: string}[]).map(f => f.ruleId))];
            expect(expected, 'the page breaks some rules').to.include('html.headingLevelSkipped');
            const shown: string[] = [];
            ['accessibility', 'seo', 'readability', 'links', 'jahia'].forEach(tab => {
                cy.get(`#audit-tab-${tab}`).click();
                cy.get('#audit-tab-panel code').each($code => {
                    shown.push($code.text());
                });
            });
            cy.wrap(shown).should(ids => expect(ids).to.include.members(expected));
        });
    });

    // A step that fails costs its own tabs only. The image holds the frame's load event (the audit
    // starts once the document is parsed, after 20 s); the server check fails, and the tabs it fills
    // say so while the accessibility tab shows its results.
    it('audits a page whose server check fails, and says which check did not finish', () => {
        const hang = {delay: 120000, statusCode: 204};
        cy.intercept('GET', '**/files/cg-never-answers.png*', req => req.reply(hang)).as('image');
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', '<p><img src="/files/cg-never-answers.png" alt="Waiting"></p></body>');
            });
        });
        cy.intercept('POST', '**/modules/graphql', req => {
            if (req.body?.operationName === 'ContentGovernanceCheckPage') {
                req.reply({statusCode: 500, body: {errors: [{message: 'cg-test failure'}]}});
            }
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        cy.get(DRAWER).contains('automated rules passed', {timeout: 150000}).should('exist');
        cy.get('#audit-tab-links').click();
        cy.get(DRAWER).contains('This check failed').should('exist');
        cy.get('#audit-tab-seo').click();
        cy.get(DRAWER).contains('This check failed').should('exist');
        cy.get('#audit-tab-accessibility').click();
        cy.get(DRAWER).contains('This check failed').should('not.exist');
    });
});
