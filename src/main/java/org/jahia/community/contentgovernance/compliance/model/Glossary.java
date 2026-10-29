package org.jahia.community.contentgovernance.compliance.model;

/**
 * A glossary applied by a scan.
 *
 * @param path      path of the {@code ccs:glossary} node
 * @param uuid      identifier of the node, used by the dialog to open it in jContent
 * @param title     display name of the glossary
 * @param termCount number of terms of the glossary that the scan applied
 */
public record Glossary(String path, String uuid, String title, int termCount) {
}
