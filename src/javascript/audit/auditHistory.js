import {gql} from '@apollo/client';

const SUMMARY_FIELDS = `
    counts {
        tab
        count
    }
    total
`;

export const PAGE_AUDITS = gql`
    query ContentGovernancePageAudits($path: String!, $language: String!, $tab: String) {
        contentGovernance {
            pageAudits(path: $path, language: $language, tab: $tab) {
                tab
                auditedAt
                auditedBy
                summary { ${SUMMARY_FIELDS} }
                results
            }
        }
    }
`;

export const SAVE_AUDIT = gql`
    mutation ContentGovernanceSaveAudit($path: String!, $language: String!, $results: String!) {
        contentGovernance {
            saveAudit(path: $path, language: $language, results: $results) {
                auditedAt
                auditedBy
                summary { ${SUMMARY_FIELDS} }
            }
        }
    }
`;

export const SAVE_FINDINGS = gql`
    mutation ContentGovernanceSaveFindings($path: String!, $language: String!, $tab: String!, $findings: String!) {
        contentGovernance {
            saveFindings(path: $path, language: $language, tab: $tab, findings: $findings) {
                tab
                auditedAt
                auditedBy
                summary { ${SUMMARY_FIELDS} }
            }
        }
    }
`;

export const SITE_AUDITS = gql`
    query ContentGovernanceSiteAudits($siteKey: String!, $language: String!) {
        contentGovernance {
            siteAudits(siteKey: $siteKey, language: $language) {
                pageCount
                pages {
                    path
                    title
                    previousTotal
                    lastAudit {
                        auditedAt
                        auditedBy
                        summary { ${SUMMARY_FIELDS} }
                    }
                }
            }
        }
    }
`;

const PAGE_LAST_MODIFIED = gql`
    query pageAuditLastModified($path: String!, $language: String!) {
        jcr {
            nodeByPath(path: $path) {
                workspace
                uuid
                aggregatedLastModifiedDate(
                    language: $language
                    recursionTypesFilter: {multi: NONE, types: ["jnt:page"]}
                )
            }
        }
    }
`;

/**
 * Cheap staleness probe: last modification date of the page and its own
 * content (stops at sub-page boundaries).
 *
 * @returns {Promise<?number>} epoch millis, or null when it cannot be determined
 */
export async function fetchPageLastModified(client, path, language) {
    try {
        const {data} = await client.query({
            query: PAGE_LAST_MODIFIED,
            variables: {path, language},
            fetchPolicy: 'no-cache'
        });
        const date = data?.jcr?.nodeByPath?.aggregatedLastModifiedDate;
        return date ? new Date(date).getTime() : null;
    } catch (e) {
        console.warn('[content-governance] last-modified probe failed', e);
        return null;
    }
}

/**
 * The issues of a stored run by tab id.
 *
 * @param {object} summary the summary of a run (`{counts: [{tab, count}], total}`)
 * @returns {object} the count of each tab the run measured, such as `{seo: 2, links: 0}`
 */
export const countsOf = summary => Object.fromEntries((summary?.counts || []).map(({tab, count}) => [tab, count]));

/**
 * The runs of a tab on a page stored for every editor of the site, newest first (the newest with
 * what it stores). Answers an empty list when they cannot be read: Page quality then works from the
 * editor's own cache, as before shared audits.
 *
 * @param {object} client the Apollo client
 * @param {string} path the page
 * @param {string} language the content language
 * @param {string} [tab] the id of the tab, Page quality (`audit`) when omitted
 * @returns {Promise<Array<object>>} the runs
 */
export function fetchPageAudits(client, path, language, tab) {
    return client.query({query: PAGE_AUDITS, variables: {path, language, tab}, fetchPolicy: 'network-only'})
        .then(({data}) => data?.contentGovernance?.pageAudits || [])
        .catch(error => {
            console.warn('[content-governance] stored audits unavailable', error);
            return [];
        });
}

/**
 * Stores a complete audit for every editor of the site.
 *
 * @returns {Promise<object|null>} the stored run (auditedAt, auditedBy, summary), null when it could not be stored
 */
export function saveAudit(client, path, language, results) {
    return client.mutate({mutation: SAVE_AUDIT, variables: {path, language, results: JSON.stringify(results)}})
        .then(({data}) => data?.contentGovernance?.saveAudit || null)
        .catch(error => {
            console.warn('[content-governance] the audit could not be stored', error);
            return null;
        });
}

/**
 * Stores the findings of a tab as its latest run on the page, for every editor of the site.
 *
 * @param {object} client the Apollo client
 * @param {string} path the page
 * @param {string} language the content language
 * @param {string} tab the id of the tab that found them
 * @param {Array<object>} findings the findings (`{key, rule?, tab?, severity, title, detail?, count?}`)
 * @returns {Promise<object|null>} the stored run (tab, auditedAt, auditedBy, summary), null when it could not be stored
 */
export function saveFindings(client, path, language, tab, findings) {
    return client.mutate({mutation: SAVE_FINDINGS, variables: {path, language, tab, findings: JSON.stringify(findings)}})
        .then(({data}) => data?.contentGovernance?.saveFindings || null)
        .catch(error => {
            console.warn(`[content-governance] the findings of "${tab}" could not be stored`, error);
            return null;
        });
}

/** @returns {object|null} the results of a stored run, null when missing or unreadable */
export function storedResults(run) {
    if (!run || !run.results) {
        return null;
    }
    try {
        return JSON.parse(run.results);
    } catch {
        return null;
    }
}
