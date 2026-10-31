import React, {Suspense, lazy} from 'react';
import PropTypes from 'prop-types';
import i18next from 'i18next';
import {registry} from '@jahia/ui-extender';
import {Header, LayoutContent, Loader} from '@jahia/moonstone';
import {GovernanceAction} from './drawer/GovernanceAction';
import {SectionGate} from './common/SectionGate';
import {NS} from './common/i18n';
import {MODULE} from './common/module';

// What jContent loads when it starts is the header button and the route entries: the drawer, the
// dashboards and the settings panel load the first time they are opened, and only the editor's
// language is loaded. Every language file holds every key (they are kept in sync).
const LOCALES = {
    en: () => import('../main/resources/javascript/locales/en.json'),
    fr: () => import('../main/resources/javascript/locales/fr.json')
};

const named = (load, name) => lazy(() => load().then(module => ({default: module[name]})));
const GeoDashboard = named(() => import('./geo/dashboard/GeoDashboard'), 'GeoDashboard');
const ComplianceDashboardRoute = named(() => import('./compliance/dashboard/ComplianceDashboard'), 'ComplianceDashboardRoute');
const SettingsPanel = named(() => import('./settings/SettingsPanel'), 'SettingsPanel');
const AuditDashboardRoute = named(() => import('./audit/dashboard/AuditDashboard'), 'AuditDashboardRoute');

/** The language of the editor's interface that the module ships, else English (i18next's fallback). */
function uiLanguage() {
    const lang = (i18next.language || globalThis.contextJsParameters?.uilang || 'en').slice(0, 2).toLowerCase();
    return LOCALES[lang] ? lang : 'en';
}

// Loaded before anything is registered, so the labels exist when jContent first draws them.
async function registerTranslations() {
    const lang = uiLanguage();
    if (!i18next.hasResourceBundle(lang, NS)) {
        const module = await LOCALES[lang]();
        const resource = module.default || module;
        i18next.addResourceBundle(lang, NS, resource[NS], true, true);
    }
}

/** A screen that loads on first use, with Jahia's empty layout and a loader meanwhile. */
const Loading = ({title, children}) => (
    <Suspense fallback={<LayoutContent hasPadding header={<Header title={title}/>} content={<Loader size="big"/>}/>}>
        {children}
    </Suspense>
);

Loading.propTypes = {
    title: PropTypes.string.isRequired,
    children: PropTypes.node.isRequired
};

const GeoDashboardRoute = () => {
    const title = i18next.t(`${NS}:geo.dashboard.navLabel`);
    return (
        <SectionGate section="geo" title={title}>
            {isOn => <Loading title={title}><GeoDashboard isOn={isOn}/></Loading>}
        </SectionGate>
    );
};

const ComplianceRoute = () => {
    const title = i18next.t(`${NS}:compliance.dashboard.navLabel`);
    return (
        <SectionGate section="compliance" title={title}>
            {() => <Loading title={title}><ComplianceDashboardRoute/></Loading>}
        </SectionGate>
    );
};

const PageQualityRoute = () => {
    const title = i18next.t(`${NS}:audit.dashboard.navLabel`);
    return (
        <SectionGate section="audit" title={title}>
            {() => <Loading title={title}><AuditDashboardRoute/></Loading>}
        </SectionGate>
    );
};

const SettingsRoute = () => (
    <Loading title={i18next.t(`${NS}:settings.navLabel`)}><SettingsPanel/></Loading>
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
    // The Page quality report lists the audits editors ran from the drawer, for whoever may open
    // jContent on the site, like the compliance report beside it.
    registry.add('adminRoute', 'contentGovernancePageQuality', {
        targets: ['jcontent:89'],
        label: `${NS}:audit.dashboard.navLabel`,
        icon: buttonIcon,
        isSelectable: true,
        requiredPermission: 'jContentAccess',
        requireModuleInstalledOnSite: MODULE,
        render: () => <PageQualityRoute/>
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
        render: () => <SettingsRoute/>
    });
}

export default async function registerContentGovernance() {
    await registerTranslations();
    register();
}
