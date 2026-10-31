package org.jahia.community.contentgovernance.audit.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import java.time.Instant;
import org.jahia.community.contentgovernance.audit.model.AuditRun;

/**
 * GraphQL view of an {@link AuditRun}.
 */
@GraphQLName("ContentGovernanceAuditRun")
@GraphQLDescription("One stored Page quality audit")
public class GqlAuditRun {

    private final AuditRun run;

    /** @param run the audit to expose */
    public GqlAuditRun(AuditRun run) {
        this.run = run;
    }

    /** @return when the audit ran, ISO-8601 */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("When the audit ran, ISO-8601")
    public String getAuditedAt() {
        return Instant.ofEpochMilli(run.auditedAt()).toString();
    }

    /** @return username of the editor who ran the audit */
    @GraphQLField
    @GraphQLDescription("Username of the editor who ran the audit")
    public String getAuditedBy() {
        return run.auditedBy();
    }

    /** @return issues per tab */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Issues per tab")
    public GqlAuditSummary getSummary() {
        return new GqlAuditSummary(run.summary());
    }

    /** @return the full results as JSON, on the latest audit of a page only */
    @GraphQLField
    @GraphQLDescription("The full results as JSON: on the latest audit of a page only")
    public String getResults() {
        return run.results();
    }
}
