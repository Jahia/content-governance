import {isSerious} from '../drawer/findings';

/** The results key of each Page quality tab whose findings are recommendations. */
const RECOMMENDATION_TABS = {seo: 'seo', vitals: 'vitals', readability: 'readability', ecodesign: 'ecodesign', links: 'links', jahia: 'jahia'};

const countOf = list => ({count: list.length, serious: list.some(item => isSerious(item.severity || item.impact))});

/**
 * What each Page quality tab has to review: how many findings, and whether one of them is critical
 * or serious (its count is then red, grey otherwise). A tab the audit did not measure has no entry.
 *
 * @param {object} [results] the audit results (`{a11y, seo, vitals, ...}`)
 * @returns {Object<string, {count: number, serious: boolean}>} by tab id
 */
export function tabCounts(results) {
    const counts = {};
    if (results?.a11y) {
        // The checks of axe-core, and the HTML rules of the server that no other tab words
        counts.accessibility = countOf([...(results.a11y.violations || []), ...(results.a11y.recommendations || [])]);
    }
    Object.entries(RECOMMENDATION_TABS).forEach(([tab, key]) => {
        if (results?.[key]) {
            counts[tab] = countOf(results[key].recommendations || []);
        }
    });
    return counts;
}
