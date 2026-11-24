/**
 * What each subdomain has to review from the GEO check (geo/useGeoCheck.js): the counts of the GEO views on
 * the report of a published page, and the failed checks of the GEO score on the rules that a site places in a
 * subdomain with no GEO view, such as `geo.content`. drawer/sections.js turns them into findings.
 */

/** Things worth a badge on the Site files view: no llms.txt, or an AI bot disallowed. */
function filesIssueCount(report) {
    const files = report.siteFiles || {};
    let n = 0;
    if (!(files.llms && files.llms.present)) {
        n++;
    }
    if (files.robots && files.robots.disallowedAiBotCount) {
        n += files.robots.disallowedAiBotCount;
    }
    if (files.robots && files.robots.present === false) {
        n++;
    }
    return n;
}

/**
 * @param {{report: ?object}} geo the state of the GEO check
 * @returns {?object} the report of a published page, whose views the GEO subdomains show, or null
 */
export const publishedReport = geo => (geo.report && geo.report.published ? geo.report : null);

/** The count of each GEO view on a published report, and whether its count is serious. */
const VIEW_COUNTS = {
    'geo.score': report => ({count: report.score ? report.score.criticalFailed : 0, serious: true}),
    'geo.crawlers': report => ({count: report.blockedCount || 0, serious: true}),
    'geo.files': report => ({count: filesIssueCount(report), serious: false}),
    'geo.schema': report => ({count: report.schema && report.schema.mapped && !report.schema.valid ? 1 : 0, serious: false})
};

/** The checks of the GEO score that failed on some rules. */
const failedOn = (report, ruleIds) => (report?.score?.checks || []).filter(check => !check.passed && ruleIds.has(check.rule));

/**
 * What the section of a subdomain has to review from the GEO check.
 *
 * @param {{report: ?object, citationsState: string}} geo the state of the GEO check
 * @param {string} id the subdomain
 * @param {Set<string>} ruleIds the rules on in the subdomain
 * @returns {{count: ?number, serious: boolean, unavailable: boolean}} `count` is null when the GEO check has
 *     no count to give; `unavailable` is true for the citations of a site with no BotRank connection
 */
export function geoStateOf(geo, id, ruleIds) {
    const report = publishedReport(geo);
    if (id === 'geo.citations') {
        return {count: null, serious: false, unavailable: geo.citationsState === 'notConfigured'};
    }
    if (VIEW_COUNTS[id]) {
        return report ? {...VIEW_COUNTS[id](report), unavailable: false} : {count: null, serious: false, unavailable: false};
    }
    const failed = failedOn(report, ruleIds);
    return {count: report ? failed.length : null, serious: failed.some(check => check.severity === 'critical'), unavailable: false};
}
