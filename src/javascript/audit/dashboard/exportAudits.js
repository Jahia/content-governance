import {downloadCsv, downloadJson} from '../../common/exportFile';

export const TAB_COLUMNS = ['accessibility', 'seo', 'vitals', 'readability', 'ecodesign', 'links', 'jahia'];

const CSV_COLUMNS = ['path', 'title', 'auditedAt', 'auditedBy', ...TAB_COLUMNS, 'total', 'previousTotal'];

const baseName = (siteKey, language, exportedAt) => `page-quality-${siteKey}-${language}-${exportedAt.slice(0, 10)}`;

/** One row per audited page, with the issues of its latest audit per tab. */
export const exportCsv = (report, siteKey, language) => {
    const exportedAt = new Date().toISOString();
    const rows = report.pages.map(page => [
        page.path, page.title, page.lastAudit.auditedAt, page.lastAudit.auditedBy,
        ...TAB_COLUMNS.map(tab => page.lastAudit.summary[tab]), page.lastAudit.summary.total, page.previousTotal
    ]);
    downloadCsv(CSV_COLUMNS, rows, `${baseName(siteKey, language, exportedAt)}.csv`);
};

/** The report as the server returned it, with the site, the language and the time of the export. */
export const exportJson = (report, siteKey, language) => {
    const exportedAt = new Date().toISOString();
    downloadJson({siteKey, language, exportedAt, ...report}, `${baseName(siteKey, language, exportedAt)}.json`);
};
