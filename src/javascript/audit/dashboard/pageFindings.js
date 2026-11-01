import {PAGE_AUDITS, storedResults} from '../auditHistory';
import {auditFindings, rankFindings} from '../../drawer/findings';

/** The findings of a page in the Page quality dashboard, read when its row is opened. */

export const COLUMN_KEYS = ['page', 'audited', 'tabs', 'total', 'actions'];

/** Every column key points at the row: each cell renders from the row it belongs to, whatever its kind. */
export const keyed = row => ({...row, ...Object.fromEntries(COLUMN_KEYS.map(key => [key, row]))});

/**
 * The sub-rows of a page: its findings once read, or one row saying they are being read, could not
 * be read, or were not stored.
 */
export function subRowsOf(item, details) {
    const detail = details[item.path];
    if (detail?.findings?.length > 0) {
        return detail.findings.map(finding => keyed({id: `${item.path}#${finding.key}`, kind: 'finding', item, finding}));
    }
    const kind = detail?.findings ? 'missing' : detail?.kind || 'loading';
    return [keyed({id: `${item.path}#${kind}`, kind, item})];
}

/**
 * The findings of the newest stored audit of a page, the most serious first.
 *
 * @returns {Promise<Array<object>>} the findings, empty when that audit's results were not stored
 */
export function readFindings(client, path, language, t) {
    return client.query({query: PAGE_AUDITS, variables: {path, language}, fetchPolicy: 'network-only'})
        .then(({data}) => {
            const results = storedResults(data?.contentGovernance?.pageAudits?.[0]);
            return rankFindings(auditFindings(results, t), Number.POSITIVE_INFINITY);
        });
}
