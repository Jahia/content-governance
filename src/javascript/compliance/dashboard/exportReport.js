import {downloadCsv, downloadJson} from '../../common/exportFile';

const CSV_COLUMNS = [
    'locationKind', 'locationPath', 'locationName', 'contentPath', 'contentName', 'contentType',
    'field', 'term', 'matchedText', 'excerpt', 'reason', 'alternative', 'glossary', 'glossaryPath'
];

const baseName = (report, scannedAt) => `compliance-${report.siteKey}-${report.language}-${scannedAt.slice(0, 10)}`;

/** One row per issue, with the location it belongs to. */
export const exportCsv = (report, scannedAt) => {
    const titles = new Map(report.glossaries.map(g => [g.path, g.title]));
    const rows = report.locations.flatMap(location => location.issues.map(issue => [
        location.kind, location.path, location.name, issue.nodePath, issue.nodeName, issue.nodeType,
        issue.propertyLabel || issue.propertyName, issue.term, issue.matchedText,
        `${issue.before || ''}${issue.matchedText}${issue.after || ''}`, issue.reason, issue.alternative, titles.get(issue.glossaryPath), issue.glossaryPath
    ]));
    downloadCsv(CSV_COLUMNS, rows, `${baseName(report, scannedAt)}.csv`);
};

/** The whole report, as the server returned it, with the time of the scan. */
export const exportJson = (report, scannedAt) => downloadJson({scannedAt, ...report}, `${baseName(report, scannedAt)}.json`);
