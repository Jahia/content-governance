package org.jahia.community.contentgovernance.audit.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.audit.model.AuditSummary;

/**
 * GraphQL view of an {@link AuditSummary}: a tab the audit did not measure answers null.
 */
@GraphQLName("ContentGovernanceAuditSummary")
@GraphQLDescription("Issues per tab of a Page quality audit; null for a tab the audit did not measure")
public class GqlAuditSummary {

    private final AuditSummary summary;

    /** @param summary the summary to expose */
    public GqlAuditSummary(AuditSummary summary) {
        this.summary = summary;
    }

    /** @return accessibility violations */
    @GraphQLField
    public Integer getAccessibility() {
        return summary.counts().get("accessibility");
    }

    /** @return SEO recommendations */
    @GraphQLField
    public Integer getSeo() {
        return summary.counts().get("seo");
    }

    /** @return Web Vitals recommendations */
    @GraphQLField
    public Integer getVitals() {
        return summary.counts().get("vitals");
    }

    /** @return readability recommendations */
    @GraphQLField
    public Integer getReadability() {
        return summary.counts().get("readability");
    }

    /** @return eco-design recommendations */
    @GraphQLField
    public Integer getEcodesign() {
        return summary.counts().get("ecodesign");
    }

    /** @return links recommendations */
    @GraphQLField
    public Integer getLinks() {
        return summary.counts().get("links");
    }

    /** @return Jahia recommendations */
    @GraphQLField
    public Integer getJahia() {
        return summary.counts().get("jahia");
    }

    /** @return critical and serious accessibility violations, or null when unknown */
    @GraphQLField
    @GraphQLDescription("Critical and serious accessibility violations; null for an audit stored before they were counted")
    public Integer getSeriousAccessibility() {
        return summary.seriousAccessibility() < 0 ? null : summary.seriousAccessibility();
    }

        /** @return the sum of the counts */
    @GraphQLField
    @GraphQLNonNull
    public int getTotal() {
        return summary.total();
    }
}
