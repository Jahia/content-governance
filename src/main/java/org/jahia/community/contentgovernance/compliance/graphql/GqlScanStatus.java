package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;

/**
 * GraphQL mirror of {@link org.jahia.community.contentgovernance.compliance.model.ScanStatus}, named for the shared schema.
 */
@GraphQLName("ComplianceScanStatus")
@GraphQLDescription("Outcome of a compliance scan")
public enum GqlScanStatus {
    SAFE, ISSUES, NO_GLOSSARY
}
