package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.community.contentgovernance.ai.LlmConfigService;
import org.jahia.community.contentgovernance.audit.graphql.AuditFields;
import org.jahia.community.contentgovernance.audit.graphql.GqlAuditRun;
import org.jahia.community.contentgovernance.audit.graphql.GqlSiteAudits;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;

import javax.inject.Inject;
import java.util.List;

/**
 * Settings and Page quality audit queries. Read-only: nothing is written, so the CSRF guard does not apply.
 */
@GraphQLName("ContentGovernanceQuery")
@GraphQLDescription("Content Governance queries")
public class GqlContentGovernance {

    @Inject
    @GraphQLOsgiService
    private LlmConfigService llm;

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private AuditHistoryService audits;

    /**
     * What a site uses of the module, as the drawer and the settings screen read it. Any signed-in
     * user who can read the site may ask: the drawer of every editor needs it.
     *
     * @param siteKey key of the site
     * @return the settings
     */
    @GraphQLField
    @GraphQLName("siteSettings")
    @GraphQLNonNull
    @GraphQLDescription("Sections and tabs of the module on a site, and whether AI is configured")
    public GqlSiteSettings siteSettings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        return GqlSiteSettings.of(SiteAccess.site(siteKey), llm);
    }

    /**
     * The stored Page quality audits of a page, for anyone who may open jContent on its site and read it.
     *
     * @param path        path of the page or main resource
     * @param language    content language
     * @param environment the request
     * @return the audits, newest first; the newest carries its full results
     */
    @GraphQLField
    @GraphQLName("pageAudits")
    @GraphQLNonNull
    @GraphQLDescription("The last Page quality audits of a page in a language, newest first; the newest carries its full results")
    public List<GqlAuditRun> pageAudits(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            DataFetchingEnvironment environment) {
        return AuditFields.history(audits, path, language, environment);
    }

    /**
     * The latest Page quality audit of every audited page of a site, for the site dashboard.
     *
     * @param siteKey     key of the site
     * @param language    content language
     * @param environment the request
     * @return the audited pages the caller may read, most issues first
     */
    @GraphQLField
    @GraphQLName("siteAudits")
    @GraphQLNonNull
    @GraphQLDescription("The latest Page quality audit of every audited page of a site in a language")
    public GqlSiteAudits siteAudits(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            DataFetchingEnvironment environment) {
        return AuditFields.site(audits, siteKey, language, environment);
    }
}
