// A cell starting with one of these is read as a formula by spreadsheet applications.
const FORMULA_START = /^[=+\-@\t\r]/;

/** One CSV cell: always quoted, quotes doubled, formula starts neutralised with a leading quote. */
export const csvCell = value => {
    let text = value === null || value === undefined ? '' : String(value);
    if (FORMULA_START.test(text)) {
        text = `'${text}`;
    }
    return `"${text.replaceAll('"', '""')}"`;
};

/** Saves `content` as a file without leaving the page. */
export const download = (content, type, fileName) => {
    const url = URL.createObjectURL(new Blob([content], {type}));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
};

/** A header row and data rows as CSV: UTF-8 with a BOM so spreadsheets read accents, CRLF line ends. */
export const downloadCsv = (columns, rows, fileName) => {
    const lines = [columns.join(','), ...rows.map(row => row.map(csvCell).join(','))];
    download(`﻿${lines.join('\r\n')}\r\n`, 'text/csv;charset=utf-8', fileName);
};

/** Any value as indented JSON, with GraphQL's __typename left out. */
export const downloadJson = (value, fileName) => {
    // eslint-disable-next-line no-unused-vars
    const strip = ({__typename, ...rest}) => rest;
    const clean = JSON.parse(JSON.stringify(value), (key, v) => (v && typeof v === 'object' && !Array.isArray(v) ? strip(v) : v));
    download(`${JSON.stringify(clean, null, 2)}\n`, 'application/json', fileName);
};
