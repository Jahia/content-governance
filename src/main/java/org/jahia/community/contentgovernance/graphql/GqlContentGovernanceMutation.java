package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.community.contentgovernance.audit.graphql.AuditFields;
import org.jahia.community.contentgovernance.audit.graphql.GqlAuditRun;
import org.jahia.community.contentgovernance.audit.graphql.ManualRuns;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.SiteLayout;
import org.jahia.community.contentgovernance.rules.TreeLayout;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.RepositoryException;

/**
 * Layout and audit mutations. Authorised here, on the server: the route of a screen only hides itself from users
 * without {@value SiteFeatures#SETTINGS_PERMISSION} on the site, or without {@value #SERVER_PERMISSION} for the
 * defaults of the system site.
 */
@GraphQLName("ContentGovernanceMutation")
@GraphQLDescription("Content Governance mutations")
public class GqlContentGovernanceMutation {

    private static final Logger logger = LoggerFactory.getLogger(GqlContentGovernanceMutation.class);
    /** The permission on the root node that the defaults of the system site require: a server administrator. */
    static final String SERVER_PERMISSION = "admin";

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private AuditHistoryService audits;

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private HtmlCheck pages;

    /**
     * Saves how a site arranges the domains, the subdomains and the rules of the system site. The write goes
     * through the session of the caller, under {@code contents} of the site. For {@code systemsite}, a server
     * administrator saves the defaults of every site: the layout reorders, moves and switches the nodes of the tree
     * ({@link TreeLayout}), and no JSON is stored.
     *
     * @param siteKey key of the site, or {@code systemsite} for the defaults
     * @param layout  the JSON of the layout
     * @return the layout as stored, not saved for {@code systemsite}
     */
    @GraphQLField
    @GraphQLName("saveSiteLayout")
    @GraphQLNonNull
    @GraphQLDescription("Saves the layout of a site: the order and the place of its domains, subdomains and rules, and what it switches off. "
            + "For systemsite, writes the order, the places and the default switches into the tree of the defaults")
    public GqlSiteLayout saveSiteLayout(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("layout") @GraphQLNonNull @GraphQLDescription("The JSON of the layout, {domains: [{id, subdomains: [{id, rules: [id]}]}], off: [id]}") String layout) {
        SiteLayout parsed;
        try {
            parsed = SiteLayout.parse(layout);
        } catch (IllegalArgumentException e) {
            throw new DataFetchingException(e.getMessage());
        }
        return writeLayout(siteKey, parsed);
    }

    /**
     * Removes the layout of a site, which then keeps the layout of the system site. The system site has no reset.
     *
     * @param siteKey key of the site, other than {@code systemsite}
     * @return the layout of the site, not saved
     */
    @GraphQLField
    @GraphQLName("resetSiteLayout")
    @GraphQLNonNull
    @GraphQLDescription("Removes the layout of a site, which then keeps the layout of the system site")
    public GqlSiteLayout resetSiteLayout(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        return writeLayout(siteKey, SiteLayout.NONE);
    }

    private static GqlSiteLayout writeLayout(String siteKey, SiteLayout layout) {
        JCRSiteNode site = SiteAccess.site(siteKey);
        if (ContentDomains.SYSTEM_SITE_KEY.equals(siteKey)) {
            return writeDefaults(layout);
        }
        if (!site.hasPermission(SiteFeatures.SETTINGS_PERMISSION) || !SiteFeatures.moduleEnabled(site)) {
            throw new DataFetchingException(SiteAccess.DENIED);
        }
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("default");
            SiteLayout.write(session, site.getPath(), layout);
            session.save();
            return new GqlSiteLayout(layout);
        } catch (RepositoryException e) {
            logger.warn("could not save the layout of {}: {}", siteKey, e.toString());
            throw new DataFetchingException("The layout could not be saved");
        }
    }

    /** Writes the defaults of every site into the tree of the system site, for a server administrator. */
    private static GqlSiteLayout writeDefaults(SiteLayout layout) {
        if (!layout.saved()) {
            throw new DataFetchingException("The defaults of the system site have no reset: save the order and the switches");
        }
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("default");
            if (!session.getRootNode().hasPermission(SERVER_PERMISSION)) {
                throw new DataFetchingException(SiteAccess.DENIED);
            }
            int changed = TreeLayout.write(session, layout);
            session.save();
            logger.info("A server administrator changed {} nodes of the defaults in {}", changed, ContentDomains.TREE);
            return new GqlSiteLayout(SiteLayout.NONE);
        } catch (RepositoryException e) {
            logger.warn("could not save the defaults in {}: {}", ContentDomains.TREE, e.toString());
            throw new DataFetchingException("The defaults could not be saved");
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

    /**
     * Runs the rules on demand ({@code trigger: manual}) of a subdomain on a page, and stores the run as the latest of
     * the subdomain, for every editor of the site. The rules of the server run on the page rendered as the caller.
     * The drawer of the caller runs the rules of the browser on the preview, and sends their results.
     *
     * @param path           path of the page or main resource
     * @param language       content language
     * @param subdomain      id of the subdomain of the rules
     * @param browserResults the results of the rules of the browser as a JSON array, or null
     * @param environment    the request
     * @return the stored run, with its results
     */
    @GraphQLField
    @GraphQLName("runManualRules")
    @GraphQLNonNull
    @GraphQLDescription("Runs the rules on demand (trigger: manual) of a subdomain on a page, and stores the run as the "
            + "latest of the subdomain, for every editor of the site")
    public GqlAuditRun runManualRules(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            @GraphQLName("subdomain") @GraphQLNonNull @GraphQLDescription("Id of the subdomain of the rules") String subdomain,
            @GraphQLName("browserResults") @GraphQLDescription("The results of the rules of the browser that the drawer "
                    + "ran on the preview, as a JSON array of {ruleId, passed, values, findings}") String browserResults,
            DataFetchingEnvironment environment) {
        return ManualRuns.run(audits, pages, path, language, subdomain, browserResults, environment);
    }
}
