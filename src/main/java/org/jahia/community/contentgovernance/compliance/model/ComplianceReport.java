package org.jahia.community.contentgovernance.compliance.model;

import java.util.List;

/**
 * Result of the scan of one page in one language.
 *
 * @param pagePath        path of the scanned page
 * @param language        content language that was scanned
 * @param status          outcome of the scan
 * @param scannedContents number of nodes whose properties were read
 * @param truncated       {@code true} when a limit stopped the scan before every content or issue was read
 * @param glossaries      folders of rules applied, in path order
 * @param issues          every place that breaks a rule, in reading order
 */
public record ComplianceReport(String pagePath, String language, ScanStatus status, int scannedContents,
                               boolean truncated, List<Glossary> glossaries, List<ComplianceIssue> issues) {
}
