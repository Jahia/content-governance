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

/**
 * The facts of the page that the GEO check reads from the repository and from the last site scan, and that
 * are findings, each with the subdomain whose section shows it and its level.
 *
 * Example: a page that nothing links to, not even the menu, is the finding `orphan`, Moderate, in `geo.content`.
 *
 * @param {?object} report the report of a published page
 * @returns {Array<{id: string, place: string, level: string}>} the findings
 */
export function pageFindingsOf(report) {
    if (!report) {
        return [];
    }
    const sitemap = report.sitemap || {};
    const links = report.links;
    const languages = report.languages || {};
    return [
        report.visibility?.guestReadable === false && {id: 'gated', place: 'geo.crawlers', level: 'serious'},
        report.visibility?.noindex && {id: 'noindex', place: 'geo.crawlers', level: 'minor'},
        sitemap.missing && {id: 'notListed', place: 'geo.files', level: 'moderate'},
        sitemap.noindexListed && {id: 'noindexListed', place: 'geo.files', level: 'moderate'},
        sitemap.staleDetail && {id: 'stale', place: 'geo.files', level: 'minor'},
        report.vanity && {id: 'vanity', place: 'geo.files', level: report.vanity.kind === 'unresolvable' ? 'moderate' : 'minor'},
        links && links.content === 0 && links.nav === 0 && {id: 'orphan', place: 'geo.content', level: 'moderate'},
        links && links.content === 0 && links.nav > 0 && {id: 'navOnly', place: 'geo.content', level: 'minor'},
        (languages.missing || []).length > 0 && {id: 'languagesMissing', place: 'geo.content', level: 'minor'},
        (languages.notPublished || []).length > 0 && {id: 'languagesUnpublished', place: 'geo.content', level: 'moderate'}
    ].filter(Boolean);
}

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
    const placed = pageFindingsOf(report).filter(finding => finding.place === id);
    const placedSerious = placed.some(finding => finding.level === 'serious');
    if (VIEW_COUNTS[id]) {
        if (!report) {
            return {count: null, serious: false, unavailable: false};
        }
        const view = VIEW_COUNTS[id](report);
        return {count: view.count + placed.length, serious: (view.serious && view.count > 0) || placedSerious, unavailable: false};
    }
    const failed = failedOn(report, ruleIds);
    return {
        count: report ? failed.length + placed.length : null,
        serious: failed.some(check => check.severity === 'critical') || placedSerious,
        unavailable: false
    };
}
