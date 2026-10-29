package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.LocationReport;

import java.util.List;

/**
 * GraphQL view of a {@link LocationReport}.
 */
@GraphQLName("ComplianceLocation")
@GraphQLDescription("A page or content item of a site with at least one issue")
public class GqlComplianceLocation {

    private final LocationReport location;

    /** @param location the location to expose */
    public GqlComplianceLocation(LocationReport location) {
        this.location = location;
    }

    /** @return path of the page or content item */
    @GraphQLField
    @GraphQLNonNull
    public String getPath() {
        return location.path();
    }

    /** @return identifier of the node */
    @GraphQLField
    @GraphQLNonNull
    public String getUuid() {
        return location.uuid();
    }

    /** @return display name of the node */
    @GraphQLField
    public String getName() {
        return location.name();
    }

    /** @return page or content item */
    @GraphQLField
    @GraphQLNonNull
    public GqlLocationKind getKind() {
        return GqlLocationKind.valueOf(location.kind().name());
    }

    /** @return label of the node type */
    @GraphQLField
    public String getTypeLabel() {
        return location.typeLabel();
    }

    /** @return number of issues */
    @GraphQLField
    public int getIssueCount() {
        return location.issues().size();
    }

    /** @return the issues */
    @GraphQLField
    @GraphQLNonNull
    public List<GqlComplianceIssue> getIssues() {
        return location.issues().stream().map(GqlComplianceIssue::new).toList();
    }
}
