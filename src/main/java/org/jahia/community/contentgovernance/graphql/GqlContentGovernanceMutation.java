package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
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

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private AuditHistoryService audits;

    /**
     * Switches tabs of the drawer, and parts of tabs, off on a site; every id not listed is switched on.
     *
     * @param siteKey  key of the site
     * @param disabled tab ids ({@code geo}) and part ids ({@code geo.citations}) to switch off, of any
     *                 tab registered in the drawer; an id of another form is dropped
     * @return the settings as stored
     */
    @GraphQLField
    @GraphQLName("saveSiteSettings")
    @GraphQLNonNull
    @GraphQLDescription("Stores which tabs of the drawer, and parts of tabs, a site switches off")
    public GqlSiteSettings saveSiteSettings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("disabled") @GraphQLNonNull @GraphQLDescription("Ids of the tabs (geo) and parts of tabs (geo.citations) switched off") List<String> disabled) {
        JCRSiteNode site = SiteAccess.site(siteKey);
        if (!site.hasPermission(SiteFeatures.SETTINGS_PERMISSION) || !SiteFeatures.moduleEnabled(site)) {
            throw new DataFetchingException(SiteAccess.DENIED);
        }
        try {
            SiteFeatures.save(site.getPath(), disabled);
            return GqlSiteSettings.of(site);
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

    /**
     * Stores the findings of a tab of the drawer on a page, as the latest run of that tab.
     *
     * @param path        path of the page or main resource
     * @param language    content language of the findings
     * @param tab         id of the tab, which the site keeps on
     * @param findings    the findings as a JSON array
     * @param environment the request
     * @return the stored run, without its findings
     */
    @GraphQLField
    @GraphQLName("saveFindings")
    @GraphQLNonNull
    @GraphQLDescription("Stores the findings of a tab of the drawer as its latest run on a page, for every editor of the site")
    public GqlAuditRun saveFindings(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            @GraphQLName("tab") @GraphQLNonNull @GraphQLDescription("Id of the tab that found them") String tab,
            @GraphQLName("findings") @GraphQLNonNull @GraphQLDescription("The findings as a JSON array of "
                    + "{key, rule, tab, severity, title, detail, count}") String findings,
            DataFetchingEnvironment environment) {
        return AuditFields.saveFindings(audits, path, language, tab, findings, environment);
    }
}
