/**
 * What feeds each subdomain of the drawer. Three checks of the module find what the subdomains show:
 *
 * - the page check (`PAGE`): the check of the server and the checks of the browser on the preview of the page;
 * - the GEO check (`GEO`): the fetches of the public page by each AI crawler, run on demand, and the citations
 *   of the last BotRank sync;
 * - the compliance scan (`COMPLIANCE`): the page and what it shows, against the term rules of the site.
 *
 * A subdomain with a dedicated view shows the view of its check, such as the axe-core checklist of
 * `accessibility`, the Web Vitals of `vitals` or the GEO score of `geo.score`. Every subdomain then lists the
 * findings of the rules that the site places in it and that its view does not show, whatever check found them.
 * Example: a site moves the rule `seo.canonical` to `links`, and the Links section lists its finding.
 */

export const PAGE = 'page';
export const GEO = 'geo';
export const COMPLIANCE = 'compliance';

/** The subdomains with a dedicated view, and the check that feeds the view. */
const VIEWS = {
    accessibility: PAGE,
    seo: PAGE,
    vitals: PAGE,
    readability: PAGE,
    ecodesign: PAGE,
    links: PAGE,
    health: PAGE,
    'geo.score': GEO,
    'geo.crawlers': GEO,
    'geo.files': GEO,
    'geo.schema': GEO,
    'geo.citations': GEO,
    terms: COMPLIANCE,
    glossary: COMPLIANCE
};

/** The evaluator of the term rules, which the compliance scan applies. */
export const PATTERN = 'pattern';

/**
 * @param {string} id a subdomain id
 * @returns {?string} the check of the dedicated view of the subdomain, or null for a subdomain that lists findings
 */
export const viewOf = id => VIEWS[id] || null;

/**
 * The checks that a domain needs: the check of each dedicated view of its subdomains, the page check for a
 * subdomain that lists findings, and the compliance scan for a term rule that the site places in one of them.
 * The GEO check runs on demand only, where a GEO view offers it.
 *
 * @param {{subdomains: Array<{id: string}>}} domain a domain of the layout
 * @param {{rulesIn: function(string): Array<{evaluator: ?string}>}} places where the rules of the site sit
 * @returns {Set<string>} the checks, among PAGE, GEO and COMPLIANCE
 */
export function checksOf(domain, places) {
    const out = new Set();
    domain.subdomains.forEach(subdomain => {
        out.add(viewOf(subdomain.id) || PAGE);
        if (places.rulesIn(subdomain.id).some(rule => rule.evaluator === PATTERN)) {
            out.add(COMPLIANCE);
        }
    });
    return out;
}

/**
 * The id of the element of a subdomain section: a subdomain id may hold a dot, as in `geo.score`, which a
 * CSS selector reads as a class.
 *
 * @param {string} id the subdomain id
 * @returns {string} such as `content-governance-subdomain-geo-score`
 */
export const sectionElementId = id => `content-governance-subdomain-${id.replaceAll(/[^A-Za-z0-9_-]/g, '-')}`;
