import {registry} from '@jahia/ui-extender';

/**
 * The entries of the drawer in jContent's registry: its panels, the tabs of the governance panel,
 * the actions on a finding, and the checks that run in the browser. The module registers its own
 * panel, tabs and checks there too (drawer/ownTabs.js, checks/register.js), so every panel gets the
 * same button and site switch, every tab the same site switch, "Fix first" rank and history, and
 * every check the same run. `docs/drawer-entries.md` states the contract.
 */

/** The registry type of a panel: a whole screen of the drawer, opened by its own button. */
export const PANEL_TYPE = 'contentGovernancePanel';

/** Where a panel shows when its entry names no node types: a page, or a main resource. */
export const PANEL_NODE_TYPES = ['jnt:page', 'jmix:mainResource'];

/** The registry type of a tab of the section bar of the governance panel. */
export const TAB_TYPE = 'contentGovernanceDrawerTab';

/** The target of those tabs. Its priority orders them. */
export const TAB_TARGET = 'contentGovernanceDrawer';

/** The target of the jContent actions that the drawer shows on a finding. */
export const FINDING_ACTIONS_TARGET = 'contentGovernanceFindingActions';

/** The form of the key of a panel or a tab and of the id of a part of a tab, which the site settings store. */
const ID = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/;

/** True for a key that the site settings can store: a letter, then up to 63 letters, digits, `_` or `-`. */
export const isTabId = id => typeof id === 'string' && ID.test(id);

/** The order of a panel, 0 when its entry gives none or not a number. */
const orderOf = entry => (Number.isFinite(entry.order) ? entry.order : 0);

/**
 * The panels of the drawer, by their `order`, then in the order modules added them. A panel with no
 * component, no label or no button label, or whose key the site settings cannot store, is left out.
 *
 * @returns {Array<object>} the registry entries
 */
export function drawerPanels() {
    return registry.find({type: PANEL_TYPE})
        .map((entry, index) => ({entry, index}))
        .filter(({entry}) => {
            if (!isTabId(entry.key)) {
                console.warn(`[content-governance] the drawer panel "${entry.key}" has a key the site settings cannot store, and is left out`);
                return false;
            }
            return Boolean(entry.component && entry.label && entry.buttonLabel);
        })
        .sort((a, b) => orderOf(a.entry) - orderOf(b.entry) || a.index - b.index)
        .map(({entry}) => entry);
}

/** The warnings already given: the drawer and its button read the tabs on every render. */
const warned = new Set();

const warnOnce = message => {
    if (!warned.has(message)) {
        warned.add(message);
        console.warn(message);
    }
};

/**
 * The tabs of the governance panel, in the order of their priority. A tab with no component or no
 * label, or whose key the site settings cannot store, is left out. So is a tab whose key is the key
 * of a panel: the site settings store both kinds of key in one list. Each tab left out warns once.
 *
 * @returns {Array<object>} the registry entries
 */
export function drawerTabs() {
    const panels = new Set(registry.find({type: PANEL_TYPE}).map(entry => entry.key));
    return registry.find({type: TAB_TYPE, target: TAB_TARGET}).filter(entry => {
        if (!isTabId(entry.key)) {
            warnOnce(`[content-governance] the drawer tab "${entry.key}" has a key the site settings cannot store, and is left out`);
            return false;
        }
        if (panels.has(entry.key)) {
            warnOnce(`[content-governance] the drawer tab "${entry.key}" has the key of a panel, and is left out`);
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

/** The registry type of a fixer: an action on a finding that proposes a fix, which the editor copies or applies. */
export const FIXER_TYPE = 'contentGovernanceFixer';

/**
 * The fixers that modules register, in the order that the modules added them. A fixer with no label, no
 * `appliesTo` function or no `run` function is left out, and the console says why once.
 *
 * @returns {Array<{key: string, label: string, appliesTo: Function, run: Function, apply: ?Function}>} the registry entries
 */
export function drawerFixers() {
    return registry.find({type: FIXER_TYPE}).filter(entry => {
        const valid = typeof entry.label === 'string' && entry.label !== '' && typeof entry.appliesTo === 'function'
            && typeof entry.run === 'function';
        if (!valid) {
            warnOnce(`[content-governance] the fixer "${entry.key}" has no label, no appliesTo function or no run function, and is left out`);
        }
        return valid;
    });
}

/**
 * The fixers that apply to a finding: those whose `appliesTo({rule, finding})` answers true. A fixer whose
 * `appliesTo` throws does not apply.
 *
 * @param {Array<object>} fixers the fixers of `drawerFixers`
 * @param {object} rule the rule of the finding, with the fields of the GraphQL field `contentRules`
 * @param {object} finding the finding: `{rule, severity, title, detail, items}`
 * @returns {Array<object>} the fixers that apply
 */
export function applicableFixers(fixers, rule, finding) {
    return fixers.filter(fixer => {
        try {
            return fixer.appliesTo({rule, finding}) === true;
        } catch (error) {
            console.warn(`[content-governance] the fixer "${fixer.key}" failed to say whether it applies`, error);
            return false;
        }
    });
}
