import React from 'react';
import i18next from 'i18next';
import {registry} from '@jahia/ui-extender';
import {GovernanceAction} from './drawer/GovernanceAction';
import {GeoDashboard} from './geo/dashboard/GeoDashboard';
import {ComplianceDashboardRoute} from './compliance/dashboard/ComplianceDashboard';
import {SettingsPanel} from './settings/SettingsPanel';
import {SectionGate} from './common/SectionGate';
import {NS} from './common/i18n';
import {MODULE} from './common/module';
import en from '../main/resources/javascript/locales/en.json';
import fr from '../main/resources/javascript/locales/fr.json';

// The bundles ship with the code, so the labels exist before anything is registered.
function registerTranslations() {
    [['en', en], ['fr', fr]].forEach(([lang, resource]) => {
        if (!i18next.hasResourceBundle(lang, NS)) {
            i18next.addResourceBundle(lang, NS, resource[NS], true, true);
        }
    });
}

const GeoDashboardRoute = () => (
    <SectionGate section="geo" title={i18next.t(`${NS}:geo.dashboard.navLabel`)}>
        {isOn => <GeoDashboard isOn={isOn}/>}
    </SectionGate>
);

const ComplianceRoute = () => (
    <SectionGate section="compliance" title={i18next.t(`${NS}:compliance.dashboard.navLabel`)}>
        {() => <ComplianceDashboardRoute/>}
    </SectionGate>
);

function register() {
    // A shield with a check mark: governance of what is published.
    const buttonIcon = window.jahia?.moonstone?.toIconComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">' +
        '<path d="M12 1 3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z"/>' +
        '</svg>'
    );

    // One button for the three sections. The component shows it only on a page or a main
    // resource of a site that enables the module and keeps at least one section switched on.
    registry.add('action', 'contentGovernance', {
        targets: ['headerPrimaryActions:890'],
        buttonIcon,
        buttonLabel: `${NS}:drawer.action`,
        component: GovernanceAction
    });

    // Site dashboards stay where editors know them, in jContent's Additional accordion: GEO
    // readiness next to Robots.txt and Sitemap (robots.txt and llms.txt belong to the site), the
    // compliance report beside the other site reports.
    registry.add('adminRoute', 'siteSettingsSeo/geoReadiness', {
        targets: ['jcontent-siteSettingsSeo:80'],
        label: `${NS}:geo.dashboard.navLabel`,
        isSelectable: true,
        requiredPermission: 'publish',
        requireModuleInstalledOnSite: MODULE,
        render: () => <GeoDashboardRoute/>
    });
    registry.add('adminRoute', 'contentGovernanceCompliance', {
        targets: ['jcontent:90'],
        label: `${NS}:compliance.dashboard.navLabel`,
        icon: buttonIcon,
        isSelectable: true,
        requiredPermission: 'jContentAccess',
        requireModuleInstalledOnSite: MODULE,
        render: () => <ComplianceRoute/>
    });

    // Per-site switches, the shared model's status and the BotRank connection: site administrators
    // only, since the BotRank binding spends the customer's organisation key.
    registry.add('adminRoute', 'contentGovernanceSettings', {
        targets: ['administration-sites:95'],
        label: `${NS}:settings.navLabel`,
        icon: buttonIcon,
        isSelectable: true,
        requiredPermission: 'site-admin',
        requireModuleInstalledOnSite: MODULE,
        render: () => <SettingsPanel/>
    });
}

export default function () {
    registerTranslations();
    register();
}
