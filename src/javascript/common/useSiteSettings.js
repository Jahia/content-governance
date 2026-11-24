import {useMemo} from 'react';
import {useQuery} from '@apollo/client';
import {SITE_SETTINGS} from './settings.gql';
import {GOVERNANCE_PANEL} from './module';

/**
 * The id in the layout of each key of a drawer tab, or of a part of a tab, whose id differs: the drawer still
 * registers its tabs by these keys, and the layout of the site names the domains and the subdomains. The server
 * maps them the same way (LegacyFeatures.java).
 */
const LAYOUT_IDS = {
    audit: 'quality',
    'audit.jahia': 'health',
    'audit.links': 'links',
    'audit.accessibility': 'accessibility',
    'audit.vitals': 'vitals',
    'audit.ecodesign': 'ecodesign',
    'audit.seo': 'seo',
    'audit.readability': 'readability',
    'geo.crawler': 'geo.crawlers'
};

/** The id in the layout of a key of the drawer registry, such as `quality` for `audit`. */
export const layoutId = key => LAYOUT_IDS[key] || key;

/**
 * The switches of a site, read from its settings. The settings list the ids that are off on the site: a panel
 * (`governance`, `chat`), a domain (`quality`) or a subdomain (`geo.citations`). Everything else is on, and nothing
 * is on where the site does not enable the module.
 *
 * @param {object} settings the site settings, or null while they load
 * @returns {{isOn: Function, isPanelOn: Function}} `isOn('geo')` or `isOn('geo', 'citations')` is true
 *   when the site keeps the governance panel, the tab and its part on; `isPanelOn('chat')` is true
 *   when the site keeps the panel on
 */
export function siteSwitches(settings) {
    const off = new Set(settings?.disabled || []);
    const isPanelOn = panel => Boolean(settings?.moduleEnabled) && !off.has(panel);
    const isOn = (tab, part) => isPanelOn(GOVERNANCE_PANEL) && !off.has(layoutId(tab)) &&
        (!part || !off.has(layoutId(`${tab}.${part}`)));
    return {isOn, isPanelOn};
}

/**
 * The Content Governance settings of a site, with the switches the drawer and the dashboards read.
 * Fetched from the network on every mount: an administrator may have switched a tab off since.
 *
 * @param {string} siteKey the site
 * @returns {{loading: boolean, error: object, settings: object, isOn: Function, isPanelOn: Function, refetch: Function}}
 *   `isOn` and `isPanelOn` as `siteSwitches` gives them
 */
export function useSiteSettings(siteKey) {
    const {data, loading, error, refetch} = useQuery(SITE_SETTINGS, {
        variables: {siteKey},
        skip: !siteKey,
        fetchPolicy: 'network-only'
    });
    const settings = data?.contentGovernance?.siteSettings || null;
    const {isOn, isPanelOn} = useMemo(() => siteSwitches(settings), [settings]);

    return {loading, error, settings, isOn, isPanelOn, refetch};
}
