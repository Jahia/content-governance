package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.ai.LlmConfigService;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;

import javax.inject.Inject;

/**
 * Settings queries. Read-only: nothing is written, so the CSRF guard does not apply.
 */
@GraphQLName("ContentGovernanceQuery")
@GraphQLDescription("Content Governance queries")
public class GqlContentGovernance {

    @Inject
    @GraphQLOsgiService
    private LlmConfigService llm;

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
}
