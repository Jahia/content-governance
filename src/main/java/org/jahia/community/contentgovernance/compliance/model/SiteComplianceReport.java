package org.jahia.community.contentgovernance.compliance.model;

import java.util.List;

/**
 * Result of the scan of a whole site in one language.
 *
 * @param siteKey         key of the site
 * @param language        content language scanned
 * @param status          outcome of the scan
 * @param scannedPages    number of pages read
 * @param scannedContents number of nodes whose properties were read
 * @param skippedLocations number of pages and content items that could not be read and are not in the report
 * @param truncated       {@code true} when a limit stopped the scan before the whole site was read
 * @param glossaries      glossaries applied, in path order
 * @param locations       the pages and content items with at least one issue, pages first, in path order
 * @param terms           the terms found, most used first
 */
public record SiteComplianceReport(String siteKey, String language, ScanStatus status, int scannedPages,
                                   int scannedContents, int skippedLocations, boolean truncated, List<Glossary> glossaries,
                                   List<LocationReport> locations, List<TermUsage> terms) {
}
