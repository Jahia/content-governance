package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.community.contentgovernance.ai.LlmConfigService;
import org.jahia.community.contentgovernance.audit.graphql.AuditFields;
import org.jahia.community.contentgovernance.audit.graphql.GqlAuditRun;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
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

    @Inject
    @GraphQLOsgiService
    private AuditHistoryService audits;

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

    /**
     * Stores a Page quality audit the caller ran, as the latest of the page; the page keeps its last
     * {@link AuditHistoryService#MAX_RUNS}.
     *
     * @param path        path of the page or main resource
     * @param language    content language of the audit
     * @param results     the audit results as JSON
     * @param environment the request
     * @return the stored audit, with the issue counts computed on the server
     */
    @GraphQLField
    @GraphQLName("saveAudit")
    @GraphQLNonNull
    @GraphQLDescription("Stores a Page quality audit as the latest of its page, for every editor of the site")
    public GqlAuditRun saveAudit(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            @GraphQLName("results") @GraphQLNonNull @GraphQLDescription("The audit results as JSON") String results,
            DataFetchingEnvironment environment) {
        return AuditFields.save(audits, path, language, results, environment);
    }
}
