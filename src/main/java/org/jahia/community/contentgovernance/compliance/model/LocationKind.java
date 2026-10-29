package org.jahia.community.contentgovernance.compliance.model;

/**
 * What a location of a site report is.
 */
public enum LocationKind {
    /** A page, with the content stored under it (sub-pages excluded). */
    PAGE,
    /** A content item of the site's content folders (an article, a news item), with its sub-content. */
    CONTENT
}
