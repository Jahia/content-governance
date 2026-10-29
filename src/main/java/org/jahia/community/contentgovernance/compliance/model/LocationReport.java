package org.jahia.community.contentgovernance.compliance.model;

import java.util.List;

/**
 * The issues of one location of a site: a page or a content item of the content folders.
 *
 * @param path      path of the page or content item
 * @param uuid      identifier of the node
 * @param name      display name of the node
 * @param kind      page or content item
 * @param typeLabel label of the node's type, in the UI language
 * @param issues    the issues found in the location, in reading order; never empty in a report
 */
public record LocationReport(String path, String uuid, String name, LocationKind kind, String typeLabel,
                             List<ComplianceIssue> issues) {
}
