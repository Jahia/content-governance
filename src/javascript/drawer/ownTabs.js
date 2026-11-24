import {lazy} from 'react';
import {registry} from '@jahia/ui-extender';
import {NS} from '../common/i18n';
import {GOVERNANCE_PANEL} from '../common/module';
import {drawerTabs, PANEL_TYPE} from './entries';
import {useHasDomains} from './layout';

const named = (load, name) => lazy(() => load().then(module => ({default: module[name]})));

/**
 * The governance panel is available where the layout of the site keeps a domain of the panel on, or the site
 * keeps a tab of another module on. Undefined while the layout loads.
 */
const useGovernanceAvailable = ({siteKey, isOn}) => {
    const hasDomains = useHasDomains(siteKey);
    if (drawerTabs().some(entry => isOn(entry.key))) {
        return true;
    }
    return hasDomains;
};

/**
 * The governance panel of the drawer, which holds the domains of the layout of the site (drawer/layout.js) and
 * the tabs of other modules. It registers as the panel of another module does, first: a panel of another module
 * takes an order of 10 or more. Its code loads the first time the drawer shows it.
 *
 * @param {object} buttonIcon the icon of its button in the header of jContent
 */
export function registerOwnPanel(buttonIcon) {
    registry.add(PANEL_TYPE, GOVERNANCE_PANEL, {
        label: `${NS}:drawer.title`,
        buttonLabel: `${NS}:drawer.action`,
        buttonIcon,
        order: 0,
        settingsHelp: `${NS}:settings.panelHelp.governance`,
        useAvailable: useGovernanceAvailable,
        component: named(() => import('./GovernancePanel'), 'GovernancePanel')
    });
}
