package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.ai.LlmConfigService;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Settings mutations. Authorised here, on the server: the settings route of the back office only
 * hides itself from users without {@value SiteFeatures#SETTINGS_PERMISSION}.
 */
@GraphQLName("ContentGovernanceMutation")
@GraphQLDescription("Content Governance mutations")
public class GqlContentGovernanceMutation {

    private static final Logger logger = LoggerFactory.getLogger(GqlContentGovernanceMutation.class);

    @Inject
    @GraphQLOsgiService
    private LlmConfigService llm;

    /**
     * Switches sections and tabs off on a site; every id not listed is switched on.
     *
     * @param siteKey  key of the site
     * @param disabled section ids ({@code geo}) and tab ids ({@code geo.citations}) to switch off;
     *                 unknown ids are dropped
     * @return the settings as stored
     */
    @GraphQLField
    @GraphQLName("saveSiteSettings")
    @GraphQLNonNull
    @GraphQLDescription("Stores which sections and tabs of the module a site switches off")
    public GqlSiteSettings saveSiteSettings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("disabled") @GraphQLNonNull @GraphQLDescription("Section and tab ids switched off") List<String> disabled) {
        JCRSiteNode site = SiteAccess.site(siteKey);
        if (!site.hasPermission(SiteFeatures.SETTINGS_PERMISSION) || !SiteFeatures.moduleEnabled(site)) {
            throw new DataFetchingException(SiteAccess.DENIED);
        }
        try {
            SiteFeatures.save(site.getPath(), disabled);
            return GqlSiteSettings.of(site, llm);
        } catch (RepositoryException e) {
            logger.warn("could not save the content governance settings of {}: {}", siteKey, e.toString());
            throw new DataFetchingException("The settings could not be saved");
        }
    }
}
