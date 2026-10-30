import {downloadCsv, downloadJson} from '../../common/exportFile';

const CSV_COLUMNS = ['area', 'finding', 'findingLabel', 'severity', 'title', 'jcrPath', 'url', 'detail'];
const SITEMAP_GROUPS = ['redirects', 'missing', 'unknown', 'staleDate', 'noindexListed'];
const LINK_GROUPS = ['orphans', 'weak'];
const VANITY_GROUPS = ['unresolvable', 'duplicates', 'canonical'];

const baseName = (siteKey, scan) => `geo-readiness-${siteKey}-${scan.language}-${producedAt(scan).slice(0, 10)}`;

/** When the scan finished, else when it started, else now: the export always has a date. */
const producedAt = scan => scan.run?.finishedAt || scan.run?.startedAt || new Date().toISOString();

const scoreRows = (scan, t) => {
    const severities = scan.run?.aggregate?.severities || {};
    return (scan.run?.failures || []).flatMap(page => (page.failed || []).map(check => [
        'score', check, t(`score.check.${check}.label`), severities[check], page.title, page.path, '', page.template
    ]));
};

const groupRows = (area, section, groups, labelKey, row) => groups.flatMap(key =>
    (section?.[key] || []).map(r => [area, key, labelKey(key), '', ...row(r)]));

const llmsRows = (scan, t) => (scan.llms?.stale || []).map(r => [
    'llms', r.why, t('files.llms.stale.title'), '', r.title, '', r.path, t(`files.llms.stale.why.${r.why}`)
]);

const freshnessRows = (scan, t) => (scan.freshness?.items || []).filter(r => r.stale).map(r => [
    'freshness', 'stale', t('export.freshnessStale', {days: scan.freshness.staleDays}), '', r.title, r.jcrPath, r.path, r.modifiedOn
]);

/**
 * One row per finding of the stored scan, in the order of the dashboard's tabs. Codes in `area`
 * and `finding` stay the same in every language; `findingLabel` and `detail` are in the UI language.
 */
export const scanRows = (scan, t) => [
    ...scoreRows(scan, t),
    ...groupRows('sitemap', scan.sitemap, SITEMAP_GROUPS, key => t(`sitemap.kind.${key}`),
        r => [r.title, r.jcrPath, r.path || r.loc, r.detail]),
    ...groupRows('links', scan.links, LINK_GROUPS, key => t(`links.kind.${key}`),
        r => [r.title, r.jcrPath, r.path, r.why ? t(`links.why.${r.why}`) : '']),
    ...groupRows('addresses', scan.vanity, VANITY_GROUPS, key => t(`vanity.kind.${key}`),
        r => [r.title, r.jcrPath, r.url, [r.why ? t(`vanity.tag.${r.why}`) : r.language, r.detail].filter(Boolean).join(' · ')]),
    ...llmsRows(scan, t),
    ...freshnessRows(scan, t)
];

export const exportCsv = (siteKey, scan, t) => downloadCsv(CSV_COLUMNS, scanRows(scan, t), `${baseName(siteKey, scan)}.csv`);

/** The stored scan as the server returns it, with the site and the time it was produced. */
export const exportJson = (siteKey, scan) => downloadJson({siteKey, producedAt: producedAt(scan), ...scan}, `${baseName(siteKey, scan)}.json`);
