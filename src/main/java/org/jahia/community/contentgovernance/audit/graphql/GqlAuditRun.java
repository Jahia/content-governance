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
@GraphQLDescription("One stored run of a tab of the drawer: a Page quality audit, or the findings of another tab")
public class GqlAuditRun {

    private final AuditRun run;

    /** @param run the audit to expose */
    public GqlAuditRun(AuditRun run) {
        this.run = run;
    }

    /** @return the id of the tab that stored the run */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Id of the tab that stored the run: audit for Page quality")
    public String getTab() {
        return run.tab();
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

    /** @return what the run stores as JSON, on the latest run of a tab only */
    @GraphQLField
    @GraphQLDescription("What the run stores as JSON, on the latest run of a tab only: the Page quality results, "
            + "or {findings: [...]} for another tab")
    public String getResults() {
        return run.results();
    }
}
