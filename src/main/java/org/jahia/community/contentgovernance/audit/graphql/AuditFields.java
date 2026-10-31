package org.jahia.community.contentgovernance.audit.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import org.jahia.community.contentgovernance.audit.model.AuditedPage;
import org.jahia.community.contentgovernance.audit.service.internal.AuditResults;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.utils.LanguageCodeConverters;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Page quality audit fields of the module's query and mutation types: permissions first
 * ({@link AuditAccess}), then the store ({@link AuditHistoryService}).
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class AuditFields {

    private static final Logger logger = LoggerFactory.getLogger(AuditFields.class);
    private static final String WORKSPACE = "default";
    private static final String TITLE = "jcr:title";
    /** Pages counted for "N of M pages audited": past this, the dashboard says "more than". */
    static final int MAX_COUNTED_PAGES = 10_000;
    static final int MAX_SAVES_PER_REQUEST = 1;
    static final int MAX_PAGE_READS_PER_REQUEST = 5;
    static final int MAX_SITE_READS_PER_REQUEST = 1;

    private AuditFields() {
    }

    /**
     * Stores an audit the caller ran in the drawer.
     *
     * @param service     the audit store
     * @param path        path of the audited page
     * @param language    content language of the audit
     * @param results     the results as JSON
     * @param environment the request, to refuse repeated saves in one request
     * @return the stored audit, without its results
     */
    public static GqlAuditRun save(AuditHistoryService service, String path, String language, String results,
                                   DataFetchingEnvironment environment) {
        AuditAccess.count(environment, "saveAudit", MAX_SAVES_PER_REQUEST);
        String user = AuditAccess.user();
        AuditAccess.Target target = AuditAccess.page(path, language);
        JSONObject checked;
        try {
            checked = AuditResults.parse(results);
        } catch (IllegalArgumentException e) {
            throw new DataFetchingException(e.getMessage());
        }
        try {
            JCRNodeWrapper page = target.page();
            return new GqlAuditRun(service.save(target.site().getPath(), page.getIdentifier(), language, page.getPath(),
                    title(page.getIdentifier(), language), user, checked));
        } catch (RepositoryException e) {
            logger.warn("could not store the audit of {}: {}", forLog(path), e.toString());
            throw new DataFetchingException("The audit could not be stored");
        }
    }

    /**
     * @param service     the audit store
     * @param path        path of a page
     * @param language    content language
     * @param environment the request, to bound the reads of one request
     * @return the stored audits of the page, newest first, the newest with its results
     */
    public static List<GqlAuditRun> history(AuditHistoryService service, String path, String language,
                                            DataFetchingEnvironment environment) {
        AuditAccess.count(environment, "pageAudits", MAX_PAGE_READS_PER_REQUEST);
        AuditAccess.Target target = AuditAccess.page(path, language);
        try {
            return service.history(target.site().getPath(), target.page().getIdentifier(), language).stream()
                    .map(GqlAuditRun::new).toList();
        } catch (RepositoryException e) {
            logger.warn("could not read the audits of {}: {}", forLog(path), e.toString());
            throw new DataFetchingException("The audits could not be read");
        }
    }

    /**
     * @param service     the audit store
     * @param siteKey     key of the site
     * @param language    content language
     * @param environment the request, to bound the reads of one request
     * @return the latest audit of every audited page the caller may read, most issues first
     */
    public static GqlSiteAudits site(AuditHistoryService service, String siteKey, String language,
                                     DataFetchingEnvironment environment) {
        AuditAccess.count(environment, "siteAudits", MAX_SITE_READS_PER_REQUEST);
        JCRSiteNode site = AuditAccess.site(siteKey, language);
        try {
            JCRSessionWrapper session = localizedSession(language);
            List<GqlAuditedPage> pages = new ArrayList<>();
            for (AuditedPage stored : service.pages(site.getPath(), language)) {
                JCRNodeWrapper page = readable(session, stored.pageId());
                if (page != null) {
                    pages.add(new GqlAuditedPage(stored, page.getPath(), page.getPropertyAsString(TITLE)));
                }
            }
            pages.sort(Comparator.comparingInt((GqlAuditedPage p) -> p.getLastAudit().getSummary().getTotal()).reversed()
                    .thenComparing(GqlAuditedPage::getPath));
            return new GqlSiteAudits(pages, countPages(session, site.getPath()));
        } catch (RepositoryException e) {
            logger.warn("could not read the audits of site {}: {}", siteKey, e.toString());
            throw new DataFetchingException("The audits could not be read");
        }
    }

    /** The page as the caller may read it, or null when it was deleted or the caller may not read it. */
    private static JCRNodeWrapper readable(JCRSessionWrapper session, String id) throws RepositoryException {
        try {
            return session.getNodeByIdentifier(id);
        } catch (ItemNotFoundException e) {
            return null;
        }
    }

    private static int countPages(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [jnt:page] WHERE ISDESCENDANTNODE('" + sitePath + "')", Query.JCR_SQL2);
        query.setLimit(MAX_COUNTED_PAGES);
        NodeIterator nodes = query.execute().getNodes();
        int count = 0;
        while (nodes.hasNext()) {
            nodes.nextNode();
            count++;
        }
        return count;
    }

    private static String title(String id, String language) throws RepositoryException {
        JCRNodeWrapper page = readable(localizedSession(language), id);
        return page == null ? null : page.getPropertyAsString(TITLE);
    }

    private static JCRSessionWrapper localizedSession(String language) throws RepositoryException {
        Locale locale = LanguageCodeConverters.languageCodeToLocale(language);
        return JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE, locale);
    }

    /** A path for the log: line breaks removed, so a caller cannot forge log lines. */
    private static String forLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }
}
