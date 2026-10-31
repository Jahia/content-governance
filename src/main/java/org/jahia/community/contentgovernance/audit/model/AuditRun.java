package org.jahia.community.contentgovernance.audit.model;

/**
 * One stored Page quality audit of a page in one language.
 *
 * @param auditedAt when the audit ran, in milliseconds since the epoch
 * @param auditedBy username of the editor who ran it
 * @param summary   issues per tab
 * @param results   the full results as JSON, or null when only the summary was read
 */
public record AuditRun(long auditedAt, String auditedBy, AuditSummary summary, String results) {
}
