import {useEffect, useState} from 'react';
import {gql, useApolloClient} from '@apollo/client';
import {wordFinding} from './ruleText';

/** The rules of a site, with their texts in the editor's interface language (English when a rule has none). */
export const CONTENT_RULES = gql`
    query ContentGovernanceRules($siteKey: String!, $language: String) {
        contentGovernance {
            contentRules(siteKey: $siteKey, language: $language) {
                id
                subdomain
                evaluator
                runtime
                title {
                    form
                    text
                }
                label
                advice
                severity
                enabled
                unit
                minimum
                maximum
                trigger
                view
                valueLabels {
                    key
                    label
                }
            }
        }
    }
`;

/** The value of `trigger` of a rule that runs only when an editor runs it in the drawer. */
export const MANUAL = 'manual';

/**
 * The rules of a site by id, from the list that the GraphQL API or the catalog file gives.
 *
 * @param {Array<{id: string, severity: string, enabled: boolean, runtime: ?string, minimum: ?number, maximum: ?number}>} list the
 *     rules; a rule with no `runtime` runs on the server, with no `trigger` in every audit, and with no `view` as a card
 * @param {string} [language] the language of their texts: the editor's interface language
 * @returns {{get: function(string): object, find: function(string): ?object, all: function(): Array<object>,
 *     language: string, word: function(string, object): object}} `get` throws on an id the catalog does not
 *     know, and `find` answers undefined; `word(id, values)` gives the texts of a finding of a rule
 */
export function ruleBook(list, language = 'en') {
    const byId = new Map(list.map(rule => [rule.id, {
        enabled: true, runtime: 'server', trigger: 'auto', view: 'card', minimum: null, maximum: null, title: [], valueLabels: [], ...rule
    }]));
    return {
        language,
        get(id) {
            const rule = byId.get(id);
            if (!rule) {
                throw new Error(`The rule catalog has no rule ${id}`);
            }
            return rule;
        },
        find(id) {
            return byId.get(id);
        },
        all() {
            return [...byId.values()];
        },
        word(id, values) {
            const rule = byId.get(id) || {id};
            return wordFinding(rule, {...limits(rule), ...values}, language);
        }
    };
}

/**
 * The rules of the site that owns a path, read at the start of an audit. Never cached: a site may have
 * changed a rule since the last audit.
 *
 * @param {object} client the Apollo client
 * @param {string} path a path under /sites/<key>
 * @param {string} [language] the editor's interface language, for the texts of the rules
 * @returns {Promise<{get: function(string): object}>} the rules of the site
 */
export async function fetchRules(client, path, language = 'en') {
    const siteKey = (path || '').split('/')[2];
    const {data} = await client.query({query: CONTENT_RULES, variables: {siteKey, language}, fetchPolicy: 'network-only'});
    return ruleBook(data.contentGovernance.contentRules, language);
}

/**
 * The limits of a rule as interpolation params of its texts: `min` and `max` when the rule has them.
 *
 * @param {object} rule a rule of the book
 * @returns {object} the params
 */
export function limits(rule) {
    const out = {};
    if (rule.minimum !== null && rule.minimum !== undefined) {
        out.min = rule.minimum;
    }
    if (rule.maximum !== null && rule.maximum !== undefined) {
        out.max = rule.maximum;
    }
    return out;
}

/**
 * The rules of the site of a page, with their texts in the editor's interface language, read once per page and
 * language: what the cards of the drawer and the runs on demand read.
 *
 * @param {string} path the page
 * @param {string} language the editor's interface language
 * @returns {?object} the rules (`ruleBook`), or null until they are read or when they could not be read
 */
export function useRuleBook(path, language) {
    const client = useApolloClient();
    const [book, setBook] = useState(null);
    useEffect(() => {
        let cancelled = false;
        setBook(null);
        fetchRules(client, path, language).then(rules => {
            if (!cancelled) {
                setBook(rules);
            }
        }).catch(error => console.warn('[content-governance] the rules of the site could not be read', error));
        return () => {
            cancelled = true;
        };
    }, [client, path, language]);
    return book;
}
