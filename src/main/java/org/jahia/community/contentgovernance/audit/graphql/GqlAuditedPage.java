package org.jahia.community.contentgovernance.audit.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.audit.model.AuditedPage;

/**
 * GraphQL view of an {@link AuditedPage}, with the page's current path and title.
 */
@GraphQLName("ContentGovernanceAuditedPage")
@GraphQLDescription("A page of the site with its latest Page quality audit")
public class GqlAuditedPage {

    private final AuditedPage page;
    private final String path;
    private final String title;

    /**
     * @param page  the stored audits of the page
     * @param path  the page's path now
     * @param title the page's title now, in the audited language
     */
    public GqlAuditedPage(AuditedPage page, String path, String title) {
        this.page = page;
        this.path = path;
        this.title = title;
    }

    /** @return path of the page now */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Path of the page now")
    public String getPath() {
        return path;
    }

    /** @return title of the page now */
    @GraphQLField
    @GraphQLDescription("Title of the page now, in the audited language")
    public String getTitle() {
        return title;
    }

    /** @return the latest audit, without its results */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The latest audit, without its results")
    public GqlAuditRun getLastAudit() {
        return new GqlAuditRun(page.lastRun());
    }

    /** @return issues of the audit before the latest, or null when there is none */
    @GraphQLField
    @GraphQLDescription("Issues of the audit before the latest, or null when there is none")
    public Integer getPreviousTotal() {
        return page.previousTotal() < 0 ? null : page.previousTotal();
    }
}
