import {deleteUser} from '@jahia/cypress';
import {
    FramePageFinding, TestUser, addPageText, asRoot, createTestSite, createTestUser, deleteTestSite, framePageCheck, gqlOk,
    setDrawerDisplay, subdomainSection
} from '../support/cg';

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

/** The domain of the layout that content-governance ships that holds each subdomain that a test opens. */
const DOMAIN_OF: Record<string, string> = {readability: 'compliance', seo: 'geo'};

/**
 * A subdomain is a section of its domain, which the press on its header opens, and closes when it is
 * open. The tab of its domain shows first. Its header shows once the layout is read; a section with a
 * major defect is already open.
 */
const openSection = (id: string, timeout = 4000) => {
    cy.get(`#content-governance-section-${DOMAIN_OF[id] || 'quality'}`, {timeout}).then($tab => {
        if ($tab.attr('aria-selected') !== 'true') {
            cy.wrap($tab).click();
        }
    });
    cy.get(subdomainSection(id), {timeout}).then($header => {
        if ($header.attr('aria-expanded') !== 'true') {
            cy.wrap($header).click();
        }
    });
    cy.get(subdomainSection(id)).should('have.attr', 'aria-expanded', 'true');
};
const sectionPanel = (id: string) => `${subdomainSection(id)}-panel`;

/**
 * Readability per page language: the formula of a language that has one, no score (rather than
 * the English one) for a language without, characters instead of words where words are not
 * spaced. The server measures the page as it renders it, so each text is content of the page. The
 * scores themselves are unit-tested (PageChecksTest).
 */
const READABILITY = [
    {lang: 'en', expect: 'Readability score (Flesch)', text: 'The team writes short pages. Each page answers one question. Readers find what they need and leave. Short sentences help everyone read faster. Plain words work better than jargon. We test every page with real readers before it goes live.'},
    {lang: 'de', expect: 'Readability score (Amstad)', text: 'Das Team schreibt kurze Seiten. Jede Seite beantwortet eine Frage. Die Leser finden schnell, was sie suchen. Kurze Sätze helfen allen Menschen beim Lesen. Einfache Wörter sind besser als Fachsprache. Wir testen jede Seite mit echten Lesern.'},
    {lang: 'sv', expect: 'Readability score (LIX)', text: 'Teamet skriver korta sidor. Varje sida svarar på en enda fråga. Läsarna hittar snabbt det de söker. Korta meningar hjälper alla att läsa snabbare. Enkla ord fungerar bättre än fackspråk. Vi testar varje sida med riktiga läsare.'},
    {lang: 'ru', expect: 'No readability formula exists for Russian here', text: 'Команда пишет короткие страницы. Каждая страница отвечает на один вопрос. Читатели быстро находят то, что ищут. Короткие предложения помогают всем читать быстрее. Простые слова лучше, чем жаргон. Мы проверяем каждую страницу с настоящими читателями.'},
    {lang: 'ja', expect: 'Japanese is written without spaces between words', text: '私たちは短いページを書きます。各ページは一つの質問に答えます。読者は必要な情報をすぐに見つけます。'}
];

/** What a run of the drawer stored, and what frame.check gives: the number of findings of each rule. */
type RuleCounts = Record<string, number>;
type StoredRecommendation = {rule?: string; items?: unknown[]};

/** The tabs whose recommendations the check of the server gives, by their key in the stored results. */
const SERVER_TABS = ['seo', 'readability', 'links', 'jahia', 'a11y', 'other'];

const PAGE_AUDITS = `query A($path: String!) {
    contentGovernance { pageAudits(path: $path, language: "en") { auditedBy results } }
}`;

describe('Content Governance - Page quality audit', () => {
    const siteKey = 'cg-audit';
    const home = `/sites/${siteKey}/home`;
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
        // The cfg of the server hides both until it shows them; one test hides them again
        setDrawerDisplay({fixFirst: true, previewButton: true});
    });

    afterEach(() => cy.logout());

    after(() => {
        setDrawerDisplay({fixFirst: false, previewButton: false});
        deleteUser(admin.username);
        deleteTestSite(siteKey);
    });

    it('hides "Fix first" and the "Show preview" button where the cfg of the server hides them', () => {
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', `${FIXTURE}</body>`);
            });
        });
        setDrawerDisplay({fixFirst: false, previewButton: false});
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        // The image without alt of the fixture is critical: "Fix first" would list it, and its section turns red
        cy.get(DRAWER).contains('a', /^image-alt$/, {timeout: 90000}).should('exist');
        cy.get(subdomainSection('accessibility')).find('.moonstone-badge').should('exist');
        cy.get('section[aria-label="Fix first"]').should('not.exist');
        cy.get(DRAWER).contains('button', /^(Show|Hide) preview$/).should('not.exist');
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
            // The bundled engine is the declared minimum: a lockfile ahead of package.json fails here.
            openSection('accessibility', 60000);
            cy.get(DRAWER).contains(`axe-core ${declared}`, {timeout: 60000}).should('exist');
            EXPECTED_RULES.forEach(rule => {
                cy.get(DRAWER).contains('a', new RegExp(`^${rule}$`)).should('exist');
            });
        });
    });

    it('puts the most serious finding first in "Fix first", and opens its section', () => {
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
        // The fixture's image without alt is critical: its section opens on its own
        cy.get(subdomainSection('accessibility'), {timeout: 90000}).should('have.attr', 'aria-expanded', 'true');
        cy.get(sectionPanel('accessibility')).should('contain.text', 'automated rules passed');
        cy.get(subdomainSection('accessibility')).click().should('have.attr', 'aria-expanded', 'false');
        cy.get(sectionPanel('accessibility')).should('not.be.visible');
        cy.get(fixFirst).contains('button', 'Show').click();
        // The image without alt of the fixture: a critical violation. Findings of one severity keep the order of the
        // sections, so a critical finding of Links, before Accessibility in the layout, may come first.
        cy.get('#content-governance-fix-first li').first().should('contain.text', 'Critical');
        const imageFinding = () => cy.contains('#content-governance-fix-first li', 'alternative text');
        imageFinding().should('contain.text', 'Critical');
        // Red is for what is critical or serious: the fixture's image without alt, on its section and its domain
        cy.get(subdomainSection('accessibility')).find('.moonstone-badge').should('exist');
        cy.get('#content-governance-section-quality').find('.moonstone-badge').should('exist');
        cy.get('#content-governance-fix-first li').each($finding => {
            expect($finding.text(), 'Fix first lists critical and serious findings only').to.match(/Critical|Serious/);
        });
        // The drawer's own button: an action of another module on the finding may start with "Open" too
        imageFinding().contains('button', /^Open$/).click();
        cy.get(subdomainSection('accessibility')).should('have.attr', 'aria-expanded', 'true');
        // The docked drawer is as tall as the page under edit: the section's results may start below its fold
        cy.get(sectionPanel('accessibility')).contains('automated rules passed').scrollIntoView().should('be.visible');
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
        openSection('vitals', 90000);
        cy.get(sectionPanel('vitals')).should('contain.text', 'Indicative figures').and('contain.text', 'LCP').within(() => {
            ['Needs improvement', 'Poor', 'Main content renders slowly', 'Slow server response', 'Layout shifts while loading'].forEach(text => {
                cy.contains(text).should('not.exist');
            });
        });
    });

    // The Readability tab counted skipped levels without saying which heading skips: the outline lists
    // every heading of the main content, and what is wrong with it. The server reads the headings, and
    // the drawer shows one in its preview.
    it('lists the headings as an outline, with the level a heading skips and a heading without text', () => {
        const outline = `${DRAWER} ol[aria-label="Heading outline"]`;
        addPageText(home, 'cg-outline', {en: '<h2>Outline section</h2><h4>Outline detail</h4><h3></h3>'});
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        openSection('readability', 90000);
        cy.contains(`${outline} li`, 'Outline section', {timeout: 30000})
            .should('contain.text', 'H2').and('not.contain.text', 'Skips');
        cy.contains(`${outline} li`, 'Outline detail')
            .should('contain.text', 'H4').and('contain.text', 'Skips from H2 to H4');
        cy.contains(`${outline} li`, '(no text)').should('contain.text', 'H3').and('contain.text', 'Empty');
        cy.get(DRAWER).contains('li', 'Skipped heading levels').find('strong')
            .should($count => expect(Number($count.text())).to.be.greaterThan(0));

        // The selector that the server gives finds the heading in the preview, and Page Builder shows the
        // page beside the drawer: the heading is marked there
        cy.contains(`${outline} li`, 'Outline detail').find('button').click();
        cy.get('iframe[data-sel-role="page-builder-frame-active"]').its('0.contentDocument.body')
            .find('h4:contains("Outline detail")')
            .should($heading => expect($heading[0].style.outline, 'marked').to.contain('3px'));
    });

    READABILITY.forEach(({lang, expect, text}, index) => {
        it(`scores readability for a page in ${lang}`, () => {
            if (index === 0) {
                addPageText(home, 'cg-readability', Object.fromEntries(READABILITY.map(item => [item.lang, `<p>${item.text}</p>`])));
            }
            cy.login(admin.username, admin.password);
            cy.visit(`/jahia/jcontent/${siteKey}/${lang}/pages/home`);
            cy.contains('button', BUTTON, {timeout: 30000}).click();
            freshAudit();
            openSection('readability', 90000);
            cy.get(DRAWER).contains(expect, {timeout: 90000}).should('exist');
            if (lang === 'ja') {
                cy.get(DRAWER).contains('Characters').should('exist');
                cy.get(DRAWER).contains('Readability score').should('not.exist');
            }
        });
    });

    // A request that never answers costs its own tabs only. The image holds the frame's load event (the
    // audit starts once the document is parsed, after 20 s); the check of the server never answers, and
    // stops at its 60 s limit, while the tabs of the browser show their results.
    it('audits a page whose requests never answer, and says which check did not finish', () => {
        const hang = {delay: 120000, statusCode: 204};
        cy.intercept('GET', '**/files/cg-never-answers.png*', req => req.reply(hang)).as('image');
        cy.intercept('POST', '**/modules/graphql*', req => {
            const operations = Array.isArray(req.body) ? req.body : [req.body];
            if (operations.some(operation => operation?.operationName === 'ContentGovernanceCheckPage')) {
                req.reply(hang);
            }
        });
        cy.intercept('GET', `**/cms/render/default/en/sites/${siteKey}/home.html?pageAuditRun=*`, req => {
            req.continue(res => {
                res.body = String(res.body).replace('</body>', '<p><img src="/files/cg-never-answers.png" alt="Waiting"></p></body>');
            });
        });
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        // The section of a check that did not finish opens on its own, so its warning shows
        cy.get(subdomainSection('links'), {timeout: 150000}).should('have.attr', 'aria-expanded', 'true');
        cy.get(sectionPanel('links')).should('contain.text', 'This check did not finish')
            .and('contain.text', 'It took more than 60 seconds');
        openSection('accessibility');
        cy.get(sectionPanel('accessibility')).should('contain.text', 'automated rules passed')
            .and('not.contain.text', 'This check did not finish');
    });

    // The Done when of content-governance#132: the drawer reads the page check service, so it gives one
    // page the findings that frame.check gives an agent, under the same rule ids.
    it('gives the findings of frame.check for the page, under the same rule ids', () => {
        const fromDrawer = (results: Record<string, {recommendations?: StoredRecommendation[]}>): RuleCounts => {
            const counts: RuleCounts = {};
            SERVER_TABS.forEach(tab => (results[tab]?.recommendations || []).forEach(rec => {
                if (rec.items) {
                    counts[rec.rule as string] = rec.items.length;
                }
            }));
            return counts;
        };
        // The defects of the server checks, as content of the page: an http:// link opening a new window,
        // a link that says "here" to a page that a visitor cannot open (the test site is not published:
        // Jahia refuses to save a link to a page that does not exist), and an image without alt
        addPageText(home, 'cg-findings', {en: `<p>Read the <a href="http://example.com/report" target="_blank">yearly report</a>, or <a href="/cms/render/default/en/sites/${siteKey}/home.html">here</a>.</p><p><img src="https://example.com/cg-none.png"></p>`});
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        freshAudit();
        openSection('links', 90000);
        cy.get(DRAWER).contains(`by ${admin.username}`, {timeout: 30000}).should('exist');
        cy.get(DRAWER).contains('links.mixedContent').should('exist');
        cy.get(DRAWER).contains('Not published').should('exist');

        gqlOk(asRoot(), PAGE_AUDITS, {path: home}).then(data => {
            const run = data.contentGovernance.pageAudits[0];
            expect(run.auditedBy).to.eq(admin.username);
            const drawer = fromDrawer(JSON.parse(run.results));
            expect(drawer).to.include.keys('links.mixedContent', 'links.blankNoopener', 'links.broken',
                'html.linkTextVague', 'html.imageAltMissing', 'health.unpublishedChanges');
            framePageCheck({siteKey, locale: 'en', path: home}).then((findings: FramePageFinding[]) => {
                const agent: RuleCounts = {};
                findings.forEach(finding => {
                    agent[finding.rule] = (agent[finding.rule] || 0) + 1;
                });
                expect(drawer).to.deep.eq(agent);
            });
        });
    });
});
