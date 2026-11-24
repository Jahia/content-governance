import {isSerious} from '../drawer/findings';

/**
 * Where the terms that the compliance scan finds show in the drawer: in the subdomain of their rule on the
 * site, `terms` for a rule with none. Example: a site moves the term rule `brand.seamless` to `writing`, and the
 * Writing section lists the uses of "seamless".
 */

/** The subdomain of a term rule that names none. */
export const DEFAULT_SUBDOMAIN = 'terms';

/**
 * @param {string} ruleId the rule of a term
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {string} the subdomain where the term shows
 */
export const termPlaceOf = (ruleId, subdomainOf) => subdomainOf(ruleId) || DEFAULT_SUBDOMAIN;

/**
 * @param {?object} report the report of the scan (`{issues: [{ruleId}]}`)
 * @param {string} id a subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {Array<object>} the uses of terms that show in the subdomain
 */
export const issuesIn = (report, id, subdomainOf) => (report?.issues || []).filter(issue => termPlaceOf(issue.ruleId, subdomainOf) === id);

/**
 * What the section of a subdomain has to review from the compliance scan: each use of a term of its rules.
 *
 * @param {{result: object}} scan the state of the compliance scan
 * @param {string} id the subdomain
 * @param {function(string): (string|undefined)} subdomainOf the subdomain of a rule on the site
 * @returns {{count: ?number, serious: boolean}} `count` is null while the scan has no report
 */
export function complianceStateOf(scan, id, subdomainOf) {
    if (!scan.result.report) {
        return {count: null, serious: false};
    }
    const issues = issuesIn(scan.result.report, id, subdomainOf);
    return {count: issues.length, serious: issues.some(issue => isSerious(issue.severity || 'serious'))};
}
