package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.SiteComplianceReport;

import java.util.List;

/**
 * GraphQL view of a {@link SiteComplianceReport}.
 */
@GraphQLName("SiteComplianceReport")
@GraphQLDescription("Result of the compliance scan of a whole site in one language")
public class GqlSiteComplianceReport {

    private final SiteComplianceReport report;

    /** @param report the report to expose */
    public GqlSiteComplianceReport(SiteComplianceReport report) {
        this.report = report;
    }

    /** @return key of the site */
    @GraphQLField
    @GraphQLNonNull
    public String getSiteKey() {
        return report.siteKey();
    }

    /** @return content language scanned */
    @GraphQLField
    @GraphQLNonNull
    public String getLanguage() {
        return report.language();
    }

    /** @return outcome of the scan */
    @GraphQLField
    @GraphQLNonNull
    public GqlScanStatus getStatus() {
        return GqlScanStatus.valueOf(report.status().name());
    }

    /** @return number of pages read */
    @GraphQLField
    public int getScannedPages() {
        return report.scannedPages();
    }

    /** @return number of contents read */
    @GraphQLField
    public int getScannedContents() {
        return report.scannedContents();
    }

    /** @return number of locations that could not be read */
    @GraphQLField
    @GraphQLDescription("Pages and content items that could not be read (missing type, provider error) and are not in the report")
    public int getSkippedLocations() {
        return report.skippedLocations();
    }

    /** @return whether a limit stopped the scan */
    @GraphQLField
    @GraphQLDescription("True when a limit stopped the scan before the whole site was read")
    public boolean isTruncated() {
        return report.truncated();
    }

    /** @return glossaries applied */
    @GraphQLField
    @GraphQLNonNull
    public List<GqlGlossary> getGlossaries() {
        return report.glossaries().stream().map(GqlGlossary::new).toList();
    }

    /** @return pages and content items with issues */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Pages and content items with at least one issue, pages first")
    public List<GqlComplianceLocation> getLocations() {
        return report.locations().stream().map(GqlComplianceLocation::new).toList();
    }

    /** @return terms found, most used first */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Terms found, most used first")
    public List<GqlTermUsage> getTerms() {
        return report.terms().stream().map(GqlTermUsage::new).toList();
    }
}
