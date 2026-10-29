package org.jahia.community.contentgovernance.compliance.model;

/**
 * Where the content holding an issue comes from, relative to the scanned page.
 */
public enum IssueOrigin {
    /** The page itself or a content stored under it (in a site report: under the page or content item). */
    PAGE,
    /** A content that a content reference of the page points to. Published on its own. */
    REFERENCE,
    /** A content stored elsewhere that the page shows (header, footer, listing). Published on its own. */
    SHARED
}
