import {registry} from '@jahia/ui-extender';

/**
 * The entries of the drawer in jContent's registry: its tabs in the section bar, the actions on a
 * finding, and the checks that run in the browser. The module registers its own tabs and checks there
 * too (drawer/ownTabs.js, checks/register.js), so every tab gets the same site switch, "Fix first" rank
 * and history, and every check the same run. `docs/drawer-entries.md` states the contract.
 */

/** The registry type of a tab of the section bar. */
export const TAB_TYPE = 'contentGovernanceDrawerTab';

/** The target of those tabs. Its priority orders them. */
export const TAB_TARGET = 'contentGovernanceDrawer';

/** The target of the jContent actions that the drawer shows on a finding. */
export const FINDING_ACTIONS_TARGET = 'contentGovernanceFindingActions';

/** The form of the key of a tab and of the id of a part of a tab, which the site settings store. */
const ID = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/;

/** True for a key that the site settings can store: a letter, then up to 63 letters, digits, `_` or `-`. */
export const isTabId = id => typeof id === 'string' && ID.test(id);

/**
 * The tabs of the drawer, in the order of their priority. A tab with no component or no label, or
 * whose key the site settings cannot store, is left out.
 *
 * @returns {Array<object>} the registry entries
 */
export function drawerTabs() {
    return registry.find({type: TAB_TYPE, target: TAB_TARGET}).filter(entry => {
        if (!isTabId(entry.key)) {
            console.warn(`[content-governance] the drawer tab "${entry.key}" has a key the site settings cannot store, and is left out`);
            return false;
        }
        return Boolean(entry.component && entry.label);
    });
}

/**
 * The parts of a tab that the site settings switch on or off, those with an id the settings can store.
 *
 * @param {object} entry a registry entry of `drawerTabs`
 * @returns {Array<object>} its `tabs`: `{id, label, help?, needs?}`
 */
export const tabParts = entry => (Array.isArray(entry.tabs) ? entry.tabs.filter(part => isTabId(part?.id) && part.label) : []);

/** The actions on a finding that other modules added, in the order of their priority. */
export const findingActions = () => registry.find({type: 'action', target: FINDING_ACTIONS_TARGET});

/** The registry type of a check that the drawer runs in the browser, on the preview of the page. */
export const CHECK_TYPE = 'contentGovernanceCheck';

/**
 * The name of the performance mark that the drawer sets in the preview before the first check runs. A
 * check that measures what the page loaded leaves out the resources after it, which other checks added,
 * such as the script of axe-core.
 */
export const CHECKS_MARK = 'contentGovernanceChecks';

/**
 * The checks that run in the browser, in the order that the modules added them. A check with no
 * evaluator or no `run` function is left out.
 *
 * @returns {Array<{key: string, evaluator: string, run: Function}>} the registry entries
 */
export function browserChecks() {
    return registry.find({type: CHECK_TYPE}).filter(entry => {
        const valid = typeof entry.evaluator === 'string' && entry.evaluator !== '' && typeof entry.run === 'function';
        if (!valid) {
            console.warn(`[content-governance] the check "${entry.key}" has no evaluator or no run function, and is left out`);
        }
        return valid;
    });
}
