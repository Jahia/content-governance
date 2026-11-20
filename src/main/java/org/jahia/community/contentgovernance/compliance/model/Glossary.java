package org.jahia.community.contentgovernance.compliance.model;

/**
 * A folder of rules that a scan applied, such as the terms of the legal team.
 *
 * @param path      path of the folder
 * @param uuid      identifier of the folder, used to open it in jContent, or {@code null} when the user
 *                  cannot read it
 * @param title     display name of the folder
 * @param termCount number of rules of the folder that the scan applied
 */
public record Glossary(String path, String uuid, String title, int termCount) {
}
