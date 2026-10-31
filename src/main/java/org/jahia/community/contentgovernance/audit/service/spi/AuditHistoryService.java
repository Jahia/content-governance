package org.jahia.community.contentgovernance.audit.service.spi;

import java.util.List;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.audit.model.AuditRun;
import org.jahia.community.contentgovernance.audit.model.AuditedPage;
import org.json.JSONObject;

/**
 * The Page quality audits a site keeps, so every editor sees the last audits of a page and the site
 * dashboard lists them. Audits are stored per page and language in the module's private node of the
 * site ({@code content-governance/audits/<language>/<page id>}), the last {@link #MAX_RUNS} of each.
 *
 * <p>Callers check the permissions first: the service reads and writes with system sessions, and
 * never decides who may see what.</p>
 *
 * <p>Thread-safety: implementations are stateless and may be called concurrently; two audits of the
 * same page saved at the same moment are both kept.</p>
 */
public interface AuditHistoryService {

    /** Audits kept per page and language; older ones are dropped when a new one is saved. */
    int MAX_RUNS = 10;

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
     * @param sitePath path of the site
     * @param pageId   identifier of the page
     * @param language content language
     * @return the stored audits of the page, newest first; only the newest carries its results; empty
     *     when the page has none
     * @throws RepositoryException on a repository failure
     */
    List<AuditRun> history(String sitePath, String pageId, String language) throws RepositoryException;

    /**
     * @param sitePath path of the site
     * @param language content language
     * @return every page of the site with a stored audit in {@code language}, in no particular order,
     *     whether or not it still exists or the caller may read it
     * @throws RepositoryException on a repository failure
     */
    List<AuditedPage> pages(String sitePath, String language) throws RepositoryException;
}
