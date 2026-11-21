import {registry} from '@jahia/ui-extender';

/**
 * The entries of the drawer in jContent's registry: its panels, the tabs of the governance panel,
 * and the actions on a finding. The module registers its own panel and tabs there too
 * (drawer/ownTabs.js), so every panel gets the same button and site switch, and every tab the same
 * site switch, "Fix first" rank and history. `docs/drawer-entries.md` states the contract.
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

/**
 * The tabs of the governance panel, in the order of their priority. A tab with no component or no
 * label, or whose key the site settings cannot store, is left out. So is a tab whose key is the key
 * of a panel: the site settings store both kinds of key in one list.
 *
 * @returns {Array<object>} the registry entries
 */
export function drawerTabs() {
    const panels = new Set(registry.find({type: PANEL_TYPE}).map(entry => entry.key));
    return registry.find({type: TAB_TYPE, target: TAB_TARGET}).filter(entry => {
        if (!isTabId(entry.key) || panels.has(entry.key)) {
            console.warn(`[content-governance] the drawer tab "${entry.key}" has a key the site settings cannot store apart, and is left out`);
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
