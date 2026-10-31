package org.jahia.community.contentgovernance.audit.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import java.util.List;

/**
 * The Page quality audits of a site in one language, for the site dashboard.
 */
@GraphQLName("ContentGovernanceSiteAudits")
@GraphQLDescription("The latest Page quality audit of every audited page of a site in one language")
public class GqlSiteAudits {

    private final List<GqlAuditedPage> pages;
    private final int pageCount;

    /**
     * @param pages     the audited pages the caller may read
     * @param pageCount the pages of the site the caller may read, audited or not
     */
    public GqlSiteAudits(List<GqlAuditedPage> pages, int pageCount) {
        this.pages = pages;
        this.pageCount = pageCount;
    }

    /** @return the audited pages the caller may read, most issues first */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The audited pages the caller may read, most issues first")
    public List<GqlAuditedPage> getPages() {
        return pages;
    }

    /** @return pages of the site the caller may read, audited or not */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Pages of the site the caller may read, audited or not")
    public int getPageCount() {
        return pageCount;
    }
}
