import {gql} from '@apollo/client';

export const CONTENT_RULES = gql`
    query ContentGovernanceRules($siteKey: String!) {
        contentGovernance {
            contentRules(siteKey: $siteKey) {
                id
                domain
                advice
                severity
                enabled
                minimum
                maximum
            }
        }
    }
`;

/**
 * The rules of a site by id, from the list that the GraphQL API or the catalog file gives.
 *
 * @param {Array<{id: string, domain: string, advice: ?string, severity: string, enabled: boolean, minimum: ?number, maximum: ?number}>} list the rules
 * @returns {{get: function(string): object}} `get` throws on an id the catalog does not know
 */
export function ruleBook(list) {
    const byId = new Map(list.map(rule => [rule.id, {enabled: true, minimum: null, maximum: null, ...rule}]));
    return {
        get(id) {
            const rule = byId.get(id);
            if (!rule) {
                throw new Error(`The rule catalog has no rule ${id}`);
            }
            return rule;
        }
    };
}

/**
 * The rules of the site that owns a path, read at the start of an audit. Never cached: a site may have
 * changed a rule since the last audit.
 *
 * @param {object} client the Apollo client
 * @param {string} path a path under /sites/<key>
 * @returns {Promise<{get: function(string): object}>} the rules of the site
 */
export async function fetchRules(client, path) {
    const siteKey = (path || '').split('/')[2];
    const {data} = await client.query({query: CONTENT_RULES, variables: {siteKey}, fetchPolicy: 'network-only'});
    return ruleBook(data.contentGovernance.contentRules);
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
 * severity of the rule and its limits; a rule the site turned off adds nothing.
 *
 * @param {{get: function(string): object}} rules the rules of the site
 * @returns {{recs: Array<{key: string, rule: string, severity: string, params: object}>, push: Function}}
 *   `push(ruleId, key, params)`, where `key` names the text of the finding
 */
export function findings(rules) {
    const recs = [];
    const push = (ruleId, key, params) => {
        const rule = rules.get(ruleId);
        if (rule.enabled) {
            recs.push({key, rule: ruleId, severity: rule.severity, params: {...limits(rule), ...params}});
        }
    };
    return {recs, push};
}
