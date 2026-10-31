import {deleteUser} from '@jahia/cypress';
import {TestUser, createTestSite, createTestUser, deleteTestSite} from '../support/cg';

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
 * Readability per page language: the formula of a language that has one, no score (rather than
 * the English one) for a language without, characters instead of words where words are not
 * spaced. The scores themselves are unit-tested (src/test/javascript/readability.test.js).
 */
const READABILITY = [
    {lang: 'en', expect: 'Readability score (Flesch)', text: 'The team writes short pages. Each page answers one question. Readers find what they need and leave. Short sentences help everyone read faster. Plain words work better than jargon. We test every page with real readers before it goes live.'},
    {lang: 'de', expect: 'Readability score (Amstad)', text: 'Das Team schreibt kurze Seiten. Jede Seite beantwortet eine Frage. Die Leser finden schnell, was sie suchen. Kurze Sätze helfen allen Menschen beim Lesen. Einfache Wörter sind besser als Fachsprache. Wir testen jede Seite mit echten Lesern.'},
    {lang: 'ru', expect: 'No readability formula exists for Russian here', text: 'Команда пишет короткие страницы. Каждая страница отвечает на один вопрос. Читатели быстро находят то, что ищут. Короткие предложения помогают всем читать быстрее. Простые слова лучше, чем жаргон. Мы проверяем каждую страницу с настоящими читателями.'},
    {lang: 'ja', expect: 'Japanese is written without spaces between words', text: '私たちは短いページを書きます。各ページは一つの質問に答えます。読者は必要な情報をすぐに見つけます。'}
];

describe('Content Governance - Page quality audit', () => {
    const siteKey = 'cg-audit';
    const admin: TestUser = {username: 'cg-audit-admin', password: 'cgAdmin1234'};

    before(() => {
        createTestSite(siteKey, true, `en,fr,${READABILITY.map(r => r.lang).filter(l => l !== 'en').join(',')}`);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
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
        cy.readFile('../package.json').its('dependencies.axe-core').then((range: string) => {
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

    READABILITY.forEach(({lang, expect, text}) => {
        it(`scores readability for a page in ${lang}`, () => {
            cy.intercept('GET', `**/cms/render/default/${lang}/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
                req.continue(res => {
                    const body = String(res.body);
                    const fixture = `<p id="cg-readability-fixture">${text}</p>`;
                    res.body = body.includes('</main>') ? body.replace('</main>', `${fixture}</main>`) : body.replace('</body>', `${fixture}</body>`);
                });
            }).as('preview');
            cy.login(admin.username, admin.password);
            cy.visit(`/jahia/jcontent/${siteKey}/${lang}/pages/home`);
            cy.contains('button', BUTTON, {timeout: 30000}).click();
            cy.wait('@preview', {timeout: 30000});
            cy.get('#audit-tab-readability', {timeout: 60000}).click();
            cy.get(DRAWER).contains(expect, {timeout: 60000}).should('exist');
            if (lang === 'ja') {
                cy.get(DRAWER).contains('Characters').should('exist');
                cy.get(DRAWER).contains('Readability score').should('not.exist');
            }
        });
    });

    // A request that never answers costs its own tab only. The image holds the frame's load event
    // (the audit starts once the document is parsed, after 20 s); the link holds the link check,
    // which stops at its 45 s limit while the other tabs show their results.
    it('audits a page whose requests never answer, and says which check did not finish', () => {
        const hang = {delay: 120000, statusCode: 204};
        cy.intercept('GET', '**/files/cg-never-answers.png*', req => req.reply(hang)).as('image');
        cy.intercept({url: `**/cms/render/default/en/sites/${siteKey}/cg-never-answers.html*`}, req => req.reply(hang));
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                const fixture = `<p><img src="/files/cg-never-answers.png" alt="Waiting"> <a href="/cms/render/default/en/sites/${siteKey}/cg-never-answers.html">A page that never answers</a></p>`;
                res.body = String(res.body).replace('</body>', `${fixture}</body>`);
            });
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get('#audit-tab-links', {timeout: 60000}).should('exist');
        cy.get(DRAWER).contains('automated rules passed', {timeout: 150000}).should('exist');
        cy.get('#audit-tab-links').click();
        cy.get(DRAWER).contains('This check did not finish').should('exist');
        cy.get(DRAWER).contains('It took more than 45 seconds').should('exist');
        cy.get('#audit-tab-seo').click();
        cy.get(DRAWER).contains('This check did not finish').should('not.exist');
    });
});
