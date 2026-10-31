import env from '@jahia/cypress/dist/plugins/env';
import {existsSync, readFileSync} from 'node:fs';

/**
 * The axe-core range the module declares: from the module's package.json next to tests/ locally, from
 * the copy ci.build.sh puts in tests/ for the CI image, which holds tests/ only.
 */
function declaredAxeCore(): string | undefined {
    const file = ['../package.json', './module-package.json'].find(path => existsSync(path));
    return file ? JSON.parse(readFileSync(file, 'utf8')).dependencies?.['axe-core'] : undefined;
}

/**
 * Node-side setup: reads JAHIA_URL and SUPER_USER_PASSWORD from the process environment and sets
 * baseUrl and Cypress.env() from them (the CYPRESS_ prefixed forms are ignored by @jahia/cypress).
 *
 * @param {Cypress.PluginEvents} on Cypress plugin event registrar.
 * @param {Cypress.PluginConfigOptions} config the resolved Cypress configuration.
 * @returns {Cypress.PluginConfigOptions} the configuration with the Jahia environment applied.
 */
export function setupNodeEvents(
    on: Cypress.PluginEvents,
    config: Cypress.PluginConfigOptions
): Cypress.PluginConfigOptions {
    config.env.AXE_CORE_RANGE = declaredAxeCore();
    return env(on, config);
}
