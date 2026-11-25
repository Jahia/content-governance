import {deleteUser} from '@jahia/cypress';
import {TestUser, addPageText, createTestSite, createTestUser, deleteTestSite, subdomainSection} from '../support/cg';

const BUTTON = 'Content governance';
const DRAWER = 'dialog[aria-labelledby="content-governance-drawer-title"]';
const RULE = 'links.mixedContent';
const FIRST = 'http://example.com/cg-first';
const SECOND = 'http://example.org/cg-second';
const EDITED = 'https://example.com/cg-edited';
const NOTE = 'Test note: both addresses answer over HTTPS.';
const PROPOSAL = 'section[aria-label="Proposal · Test fix"]';

/** What the fixers of the test module record, and the switches that the suite sets on them. */
type FixerControl = {
    fail: string | null;
    empty: boolean;
    runs: Array<{fixer: string; occurrence: string; path?: string; language?: string}>;
    applied: Array<{text: string; occurrence: string | null}>;
    hold: () => void;
    release: () => void;
};

const fixers = (): Cypress.Chainable<FixerControl> =>
    cy.window().its('cgTestFixers') as unknown as Cypress.Chainable<FixerControl>;

/**
 * The fixers of a finding (content-governance#183). The test module (tests/jahia-module) registers two fixers of
 * the rule links.mixedContent, which no code of Content Governance names: `cgTestFix`, the first, with two
 * proposals, a note and an apply, and `cgTestFixOther`, which lands in the ⋮ of the card. Two http:// links on
 * the page give the rule two occurrences. `window.cgTestFixers` holds a run in the running state, makes it fail,
 * and records what each run and each apply received.
 */
describe('Content Governance - fixers', () => {
    const siteKey = 'cg-fixers';
    const home = `/sites/${siteKey}/home`;
    const admin: TestUser = {username: 'cg-fixers-admin', password: 'cgAdmin1234'};

    const card = () => cy.get(`${subdomainSection('links')}-panel`).contains('article', RULE);
    const row = (index: number) => card().find('details > ul > li').eq(index);
    // The line above the occurrences that Generate all shows
    const bar = () => card().find('[data-fix-all-bar]');

    /** Opens the drawer on the home page, runs a fresh audit, and opens the occurrences of the rule. */
    const openCard = () => {
        cy.login(admin.username, admin.password);
        cy.visit(`/jahia/jcontent/${siteKey}/en/pages/home`);
        cy.contains('button', BUTTON, {timeout: 30000}).click();
        cy.get(DRAWER).contains('button', 'Re-run', {timeout: 120000}).should('not.be.disabled').click();
        // A card that the end of the audit renders again would drop the runs of its fixers
        cy.get(DRAWER).contains('button', 'Re-run').should('be.disabled');
        cy.get(DRAWER).contains('button', 'Re-run', {timeout: 120000}).should('not.be.disabled');
        cy.get('#content-governance-section-quality', {timeout: 30000}).then($tab => {
            if ($tab.attr('aria-selected') !== 'true') {
                cy.wrap($tab).click();
            }
        });
        cy.get(subdomainSection('links')).then($header => {
            if ($header.attr('aria-expanded') !== 'true') {
                cy.wrap($header).click();
            }
        });
        // The audit runs on the server and in the preview before the card shows
        cy.get(`${subdomainSection('links')}-panel`).contains('article', RULE, {timeout: 90000}).should('exist');
        card().find('details > summary').should('contain.text', 'Findings (2)');
    };

    const openOccurrences = () => {
        card().find('details').then($details => {
            if (!$details.prop('open')) {
                cy.wrap($details).find('> summary').click('left');
            }
        });
        card().find('details').should('have.prop', 'open', true);
    };

    before(() => {
        createTestSite(siteKey);
        createTestUser(admin, `/sites/${siteKey}`, ['site-administrator']);
        addPageText(home, 'cg-fixers', {
            en: `<p>Read the <a href="${FIRST}">first report</a> and the <a href="${SECOND}">second report</a>.</p>`
        });
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

    it('runs the first fixer of a row, and hands the edited proposal to its apply', () => {
        openCard();
        openOccurrences();
        row(0).find('[data-fixer="cgTestFix"]').should('exist');
        row(1).find('[data-fixer="cgTestFix"]').should('exist');

        fixers().then(control => control.hold());
        row(0).find('[data-fixer="cgTestFix"]').click();
        row(0).should('contain.text', 'Generating…').contains('button', 'Cancel').should('exist');
        row(0).find('[data-fixer]').should('not.exist');
        fixers().then(control => control.release());

        row(0).find(PROPOSAL).within(() => {
            cy.get('input[type="radio"]').should('have.length', 2);
            cy.get('input[type="radio"]').eq(0).should('be.checked');
            cy.get('textarea').should('have.value', 'https://example.com/cg-first');
            cy.root().should('contain.text', 'Before:').and('contain.text', 'The same address over HTTPS')
                .and('contain.text', NOTE);
            cy.get('input[type="radio"]').eq(1).check();
            cy.get('textarea').should('have.value', 'https://www.example.com/cg-first');
            cy.get('textarea').clear().type(EDITED);
            cy.contains('button', /^Apply$/).click();
        });

        row(0).find(PROPOSAL).should('not.exist');
        row(0).find('output').should('contain.text', 'Applied:').and('contain.text', EDITED)
            .and('contain.text', 'The finding clears at the next audit.');
        fixers().then(control => {
            expect(control.applied).to.deep.eq([{text: EDITED, occurrence: FIRST}]);
            expect(control.runs).to.deep.eq([{fixer: 'cgTestFix', occurrence: FIRST, path: home, language: 'en'}]);
        });
        // The other row keeps its button
        row(1).find('[data-fixer="cgTestFix"]').should('exist');
    });

    it('cancels a run, and copies, regenerates and discards a proposal', () => {
        openCard();
        openOccurrences();

        fixers().then(control => control.hold());
        row(1).find('[data-fixer="cgTestFix"]').click();
        row(1).contains('button', 'Cancel').click();
        row(1).should('not.contain.text', 'Generating…');
        row(1).find('[data-fixer="cgTestFix"]').should('exist');
        fixers().then(control => control.release());

        // The CI serves Jahia on a non-secure origin, where the browser has no clipboard: the test gives it one
        cy.window().then(win => {
            const writeText = cy.stub().as('writeText').resolves();
            Object.defineProperty(win.navigator, 'clipboard', {value: {writeText}, configurable: true});
        });
        row(1).find('[data-fixer="cgTestFix"]').click();
        row(1).find(PROPOSAL).within(() => {
            cy.get('textarea').should('have.value', 'https://example.org/cg-second').clear().type(EDITED);
            cy.contains('button', 'Copy').click();
            cy.contains('button', 'Copied').should('exist');
        });
        cy.get('@writeText').should('have.been.calledOnceWith', EDITED);

        fixers().then(control => control.hold());
        row(1).find(PROPOSAL).contains('button', 'Regenerate').click();
        row(1).should('contain.text', 'Generating…');
        fixers().then(control => control.release());
        // A new run drops the edit
        row(1).find(`${PROPOSAL} textarea`).should('have.value', 'https://example.org/cg-second');
        fixers().its('runs').should('have.length', 3);

        row(1).find(PROPOSAL).contains('button', 'Discard').click();
        row(1).find(PROPOSAL).should('not.exist');
        row(1).find('[data-fixer="cgTestFix"]').should('exist');
        fixers().its('applied').should('be.empty');
    });

    it('says why a run gave no proposal, and runs it again on Retry', () => {
        openCard();
        openOccurrences();

        fixers().then(control => {
            control.fail = 'The test model is offline';
        });
        row(0).find('[data-fixer="cgTestFix"]').click();
        row(0).find('[role="alert"]').should('contain.text', 'No proposal: The test model is offline');
        row(0).find(PROPOSAL).should('not.exist');

        fixers().then(control => {
            control.fail = null;
            control.empty = true;
        });
        row(0).find('[role="alert"]').contains('button', 'Retry').click();
        row(0).find('[role="alert"]').should('contain.text', 'No proposal for this finding.');

        fixers().then(control => {
            control.empty = false;
        });
        row(0).find('[role="alert"]').contains('button', 'Retry').click();
        row(0).find('[role="alert"]').should('not.exist');
        row(0).find(`${PROPOSAL} textarea`).should('have.value', 'https://example.com/cg-first');
        fixers().its('runs').should('have.length', 3);
    });

    it('puts the other fixer in the ⋮ of the card, where it runs on every occurrence', () => {
        openCard();
        openOccurrences();
        card().find('[data-fixer="cgTestFixOther"]').should('not.exist');
        card().find('button[aria-label="More actions"]').click();
        card().find('[data-fixer="cgTestFixOther"]').should('be.visible').click();

        bar().should('contain.text', '2 proposals');
        row(0).find('input').should('have.value', `${FIRST}#other`);
        row(1).find('input').should('have.value', `${SECOND}#other`);
        fixers().its('runs').should(runs => {
            expect(runs.map(run => run.fixer)).to.deep.eq(['cgTestFixOther', 'cgTestFixOther']);
        });

        bar().contains('button', 'Discard all').click();
        bar().should('not.exist');
        row(0).find('[data-fixer="cgTestFix"]').should('exist');
        row(1).find('[data-fixer="cgTestFix"]').should('exist');
    });

    it('generates a proposal for every occurrence, and applies them all once confirmed', () => {
        openCard();
        fixers().then(control => control.hold());
        // Generate all opens the occurrences
        card().find('[data-fix-all="cgTestFix"]').click();
        card().find('details').should('have.prop', 'open', true);
        card().find('[data-fix-all]').should('not.exist');
        bar().should('contain.text', 'Generating 0 of 2').contains('button', 'Cancel all').should('exist');
        row(0).should('contain.text', 'Generating…');
        row(1).should('contain.text', 'Generating…');
        fixers().then(control => control.release());

        bar().should('contain.text', '2 proposals');
        row(0).find('input').should('have.value', 'https://example.com/cg-first');
        row(1).find('input').should('have.value', 'https://example.org/cg-second');

        // Apply 2 asks first, on the same line, and Cancel goes back
        bar().contains('button', 'Apply 2').click();
        bar().should('contain.text', 'Write 2 proposals?');
        fixers().its('applied').should('be.empty');
        bar().contains('button', /^Cancel$/).click();
        bar().should('contain.text', '2 proposals').and('not.contain.text', 'Write');

        bar().contains('button', 'Apply 2').click();
        bar().contains('button', 'Apply 2').click();
        bar().should('match', 'output').and('have.attr', 'data-fix-all-bar', 'done').and('contain.text', '2 applied')
            .and('contain.text', 'The findings clear at the next audit.');
        row(0).find('output').should('contain.text', 'https://example.com/cg-first');
        row(1).find('output').should('contain.text', 'https://example.org/cg-second');
        fixers().its('applied').should(applied => {
            expect(applied.map(item => `${item.occurrence} ${item.text}`).sort()).to.deep.eq([
                `${FIRST} https://example.com/cg-first`,
                `${SECOND} https://example.org/cg-second`
            ]);
        });

        // Close hides the summary, and each row keeps its state
        bar().find('button[aria-label="Close"]').click();
        bar().should('not.exist');
        row(0).find('output').should('contain.text', 'Applied:');
    });
});
