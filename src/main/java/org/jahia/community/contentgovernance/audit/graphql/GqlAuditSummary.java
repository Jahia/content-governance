package org.jahia.community.contentgovernance.audit.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import java.util.List;
import org.jahia.community.contentgovernance.audit.model.AuditSummary;

/**
 * GraphQL view of an {@link AuditSummary}: the issues per tab id, for the tabs the run measured.
 */
@GraphQLName("ContentGovernanceAuditSummary")
@GraphQLDescription("Issues per tab id of a stored run, for the tabs the run measured")
public class GqlAuditSummary {

    private final AuditSummary summary;

    /** @param summary the summary to expose */
    public GqlAuditSummary(AuditSummary summary) {
        this.summary = summary;
    }

    /** @return the issues of each tab the run measured */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Issues per tab id, for the tabs the run measured: the tabs of Page quality in the order of the "
            + "drawer, or the id of the tab that stored the run")
    public List<GqlTabCount> getCounts() {
        return summary.counts().entrySet().stream().map(e -> new GqlTabCount(e.getKey(), e.getValue())).toList();
    }

    /** @return critical and serious accessibility violations, or null when unknown */
    @GraphQLField
    @GraphQLDescription("Critical and serious accessibility violations; null for a run of another tab, or an audit "
            + "stored before they were counted")
    public Integer getSeriousAccessibility() {
        return summary.seriousAccessibility() < 0 ? null : summary.seriousAccessibility();
    }

    /** @return the sum of the counts */
    @GraphQLField
    @GraphQLNonNull
    public int getTotal() {
        return summary.total();
    }

    /** The issues of one tab. */
    @GraphQLName("ContentGovernanceTabCount")
    @GraphQLDescription("The issues of one tab of a stored run")
    public static class GqlTabCount {
        private final String tab;
        private final int count;

        GqlTabCount(String tab, int count) {
            this.tab = tab;
            this.count = count;
        }

        /** @return the tab id */
        @GraphQLField
        @GraphQLNonNull
        @GraphQLDescription("Id of the tab: a tab of Page quality (accessibility, seo...) or a tab of the drawer")
        public String getTab() {
            return tab;
        }

        /** @return the issues the tab found */
        @GraphQLField
        @GraphQLNonNull
        public int getCount() {
            return count;
        }
    }
}
