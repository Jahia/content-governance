import {registry} from '@jahia/ui-extender';

/**
 * The entries that other modules add to the drawer through jContent's registry: tabs in the section
 * bar, and actions on a finding. `docs/drawer-entries.md` states the contract.
 */

/** The registry type of a tab that another module adds to the section bar. */
export const TAB_TYPE = 'contentGovernanceDrawerTab';

/** The target of those tabs. Its priority orders them, after the module's own sections. */
export const TAB_TARGET = 'contentGovernanceDrawer';

/** The target of the jContent actions that the drawer shows on a finding. */
export const FINDING_ACTIONS_TARGET = 'contentGovernanceFindingActions';

/**
 * The tabs that other modules added, in the order of their priority. A tab with no component or no
 * label, or whose key is the id of one of the module's own sections, is left out.
 *
 * @param {Array<string>} ownIds the ids of the module's own sections
 * @returns {Array<object>} the registry entries
 */
export function addedTabs(ownIds) {
    return registry.find({type: TAB_TYPE, target: TAB_TARGET}).filter(entry => {
        if (ownIds.includes(entry.key)) {
            console.warn(`[content-governance] the drawer tab "${entry.key}" has the id of a section of the module, and is left out`);
            return false;
        }
        return Boolean(entry.component && entry.label);
    });
}

/** The actions on a finding that other modules added, in the order of their priority. */
export const findingActions = () => registry.find({type: 'action', target: FINDING_ACTIONS_TARGET});
