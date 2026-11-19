package org.jahia.community.contentgovernance.audit.model;

/**
 * One stored run of a tab of the drawer on a page in one language: a Page quality audit, or the
 * findings that another tab stored.
 *
 * @param tab       the id of the tab that stored the run ({@code audit} for Page quality)
 * @param auditedAt when the run happened, in milliseconds since the epoch
 * @param auditedBy username of the editor who ran it
 * @param summary   issues per tab id
 * @param results   what the run stores as JSON (the Page quality results, or {@code {"findings": [...]}}),
 *                  or null when only the summary was read
 */
public record AuditRun(String tab, long auditedAt, String auditedBy, AuditSummary summary, String results) {
}
