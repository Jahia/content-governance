package org.jahia.community.contentgovernance.audit.service.spi;

import java.util.List;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.audit.model.AuditRun;
import org.jahia.community.contentgovernance.audit.model.AuditedPage;
import org.json.JSONObject;

/**
 * The runs a site keeps of each tab of the drawer, so every editor sees the last runs of a page. A
 * run is a Page quality audit (tab {@code audit}), which the site dashboard lists, or the findings
 * that another tab stored. Runs are stored per page and language in the module's private node of the
 * site ({@code content-governance/audits/<language>/<page id>}), the last {@link #MAX_RUNS} of each
 * tab.
 *
 * <p>Callers check the permissions first: the service reads and writes with system sessions, and
 * never decides who may see what.</p>
 *
 * <p>Thread-safety: implementations are stateless and may be called concurrently; two audits of the
 * same page saved at the same moment are both kept.</p>
 */
public interface AuditHistoryService {

    /** Runs kept per tab, page and language; older ones are dropped when a new one is saved. */
    int MAX_RUNS = 10;

    /** The tab of the Page quality audits. */
    String AUDIT_TAB = "audit";

    /**
     * Stores an audit as the latest of its page.
     *
     * @param sitePath path of the site ({@code /sites/<key>})
     * @param pageId   identifier of the page
     * @param language content language of the audit
     * @param path     path of the page now
     * @param title    title of the page now, or null
     * @param user     username of the editor who ran the audit
     * @param results  the checked results ({@code AuditResults.parse})
     * @return the stored audit, with its summary and without its results
     * @throws RepositoryException when the audit could not be stored
     */
    AuditRun save(String sitePath, String pageId, String language, String path, String title, String user,
                  JSONObject results) throws RepositoryException;

    /**
     * Stores the findings of a tab as the latest run of that tab on the page. The site dashboard of
     * Page quality does not list it.
     *
     * @param sitePath path of the site ({@code /sites/<key>})
     * @param pageId   identifier of the page
     * @param language content language of the findings
     * @param tab      id of the tab that found them, never {@link #AUDIT_TAB}
     * @param user     username of the editor who ran the tab
     * @param findings the checked findings ({@code AuditResults.parseFindings})
     * @return the stored run, with its summary and without its findings
     * @throws RepositoryException when the run could not be stored
     */
    AuditRun saveFindings(String sitePath, String pageId, String language, String tab, String user,
                          JSONObject findings) throws RepositoryException;

    /**
     * @param sitePath path of the site
     * @param pageId   identifier of the page
     * @param language content language
     * @param tab      id of the tab
     * @return the stored runs of the tab on the page, newest first; only the newest carries what it
     *     stores; empty when the page has none
     * @throws RepositoryException on a repository failure
     */
    List<AuditRun> history(String sitePath, String pageId, String language, String tab) throws RepositoryException;

    /**
     * @param sitePath path of the site
     * @param language content language
     * @return every page of the site with a stored audit in {@code language}, in no particular order,
     *     whether or not it still exists or the caller may read it
     * @throws RepositoryException on a repository failure
     */
    List<AuditedPage> pages(String sitePath, String language) throws RepositoryException;
}
