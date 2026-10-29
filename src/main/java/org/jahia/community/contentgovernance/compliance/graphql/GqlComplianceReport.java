package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.ComplianceReport;

import java.util.List;

/**
 * GraphQL view of a {@link ComplianceReport}.
 */
@GraphQLName("ComplianceReport")
@GraphQLDescription("Result of the compliance scan of one page in one language")
public class GqlComplianceReport {

    private final ComplianceReport report;

    /** @param report the report to expose */
    public GqlComplianceReport(ComplianceReport report) {
        this.report = report;
    }

    /** @return path of the scanned page */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Path of the scanned page")
    public String getPagePath() {
        return report.pagePath();
    }

    /** @return the scanned content language */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Content language scanned")
    public String getLanguage() {
        return report.language();
    }

    /** @return outcome of the scan */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("SAFE, ISSUES, or NO_GLOSSARY when no glossary with terms exists for the language")
    public GqlScanStatus getStatus() {
        return GqlScanStatus.valueOf(report.status().name());
    }

    /** @return number of contents read */
    @GraphQLField
    @GraphQLDescription("Number of contents read")
    public int getScannedContents() {
        return report.scannedContents();
    }

    /** @return whether a limit stopped the scan */
    @GraphQLField
    @GraphQLDescription("True when a limit stopped the scan before every content or issue was read")
    public boolean isTruncated() {
        return report.truncated();
    }

    /** @return glossaries applied */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Glossaries applied")
    public List<GqlGlossary> getGlossaries() {
        return report.glossaries().stream().map(GqlGlossary::new).toList();
    }

    /** @return the issues found */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Every use of a non-authorised term")
    public List<GqlComplianceIssue> getIssues() {
        return report.issues().stream().map(GqlComplianceIssue::new).toList();
    }
}
