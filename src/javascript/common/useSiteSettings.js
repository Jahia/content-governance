import {useMemo} from 'react';
import {useQuery} from '@apollo/client';
import {SITE_SETTINGS} from './settings.gql';

/**
 * The Content Governance settings of a site, with helpers the drawer and the dashboards read.
 * Fetched from the network on every mount: an administrator may have switched a tab off since.
 *
 * @param {string} siteKey the site
 * @returns {{loading: boolean, error: object, settings: object, isOn: Function, refetch: Function}}
 *   `isOn('geo')` or `isOn('geo', 'citations')` is true when the section (and the tab) is switched on.
 */
export function useSiteSettings(siteKey) {
    const {data, loading, error, refetch} = useQuery(SITE_SETTINGS, {
        variables: {siteKey},
        skip: !siteKey,
        fetchPolicy: 'network-only'
    });
    const settings = data?.contentGovernance?.siteSettings || null;

    const isOn = useMemo(() => (section, tab) => {
        const found = settings?.sections.find(s => s.id === section);
        if (!found || !found.enabled) {
            return false;
        }
        return !tab || Boolean(found.tabs.find(t => t.id === tab)?.enabled);
    }, [settings]);

    return {loading, error, settings, isOn, refetch};
}
