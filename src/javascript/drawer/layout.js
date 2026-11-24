import {useEffect, useMemo, useState} from 'react';
import {gql, useApolloClient} from '@apollo/client';
import {GOVERNANCE_PANEL} from '../common/module';

/**
 * The layout of a site as the drawer reads it: its domains, in the order of the site, each with the
 * subdomains that are on, and the subdomain of each rule on the site. The server applies the layout
 * of the site to the tree of the system site, so an item that the site switches off is not listed.
 */
export const DRAWER_LAYOUT = gql`
    query ContentGovernanceDrawerLayout($siteKey: String!, $language: String) {
        contentGovernance {
            contentDomains(siteKey: $siteKey, language: $language) {
                id
                label
                help
                panel
                subdomains {
                    id
                    label
                    help
                }
            }
            contentRules(siteKey: $siteKey) {
                id
                subdomain
                evaluator
                enabled
            }
        }
    }
`;

/** The domains of a site and the ids of their subdomains that are on: whether the governance panel has a domain. */
export const DRAWER_DOMAINS = gql`
    query ContentGovernanceDrawerDomains($siteKey: String!) {
        contentGovernance {
            contentDomains(siteKey: $siteKey) {
                id
                panel
                subdomains {
                    id
                }
            }
        }
    }
`;

const EMPTY = Object.freeze([]);

/**
 * The domains that the governance panel shows, with their subdomains. A domain of the panel of another
 * module, and a domain with no subdomain on, are left out.
 *
 * @param {Array<{id: string, panel: string, subdomains: Array<object>}>} [domains] what `contentDomains` gives
 * @returns {Array<object>} the domains, in their order
 */
export const governanceDomains = domains => (domains || [])
    .filter(domain => domain.panel === GOVERNANCE_PANEL && (domain.subdomains || []).length > 0);

/**
 * Where each rule of the site sits: its subdomain on the site, and its evaluator. A rule that the site
 * switches off is left out: it gives no finding.
 *
 * @param {Array<{id: string, subdomain: ?string, evaluator: ?string, enabled: boolean}>} [rules] what `contentRules` gives
 * @returns {{subdomainOf: function(string): (string|undefined), rulesIn: function(string): Array<object>}}
 *     `subdomainOf(ruleId)` is undefined for a rule the site does not know; `rulesIn(subdomainId)` lists
 *     the rules on in a subdomain
 */
export function rulePlaces(rules) {
    const on = (rules || []).filter(rule => rule.enabled !== false && rule.subdomain);
    const bySubdomain = new Map();
    on.forEach(rule => bySubdomain.set(rule.subdomain, [...(bySubdomain.get(rule.subdomain) || []), rule]));
    const byId = new Map((rules || []).filter(rule => rule.subdomain).map(rule => [rule.id, rule.subdomain]));
    return {
        subdomainOf: id => byId.get(id),
        rulesIn: subdomainId => bySubdomain.get(subdomainId) || EMPTY
    };
}

/**
 * Reads a query once for a site, with no cache. The drawer and its button keep what they read: another query of
 * the module writes the object `contentGovernance` of the cache again, and a watched query would then lose its
 * answer, load again and unmount the drawer.
 *
 * @param {object} query the query
 * @param {object} variables its variables, with `siteKey`
 * @returns {{data: ?object, error: ?object}} the answer, or the error; both null while it loads
 */
function useReadOnce(query, variables) {
    const client = useApolloClient();
    const key = JSON.stringify(variables);
    const [state, setState] = useState({key: null, data: null, error: null});
    useEffect(() => {
        const vars = JSON.parse(key);
        if (!client || !vars.siteKey) {
            return undefined;
        }
        let live = true;
        client.query({query, variables: vars, fetchPolicy: 'no-cache'})
            .then(({data}) => live && setState({key, data: data?.contentGovernance || null, error: null}))
            .catch(error => {
                if (live) {
                    console.warn('[content-governance] the layout of the site could not be read', error);
                    setState({key, data: null, error});
                }
            });
        return () => {
            live = false;
        };
    }, [client, query, key]);
    return state.key === key ? state : {data: null, error: null};
}

/**
 * The layout of a site, read once per opening of the drawer: an administrator who changes the layout sees it
 * the next time the drawer opens.
 *
 * @param {?string} siteKey the site, or null to read nothing
 * @param {string} language the language of the labels: the editor's interface language
 * @returns {{loading: boolean, error: ?object, domains: Array<object>, places: object}} `domains` as
 *     `governanceDomains` gives them, `places` as `rulePlaces` gives them
 */
export function useDrawerLayout(siteKey, language) {
    const {data, error} = useReadOnce(DRAWER_LAYOUT, {siteKey, language});
    const domains = useMemo(() => governanceDomains(data?.contentDomains), [data]);
    const places = useMemo(() => rulePlaces(data?.contentRules), [data]);
    return {loading: Boolean(siteKey) && !data && !error, error, domains, places};
}

/**
 * Whether the layout of a site gives the governance panel at least one domain with a subdomain on. The header
 * of jContent asks it for its button, so it reads the ids only.
 *
 * @param {?string} siteKey the site, or null to read nothing
 * @returns {boolean|undefined} undefined while it loads, and when it cannot be read
 */
export function useHasDomains(siteKey) {
    const {data} = useReadOnce(DRAWER_DOMAINS, {siteKey});
    return data ? governanceDomains(data.contentDomains).length > 0 : undefined;
}
