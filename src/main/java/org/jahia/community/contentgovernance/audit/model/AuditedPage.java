package org.jahia.community.contentgovernance.audit.model;

/**
 * A page of a site with at least one stored audit in a language, as the site dashboard lists it.
 *
 * @param pageId        identifier of the page (its stored audits follow it when it moves)
 * @param path          path of the page when it was last audited
 * @param title         title of the page when it was last audited
 * @param lastRun       the latest audit, summary only
 * @param previousTotal issues of the audit before it, or -1 when there is none
 */
public record AuditedPage(String pageId, String path, String title, AuditRun lastRun, int previousTotal) {
}
