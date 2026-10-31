import {gql} from '@apollo/client';

const SUMMARY_FIELDS = `
    accessibility
    seo
    vitals
    readability
    ecodesign
    links
    jahia
    total
`;

export const PAGE_AUDITS = gql`
    query ContentGovernancePageAudits($path: String!, $language: String!) {
        contentGovernance {
            pageAudits(path: $path, language: $language) {
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

/**
 * The audits of a page stored for every editor of the site, newest first (the newest with its
 * results). Answers an empty list when they cannot be read: the drawer then works from the editor's
 * own cache, as before shared audits.
 */
export function fetchPageAudits(client, path, language) {
    return client.query({query: PAGE_AUDITS, variables: {path, language}, fetchPolicy: 'network-only'})
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
