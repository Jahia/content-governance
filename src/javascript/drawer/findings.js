/**
 * The findings of the drawer's sections, for its "Fix first" list. A finding is
 * `{key, section, tab, severity, title}`: `severity` is `critical`, `serious`, `moderate` or
 * `minor` (axe-core's impacts, which the module's recommendations already use), `title` is
 * already translated, `tab` is the tab of the section that shows it (null for a section without tabs).
 */

const RANK = {critical: 0, serious: 1, moderate: 2, minor: 3};

/** Each Page quality tab with recommendations (also its key in the results) and the namespace of their texts. */
const AUDIT_TABS = [['seo', 'seo'], ['vitals', 'vitals'], ['readability', 'readability'], ['ecodesign', 'eco'], ['links', 'links'], ['jahia', 'jahia']];

/**
 * @param {Array<object>} findings findings of every section
 * @param {number} max how many to keep
 * @returns {Array<object>} the most serious first; findings of equal severity keep their order
 *     (Page quality, then GEO readiness, then Compliance, as the drawer lists its sections)
 */
export function rankFindings(findings, max = 5) {
    return findings
        .map((finding, index) => ({finding, index}))
        .sort((a, b) => ((RANK[a.finding.severity] ?? 4) - (RANK[b.finding.severity] ?? 4)) || (a.index - b.index))
        .slice(0, max)
        .map(({finding}) => finding);
}

/**
 * The findings of a Page quality audit: every accessibility violation, then the recommendations of
 * the other tabs.
 *
 * @param {object} results the audit results (`{a11y, seo, vitals, ...}`); missing tabs are skipped
 * @param {Function} t the section's translation function (`audit.` prefix)
 * @returns {Array<object>} findings
 */
export function auditFindings(results, t) {
    if (!results) {
        return [];
    }
    const out = [];
    (results.a11y?.violations || []).forEach(rule => {
        out.push({key: `audit.a11y.${rule.id}`, section: 'audit', tab: 'accessibility', severity: rule.impact || 'minor', title: rule.help || rule.id});
    });
    AUDIT_TABS.forEach(([tab, ns]) => {
        (results[tab]?.recommendations || []).forEach(rec => {
            out.push({
                key: `audit.${tab}.${rec.key}`,
                section: 'audit',
                tab,
                severity: rec.severity || 'minor',
                title: t(`${ns}.recs.${rec.key}.title`, rec.params)
            });
        });
    });
    return out;
}

/**
 * The findings of a compliance report: one per term, however often it is used.
 *
 * @param {object} report the page scan (`{issues: [{term}]}`)
 * @param {Function} t the section's translation function (`compliance.` prefix)
 * @returns {Array<object>} findings, all serious
 */
export function complianceFindings(report, t) {
    const uses = new Map();
    (report?.issues || []).forEach(issue => uses.set(issue.term, (uses.get(issue.term) || 0) + 1));
    return [...uses].map(([term, count]) => ({
        key: `compliance.${term}`,
        section: 'compliance',
        tab: null,
        severity: 'serious',
        title: t('fixFirst.term', {term, count})
    }));
}
