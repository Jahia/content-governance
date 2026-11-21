import {lazy} from 'react';
import {registry} from '@jahia/ui-extender';
import {NS} from '../common/i18n';
import {GOVERNANCE_PANEL} from '../common/module';
import {drawerTabs, PANEL_TYPE, TAB_TARGET, TAB_TYPE} from './entries';

const named = (load, name) => lazy(() => load().then(module => ({default: module[name]})));

/**
 * The parts of a tab that the site settings switch, with the translation keys of their label and help.
 *
 * @param {Array<string>} ids the ids of the parts, in the order of the tab
 * @param {string} prefix the key prefix of their texts (`audit`, `geo`)
 * @param {Function} label the key of the label of a part, after the prefix
 * @param {object} needs what a part needs, by part id: `botRank`
 * @returns {Array<object>} `{id, label, help, needs}`
 */
const parts = (ids, prefix, label, needs) => ids.map(id => ({
    id,
    label: `${NS}:${prefix}.${label(id)}`,
    help: `${NS}:${prefix}.tabHelp.${id}`,
    needs: needs[id]
}));

/**
 * The tabs of the module: Page quality, GEO readiness and Compliance. They register as a tab of
 * another module does, before it: a tab of another module takes a priority of 10 or more. Their code
 * loads the first time the drawer shows them.
 */
export function registerOwnTabs() {
    registry.add(TAB_TYPE, 'audit', {
        targets: [`${TAB_TARGET}:1`],
        label: `${NS}:drawer.section.audit`,
        help: `${NS}:drawer.sectionHelp.audit`,
        settingsHelp: `${NS}:settings.sectionHelp.audit`,
        tabs: parts(['accessibility', 'seo', 'vitals', 'readability', 'ecodesign', 'links', 'jahia'], 'audit',
            id => `tabs.${id}`, {}),
        component: named(() => import('../audit/AuditSection'), 'AuditSection')
    });
    registry.add(TAB_TYPE, 'geo', {
        targets: [`${TAB_TARGET}:2`],
        label: `${NS}:drawer.section.geo`,
        help: `${NS}:drawer.sectionHelp.geo`,
        settingsHelp: `${NS}:settings.sectionHelp.geo`,
        tabs: parts(['score', 'crawler', 'files', 'schema', 'citations'], 'geo', id => `${id}.tab`, {citations: 'botRank'}),
        component: named(() => import('../geo/GeoSection'), 'GeoSection')
    });
    // The scan reads a page and everything it shows, as someone who may edit it
    registry.add(TAB_TYPE, 'compliance', {
        targets: [`${TAB_TARGET}:3`],
        label: `${NS}:drawer.section.compliance`,
        help: `${NS}:drawer.sectionHelp.compliance`,
        settingsHelp: `${NS}:settings.sectionHelp.compliance`,
        requirePage: true,
        requireWrite: true,
        component: named(() => import('../compliance/ComplianceSection'), 'ComplianceSection')
    });
}

/** The governance panel is available where the site keeps at least one of its tabs on. */
const useGovernanceAvailable = ({isOn}) => drawerTabs().some(entry => isOn(entry.key));

/**
 * The governance panel of the drawer, which holds the tabs. It registers as the panel of another
 * module does, first: a panel of another module takes an order of 10 or more. Its code loads the
 * first time the drawer shows it.
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
