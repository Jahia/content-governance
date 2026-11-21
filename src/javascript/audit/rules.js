import {gql} from '@apollo/client';
import {wordFinding} from './ruleText';

/** The rules of a site, with their texts in the editor's interface language (English when a rule has none). */
export const CONTENT_RULES = gql`
    query ContentGovernanceRules($siteKey: String!, $language: String) {
        contentGovernance {
            contentRules(siteKey: $siteKey, language: $language) {
                id
                domain
                evaluator
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
            }
        }
    }
`;

/**
 * The rules of a site by id, from the list that the GraphQL API or the catalog file gives.
 *
 * @param {Array<{id: string, severity: string, enabled: boolean, minimum: ?number, maximum: ?number}>} list the rules
 * @param {string} [language] the language of their texts: the editor's interface language
 * @returns {{get: function(string): object, find: function(string): ?object, all: function(): Array<object>,
 *     language: string, word: function(string, object): object}} `get` throws on an id the catalog does not
 *     know, and `find` answers undefined; `word(id, values)` gives the texts of a finding of a rule
 */
export function ruleBook(list, language = 'en') {
    const byId = new Map(list.map(rule => [rule.id, {enabled: true, minimum: null, maximum: null, title: [], ...rule}]));
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
 * True when a value is inside the limits that a rule has.
 *
 * @param {object} rule a rule of the book
 * @param {number} value the measured value
 * @returns {boolean} whether the value passes
 */
export function accepts(rule, value) {
    return (rule.minimum === null || value >= rule.minimum) && (rule.maximum === null || value <= rule.maximum);
}

/**
 * A list of findings and the function that adds one. A finding carries the id of its rule, the
 * severity of the rule, its limits, and its texts worded from the rule; a rule the site turned off adds
 * nothing.
 *
 * @param {{get: function(string): object, word: function(string, object): object}} rules the rules of the site
 * @returns {{recs: Array<{key: string, rule: string, severity: string, params: object, title: string,
 *     detail: string, label: string}>, push: Function}} `push(ruleId, key, params)`; `key` stays for the
 *     actions that other modules add to a finding
 */
export function findings(rules) {
    const recs = [];
    const push = (ruleId, key, params) => {
        const rule = rules.get(ruleId);
        if (rule.enabled) {
            const values = {...limits(rule), ...params};
            recs.push({key: ruleId, rule: ruleId, severity: rule.severity, params: values, ...rules.word(ruleId, values)});
        }
    };
    return {recs, push};
}
