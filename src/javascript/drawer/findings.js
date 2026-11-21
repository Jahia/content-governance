import {withoutTimingFindings} from '../audit/vitalsFindings';

/**
 * The findings of the drawer's tabs, for its "Fix first" list. A finding is
 * `{key, section, tab, severity, title}`: `section` is the id of the drawer tab that found it (the
 * drawer sets it), `severity` is `critical`, `serious`, `moderate` or `minor` (axe-core's impacts,
 * which the module's recommendations already use), `title` is already translated, `tab` is the part
 * of the drawer tab that shows it (null for a drawer tab without parts).
 * Page quality findings also carry `detail` (what to do, translated), `rule` (the id of their rule
 * in the catalog, null for a check of axe-core and for an audit stored before the catalog) and
 * `count`: the elements that fail an axe-core rule, or the findings of a rule of the server.
 */

const RANK = {critical: 0, serious: 1, moderate: 2, minor: 3};

/** The key of a recommendation of a rule that no tab words itself: it shows the id and the advice of the rule. */
export const RULE_KEY = 'rule';

/** A finding to deal with before publishing: its count is red and it is listed in "Fix first". */
export const isSerious = severity => severity === 'critical' || severity === 'serious';

/**
 * Each Page quality tab with recommendations (also its key in the results) and the namespace of their
 * texts. The recommendations of `a11y` are the HTML rules of the server that no other tab words.
 */
const AUDIT_TABS = [['a11y', 'a11y'], ['seo', 'seo'], ['vitals', 'vitals'], ['readability', 'readability'], ['ecodesign', 'eco'], ['links', 'links'], ['jahia', 'jahia']];

/**
 * @param {Array<object>} findings findings of every section
 * @param {number} max how many to keep
 * @returns {Array<object>} the most serious first; findings of equal severity keep their order,
 *     which is the order of the drawer's tabs
 */
export function rankFindings(findings, max = 5) {
    return findings
        .map((finding, index) => ({finding, index}))
        .sort((a, b) => ((RANK[a.finding.severity] ?? 4) - (RANK[b.finding.severity] ?? 4)) || (a.index - b.index))
        .slice(0, max)
        .map(({finding}) => finding);
}

/**
 * The key of a Page quality finding.
 *
 * @param {string} resultKey the key of its tab in the results (`a11y`, `seo`, `ecodesign`...)
 * @param {string} id the axe-core rule or the recommendation
 * @returns {string} the key, such as `audit.seo.shortTitle`
 */
export const auditFindingKey = (resultKey, id) => `audit.${resultKey}.${id}`;

/**
 * The title and the detail of a recommendation of a Page quality tab: the texts that its rule gave when the
 * audit ran (`title` and `detail`), in the editor's interface language. Only an audit stored by an older
 * version has no `title`: it shows its old text, from its key under `<ns>.recs`, or, for a rule that no tab
 * worded (`key: 'rule'`), its id and its advice.
 *
 * @param {{key: string, title: ?string, detail: ?string, params: object, advice: ?string}} rec the recommendation
 * @param {string} ns the namespace of the old texts of the tab, such as `seo` or `eco`
 * @param {Function} t the translation function of Page quality (`audit.` prefix)
 * @returns {{title: string, detail: string}} the texts
 */
export function recommendationText(rec, ns, t) {
    if (typeof rec.title === 'string') {
        return {title: rec.title, detail: rec.detail || ''};
    }
    if (rec.key === RULE_KEY) {
        return {title: t('recs.rule', rec.params), detail: rec.advice || ''};
    }
    return {title: t(`${ns}.recs.${rec.key}.title`, rec.params), detail: t(`${ns}.recs.${rec.key}.detail`, rec.params)};
}

/**
 * The id of a recommendation in its tab: the id of its rule, or, in an audit stored by an older version,
 * its key.
 *
 * @param {{key: string, rule: ?string}} rec the recommendation
 * @returns {string} the id
 */
export const recommendationId = rec => (rec.key === RULE_KEY || typeof rec.title === 'string' ? rec.rule || rec.key : rec.key);


/**
 * The findings that the drawer hands to its tabs and to the actions on a finding: those of the
 * given tabs, in the drawer's order. Each has `rule`, the id of the rule it reports: today, its key.
 *
 * @param {object} bySection the findings of each tab, by tab id
 * @param {Array<string>} sectionIds the tabs the page can use, in the drawer's order
 * @returns {Array<object>} findings
 */
export function entryFindings(bySection, sectionIds) {
    return sectionIds.flatMap(id => bySection[id] || []).map(finding => ({...finding, rule: finding.key}));
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
        out.push({
            key: auditFindingKey('a11y', rule.id),
            section: 'audit',
            tab: 'accessibility',
            // The severity that the site gives the rule axe.<id>; axe-core's impact in an older audit
            severity: rule.severity || rule.impact || 'minor',
            rule: rule.rule || null,
            title: rule.title || rule.help || rule.id,
            detail: rule.description,
            count: rule.totalNodes
        });
    });
    AUDIT_TABS.forEach(([tab, ns]) => {
        const recommendations = results[tab]?.recommendations || [];
        (tab === 'vitals' ? withoutTimingFindings(recommendations) : recommendations).forEach(rec => {
            out.push({
                key: auditFindingKey(tab, recommendationId(rec)),
                section: 'audit',
                tab: tab === 'a11y' ? 'accessibility' : tab,
                severity: rec.severity || 'minor',
                rule: rec.rule || null,
                ...recommendationText(rec, ns, t),
                ...(rec.items ? {count: rec.items.length} : {})
            });
        });
    });
    return out;
}

/**
 * The findings of a compliance report: one per rule, however often the page breaks it. The key of a
 * finding is the id of its rule, such as `compliance.click-here`.
 *
 * @param {object} report the page scan (`{issues: [{ruleId, severity, term}]}`)
 * @param {Function} t the section's translation function (`compliance.` prefix)
 * @returns {Array<object>} findings, with the severity of their rule
 */
export function complianceFindings(report, t) {
    const byRule = new Map();
    (report?.issues || []).forEach(issue => {
        const found = byRule.get(issue.ruleId);
        byRule.set(issue.ruleId, {issue: found?.issue || issue, count: (found?.count || 0) + 1});
    });
    return [...byRule].map(([ruleId, {issue, count}]) => ({
        key: ruleId,
        section: 'compliance',
        tab: null,
        severity: issue.severity || 'serious',
        title: t('fixFirst.term', {term: issue.term, count})
    }));
}
