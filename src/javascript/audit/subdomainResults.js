import {withoutTimingFindings} from './vitalsFindings';
import {isSerious} from '../drawer/findings';

/**
 * The results of the page check by subdomain. The results keep one entry per subdomain with a view of the
 * page check, under a key of their own (`a11y` for accessibility, `jahia` for health), and one entry,
 * `other`, for the rules of every other subdomain. A stored audit keeps this shape.
 *
 * A recommendation shows in the subdomain of its rule on the site, whatever entry holds it. Example: an audit
 * holds the recommendation of `seo.canonical` under `seo`, and the site then moves the rule to `links`. The
 * Links section lists the recommendation.
 */

/** The key in the results of each subdomain with a view of the page check. */
export const RESULT_OF = {
    accessibility: 'a11y',
    seo: 'seo',
    vitals: 'vitals',
    readability: 'readability',
    ecodesign: 'ecodesign',
    links: 'links',
    health: 'jahia'
};

/** The key in the results of the recommendations of the rules of the other subdomains. */
export const OTHER = 'other';

/** The keys of the results that hold recommendations, in the order of the views. */
export const RECOMMENDATION_KEYS = [...Object.values(RESULT_OF), OTHER];

const SUBDOMAIN_OF = Object.fromEntries(Object.entries(RESULT_OF).map(([subdomain, key]) => [key, subdomain]));

/**
 * @param {?string} rule the id of the rule of a recommendation
 * @param {string} key the key of the results that hold it
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {?string} where the recommendation shows: the subdomain of its rule on the site, else the
 *     subdomain of the results that hold it (a rule that the site no longer has), else null
 */
export const placeOf = (rule, key, subdomainOf) => (rule && subdomainOf(rule)) || SUBDOMAIN_OF[key] || null;

/** The recommendations under a key, without the findings on timings of an audit stored before 1.11. */
const recommendationsUnder = (results, key) => {
    const list = results?.[key]?.recommendations || [];
    return key === 'vitals' ? withoutTimingFindings(list) : list;
};

/**
 * The recommendations of the page check that show in a subdomain, each with `bucket`, the key of the
 * results that hold it: its finding key and the namespace of its older texts follow that key.
 *
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {Array<object>} the recommendations
 */
export function recommendationsIn(results, subdomainId, subdomainOf) {
    return RECOMMENDATION_KEYS.flatMap(key => recommendationsUnder(results, key)
        .filter(rec => placeOf(rec.rule, key, subdomainOf) === subdomainId)
        .map(rec => ({...rec, bucket: key})));
}

/**
 * The views of the rules that show as facts or scores (audit/ruleViews.js) and that show in a subdomain. The results
 * keep them under `other`.
 *
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {Array<object>} the views
 */
export const viewsIn = (results, subdomainId, subdomainOf) => (results?.[OTHER]?.views || [])
    .filter(view => placeOf(view.rule, OTHER, subdomainOf) === subdomainId);

/**
 * The rules of the browser that no check gave a result for, and that show in a subdomain.
 *
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {Array<{rule: string, label: string}>} the rules
 */
export const notCheckedIn = (results, subdomainId, subdomainOf) => RECOMMENDATION_KEYS
    .flatMap(key => (results?.[key]?.notChecked || []).filter(item => placeOf(item.rule, key, subdomainOf) === subdomainId));

/**
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @returns {?object} the outcome of the step that did not finish for the subdomain, or null
 */
export const incompleteIn = (results, subdomainId) => results?.incomplete?.[RESULT_OF[subdomainId] || OTHER] || null;

/**
 * Whether the page check measured a subdomain: a subdomain with a view of its own needs its results, and any
 * other subdomain an audit. An audit stored before 2.0.0 has no `other`, and lists what it holds.
 *
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @returns {boolean} true when the results hold the subdomain
 */
export const measures = (results, subdomainId) => Boolean(RESULT_OF[subdomainId] ? results?.[RESULT_OF[subdomainId]] : results);

/**
 * What the section of a subdomain has to review from the page check: how many findings, whether one of them
 * is critical or serious (its count is then red). The accessibility section also counts the violations of
 * axe-core.
 *
 * @param {?object} results the results of the page check
 * @param {string} subdomainId the subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {{count: ?number, serious: boolean}} `count` is null when the audit did not measure the subdomain
 */
export function pageStateOf(results, subdomainId, subdomainOf) {
    if (!measures(results, subdomainId)) {
        return {count: null, serious: false};
    }
    const violations = subdomainId === 'accessibility' ? results.a11y?.violations || [] : [];
    const severities = [
        ...violations.map(violation => violation.severity || violation.impact),
        ...recommendationsIn(results, subdomainId, subdomainOf).map(rec => rec.severity)
    ];
    return {count: severities.length, serious: severities.some(isSerious)};
}
