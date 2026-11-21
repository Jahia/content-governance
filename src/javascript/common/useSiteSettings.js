import {useMemo} from 'react';
import {useQuery} from '@apollo/client';
import {SITE_SETTINGS} from './settings.gql';

/**
 * The Content Governance settings of a site, with helpers the drawer and the dashboards read.
 * Fetched from the network on every mount: an administrator may have switched a tab off since.
 * The site stores the ids it switched off, so every tab of the drawer's registry is on until then.
 *
 * @param {string} siteKey the site
 * @returns {{loading: boolean, error: object, settings: object, isOn: Function, refetch: Function}}
 *   `isOn('geo')` or `isOn('geo', 'citations')` is true when the site enables the module and keeps
 *   the tab (and its part) switched on.
 */
export function useSiteSettings(siteKey) {
    const {data, loading, error, refetch} = useQuery(SITE_SETTINGS, {
        variables: {siteKey},
        skip: !siteKey,
        fetchPolicy: 'network-only'
    });
    const settings = data?.contentGovernance?.siteSettings || null;

    const isOn = useMemo(() => {
        const off = new Set(settings?.disabled || []);
        return (tab, part) => Boolean(settings?.moduleEnabled) && !off.has(tab) && (!part || !off.has(`${tab}.${part}`));
    }, [settings]);

    return {loading, error, settings, isOn, refetch};
}
