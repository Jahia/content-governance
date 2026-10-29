package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.community.contentgovernance.compliance.service.spi.ComplianceScanService;
import org.jahia.community.contentgovernance.settings.Features;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.LanguageCodeConverters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compliance scan queries. Read-only: nothing is written, so the CSRF guard does not apply.
 */
@GraphQLName("ComplianceScanQuery")
@GraphQLDescription("Content compliance scan queries")
public class GqlComplianceScan {

    private static final Logger logger = LoggerFactory.getLogger(GqlComplianceScan.class);

    /** Who may scan a page: whoever may edit it. Checked on the page in the default workspace. */
    static final String REQUIRED_PERMISSION = "jcr:write";
    private static final String EDIT_WORKSPACE = "default";
    private static final String PAGE_TYPE = "jnt:page";
    /** Who may scan a whole site: whoever may open jContent on it. Checked on the site node. */
    static final String SITE_PERMISSION = "jContentAccess";
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8}){0,3}$");
    private static final String DENIED = "Permission denied";
    /** Scans one request may run: a request with many aliases of scanSite would otherwise read the site many times. */
    static final int MAX_PAGE_SCANS_PER_REQUEST = 5;
    static final int MAX_SITE_SCANS_PER_REQUEST = 1;

    @Inject
    @GraphQLOsgiService
    private ComplianceScanService scanService;

    /**
     * Scans a page in one language of the default workspace, as the current user.
     *
     * @param path          path of the page
     * @param language      content language to scan
     * @param uiLanguage    language of the labels in the report, English when missing
     * @param renderedPaths paths of the contents the edit frame shows for the page
     * @return the report
     */
    @GraphQLField
    @GraphQLName("scanPage")
    @GraphQLNonNull
    @GraphQLDescription("Scans the text of a page, of the content under it, of the content it references and of the shared content it shows")
    public GqlComplianceReport scanPage(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language to scan") String language,
            @GraphQLName("uiLanguage") @GraphQLDescription("Language of the labels in the report") String uiLanguage,
            @GraphQLName("renderedPaths") @GraphQLDescription("Paths of the contents the edit frame shows for this page (header, footer, listings)") List<String> renderedPaths,
            DataFetchingEnvironment environment) {
        requireAuthenticated();
        countScan(environment, "scanPage", MAX_PAGE_SCANS_PER_REQUEST);
        Locale contentLocale = contentLocale(language);
        Locale uiLocale = uiLocale(uiLanguage);
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(EDIT_WORKSPACE, contentLocale);
            JCRNodeWrapper page = session.getNode(path);
            if (!page.isNodeType(PAGE_TYPE) || !page.hasPermission(REQUIRED_PERMISSION)) {
                throw new DataFetchingException(DENIED);
            }
            requireModuleEnabled(page.getResolveSite());
            return new GqlComplianceReport(scanService.scan(page, uiLocale, renderedPaths));
        } catch (PathNotFoundException e) {
            throw new DataFetchingException(DENIED);
        } catch (RepositoryException e) {
            logger.warn("Compliance scan of {} in {} failed: {}", forLog(path), language, e.toString());
            throw new DataFetchingException("The scan could not read the content");
        }
    }

    /**
     * Scans a whole site in one language of the default workspace, as the current user.
     *
     * @param siteKey    key of the site
     * @param language   content language to scan
     * @param uiLanguage language of the labels in the report, English when missing
     * @return the report
     */
    @GraphQLField
    @GraphQLName("scanSite")
    @GraphQLNonNull
    @GraphQLDescription("Scans every page and every content item of the content folders of a site")
    public GqlSiteComplianceReport scanSite(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language to scan") String language,
            @GraphQLName("uiLanguage") @GraphQLDescription("Language of the labels in the report") String uiLanguage,
            DataFetchingEnvironment environment) {
        requireAuthenticated();
        countScan(environment, "scanSite", MAX_SITE_SCANS_PER_REQUEST);
        if (siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            throw new DataFetchingException(DENIED);
        }
        Locale contentLocale = contentLocale(language);
        Locale uiLocale = uiLocale(uiLanguage);
        try {
            JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(EDIT_WORKSPACE, contentLocale);
            if (!(session.getNode("/sites/" + siteKey) instanceof JCRSiteNode site) || !site.hasPermission(SITE_PERMISSION)) {
                throw new DataFetchingException(DENIED);
            }
            requireModuleEnabled(site);
            return new GqlSiteComplianceReport(scanService.scanSite(site, uiLocale));
        } catch (PathNotFoundException e) {
            throw new DataFetchingException(DENIED);
        } catch (RepositoryException e) {
            logger.warn("Compliance scan of site {} in {} failed: {}", siteKey, language, e.toString());
            throw new DataFetchingException("The scan could not read the content");
        }
    }

    private static void requireAuthenticated() {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user)) {
            throw new DataFetchingException(DENIED);
        }
    }

    /** Refuses a request that runs a scan more often than {@code max} times (aliases). */
    private static void countScan(DataFetchingEnvironment environment, String field, int max) {
        if (environment == null || environment.getGraphQlContext() == null) {
            return;
        }
        String key = GqlComplianceScan.class.getName() + "." + field;
        int count = environment.getGraphQlContext().<Integer>getOrDefault(key, 0) + 1;
        environment.getGraphQlContext().put(key, count);
        if (count > max) {
            throw new DataFetchingException("Too many " + field + " in one request");
        }
    }

    /** A path for the log: line breaks removed, so a caller cannot forge log lines. */
    private static String forLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }

    /**
     * The scan runs only on sites that enable the module (directly or through a module they enable, as
     * jContent checks it) and keep the compliance section switched on in Content Governance settings.
     */
    private static void requireModuleEnabled(JCRSiteNode site) {
        if (site == null || !SiteFeatures.isEnabled(site.getPath(), Features.COMPLIANCE)) {
            throw new DataFetchingException("The compliance scan is not enabled on this site");
        }
    }

    private static Locale contentLocale(String language) {
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            throw new DataFetchingException("Invalid language");
        }
        return LanguageCodeConverters.languageCodeToLocale(language);
    }

    private static Locale uiLocale(String uiLanguage) {
        return uiLanguage != null && LANGUAGE.matcher(uiLanguage).matches()
                ? LanguageCodeConverters.languageCodeToLocale(uiLanguage) : Locale.ENGLISH;
    }
}
