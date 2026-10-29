const CSV_COLUMNS = [
    'locationKind', 'locationPath', 'locationName', 'contentPath', 'contentName', 'contentType',
    'field', 'term', 'matchedText', 'excerpt', 'reason', 'alternative', 'glossary', 'glossaryPath'
];

// A cell starting with one of these is read as a formula by spreadsheet applications.
const FORMULA_START = /^[=+\-@\t\r]/;

const csvCell = value => {
    let text = value === null || value === undefined ? '' : String(value);
    if (FORMULA_START.test(text)) {
        text = `'${text}`;
    }
    return `"${text.replaceAll('"', '""')}"`;
};

const download = (content, type, fileName) => {
    const url = URL.createObjectURL(new Blob([content], {type}));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
};

const baseName = (report, scannedAt) => `compliance-${report.siteKey}-${report.language}-${scannedAt.slice(0, 10)}`;

/** One row per issue, with the location it belongs to. UTF-8 with a BOM so spreadsheets read accents. */
export const exportCsv = (report, scannedAt) => {
    const lines = [CSV_COLUMNS.join(',')];
    const titles = new Map(report.glossaries.map(g => [g.path, g.title]));
    report.locations.forEach(location => location.issues.forEach(issue => {
        lines.push([
            location.kind, location.path, location.name, issue.nodePath, issue.nodeName, issue.nodeType,
            issue.propertyLabel || issue.propertyName, issue.term, issue.matchedText,
            `${issue.before || ''}${issue.matchedText}${issue.after || ''}`, issue.reason, issue.alternative, titles.get(issue.glossaryPath), issue.glossaryPath
        ].map(csvCell).join(','));
    }));
    download(`﻿${lines.join('\r\n')}\r\n`, 'text/csv;charset=utf-8', `${baseName(report, scannedAt)}.csv`);
};

/** The whole report, as the server returned it, with the time of the scan. */
export const exportJson = (report, scannedAt) => {
    // eslint-disable-next-line no-unused-vars
    const strip = ({__typename, ...rest}) => rest;
    const clean = JSON.parse(JSON.stringify(report), (key, value) => (value && typeof value === 'object' && !Array.isArray(value) ? strip(value) : value));
    download(`${JSON.stringify({scannedAt, ...clean}, null, 2)}\n`, 'application/json', `${baseName(report, scannedAt)}.json`);
};
