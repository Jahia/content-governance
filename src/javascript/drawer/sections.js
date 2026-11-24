import {COMPLIANCE, GEO, PAGE, viewOf} from './subdomains';
import {pageStateOf, placeOf} from '../audit/subdomainResults';
import {geoStateOf} from '../geo/geoState';
import {complianceStateOf, termPlaceOf} from '../compliance/complianceState';
import {auditFindings, complianceFindings} from './findings';

/**
 * What each section of a domain has to review, and the findings of each domain, from the three checks of the
 * drawer (drawer/subdomains.js). A section counts what its dedicated view counts, and the findings of the rules
 * that the site places in it from the other checks. Example: the site moves `seo.title` to `geo.score`, and the
 * section of the GEO score counts the critical checks of the score and the finding of `seo.title`.
 */

const NONE = Object.freeze({count: null, serious: false});

/** The count of the parts that give one, null when none does, and whether a counted part is serious. */
const combine = parts => {
    const counted = parts.filter(part => typeof part.count === 'number');
    return {
        count: counted.length > 0 ? counted.reduce((sum, part) => sum + part.count, 0) : null,
        serious: counted.some(part => part.serious && part.count > 0)
    };
};

/**
 * What one check counts in each subdomain.
 *
 * @param {object} engines the state of the page check, the GEO check and the compliance scan
 * @param {object} places where the rules of the site sit (drawer/layout.js)
 * @param {?string} complianceUnavailable why the editor cannot use the compliance scan, or null
 * @returns {{page: Function, geo: Function, compliance: Function}} each gives `{count, serious}` for a subdomain id
 */
const checkStates = (engines, places, complianceUnavailable) => ({
    page: id => (engines.page.ready ? pageStateOf(engines.page.results, id, places.subdomainOf) : NONE),
    geo: id => geoStateOf(engines.geo, id, new Set(places.rulesIn(id).map(rule => rule.id))),
    compliance: id => (complianceUnavailable ? NONE : complianceStateOf(engines.compliance, id, places.subdomainOf))
});

/**
 * The sections of a domain, in the order of the layout: each subdomain with its label, its help and what it has
 * to review.
 *
 * @param {{subdomains: Array<{id: string, label: string, help: ?string}>}} domain a domain of the layout
 * @param {object} engines the state of the page check, the GEO check and the compliance scan
 * @param {object} places where the rules of the site sit
 * @param {?string} complianceUnavailable why the editor cannot use the compliance scan, or null
 * @returns {Array<{id: string, label: string, help: ?string, count: ?number, serious: boolean, unavailable: boolean}>}
 *     `count` is null when no check counted the subdomain yet; `unavailable` is true for citations with no BotRank
 *     connection
 */
export function sectionsOf(domain, engines, places, complianceUnavailable) {
    const states = checkStates(engines, places, complianceUnavailable);
    return domain.subdomains.map(subdomain => {
        const view = viewOf(subdomain.id);
        const page = states.page(subdomain.id);
        const geo = states.geo(subdomain.id);
        const compliance = states.compliance(subdomain.id);
        // The check of the view counts, even 0; another check counts what it found there
        const parts = [
            ...(view === PAGE || view === null || page.count > 0 ? [page] : []),
            ...(view === GEO || (view === null && geo.count > 0) ? [geo] : []),
            ...(view === COMPLIANCE || compliance.count > 0 ? [compliance] : [])
        ];
        return {
            id: subdomain.id,
            label: subdomain.label,
            help: subdomain.help,
            ...combine(parts),
            unavailable: view === GEO && Boolean(geo.unavailable)
        };
    });
}

/**
 * The findings of each domain, for "Fix first" and the other entries of the drawer, in the order of its
 * subdomains. Each finding names the domain (`section`) and the subdomain (`tab`) that show it. A finding of a
 * subdomain that the site does not show is left out.
 *
 * @param {Array<object>} domains the domains of the layout
 * @param {object} engines the state of the page check, the GEO check and the compliance scan
 * @param {object} places where the rules of the site sit
 * @param {?string} complianceUnavailable why the editor cannot use the compliance scan, or null
 * @param {{audit: Function, geo: Function, compliance: Function}} t the translation function of each check
 * @returns {Object<string, Array<object>>} the findings by domain id
 */
export function domainFindings(domains, engines, places, complianceUnavailable, t) {
    const states = checkStates(engines, places, complianceUnavailable);
    const page = engines.page.ready ?
        auditFindings(engines.page.results, t.audit, (rule, key) => placeOf(rule, key, places.subdomainOf)) : [];
    const report = complianceUnavailable ? null : engines.compliance.result.report;
    const terms = report ? complianceFindings(report, t.compliance, ruleId => termPlaceOf(ruleId, places.subdomainOf)) : [];
    return Object.fromEntries(domains.map(domain => [domain.id, domain.subdomains.flatMap(subdomain => {
        const view = viewOf(subdomain.id);
        const geo = view === GEO || view === null ? states.geo(subdomain.id) : NONE;
        return [
            ...page.filter(finding => finding.tab === subdomain.id),
            ...(geo.count > 0 ? [{
                key: subdomain.id,
                tab: subdomain.id,
                severity: geo.serious ? 'serious' : 'moderate',
                title: t.geo('fixFirst', {tab: subdomain.label, count: geo.count})
            }] : []),
            ...terms.filter(finding => finding.tab === subdomain.id)
        ].map(finding => ({...finding, section: domain.id}));
    })]));
}
