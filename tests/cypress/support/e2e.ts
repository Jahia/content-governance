// @jahia/cypress publishes through cy.waitUntil, which registerSupport() does not load.
import 'cypress-wait-until';
import {registerSupport} from '@jahia/cypress/dist/support/registerSupport';
import {asRoot, assertRefused, readSettings, signOut} from './cg';

registerSupport();

// Apollo (behind the @jahia/cypress helpers) calls fetch unbound.
if (typeof window !== 'undefined' && window.fetch) {
    globalThis.fetch = window.fetch.bind(window);
}

/**
 * Run-level guard: the specs read answers of the contentGovernance fields. When the module is not
 * deployed the field does not exist and a spec asserting a refusal would pass for that reason, so the
 * run checks once that the field answers, as root, on a site key that does not exist.
 */
before(() => {
    readSettings(asRoot(), 'cg-module-probe').then(body => {
        assertRefused(body, ['contentGovernance', 'siteSettings']);
    });
});

// The calls of a test share one server session, which holds a licence seat until it is signed out.
afterEach(() => {
    signOut();
});
