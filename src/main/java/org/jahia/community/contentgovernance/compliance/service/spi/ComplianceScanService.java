package org.jahia.community.contentgovernance.compliance.service.spi;

import org.jahia.community.contentgovernance.compliance.model.ComplianceReport;
import org.jahia.community.contentgovernance.compliance.model.SiteComplianceReport;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.Locale;

/**
 * Scans content against the rules of the evaluator {@code pattern} of its site, such as the terms that the
 * legal team forbids and the glossary terms to translate: one page as Page Builder shows it
 * ({@link #scan}), or a whole site where its content is stored ({@link #scanSite}). Each issue names its
 * rule and the folder of the rule.
 *
 * <p>The page scan reads, in the language of the page node's session:</p>
 * <ul>
 *     <li>the text properties of the page and of every content stored under it, sub-pages excluded;</li>
 *     <li>the contents that a content reference ({@code jmix:nodeReference}) of the page points to;</li>
 *     <li>the areas of its ancestor pages, up to the home page, for which the page has no area of the
 *     same name: the header and footer parented on the home page;</li>
 *     <li>the contents stored elsewhere that the caller saw rendered on the page (listings).</li>
 * </ul>
 * <p>A text property is a {@code STRING} property shown in Content Editor as text, text area or rich
 * text, with no value constraint (choicelists and tokens are technical values). Rich text is read as
 * the text a visitor sees. The rules are those that the site keeps on, that apply on the day and to the
 * language of the session, and that one text can break: a mandatory mention needs a whole page.</p>
 *
 * <p>The content is read through the session of the given node: the scan never sees content that the
 * user cannot read. The rules are read with a system session, so a rule applies even when the user
 * cannot read its folder. Implementations are thread-safe; a call uses one session and must stay on the
 * thread that owns it.</p>
 */
public interface ComplianceScanService {

    /** Most nodes read by one scan; the report says when the limit was reached. */
    int MAX_SCANNED_NODES = 5000;

    /** Most issues listed by one scan; the report says when the limit was reached. */
    int MAX_ISSUES = 1000;

    /** Most rendered paths taken from the caller. */
    int MAX_RENDERED_PATHS = 2000;

    /** Most nodes read by one site scan; the report says when the limit was reached. */
    int MAX_SITE_SCANNED_NODES = 20000;

    /** Most issues listed by one site scan; the report says when the limit was reached. */
    int MAX_SITE_ISSUES = 5000;

    /**
     * Scans a page.
     *
     * @param page          the page, from a session of the workspace and language to scan
     * @param uiLocale      language of the labels in the report (content types, properties)
     * @param renderedPaths paths of the contents the page shows, as rendered in the edit frame; may be
     *                      {@code null}. Paths outside the page's site, pages, the page's ancestors and
     *                      paths the session cannot read are ignored.
     * @return the report, never {@code null}
     * @throws RepositoryException when the page cannot be read
     */
    ComplianceReport scan(JCRNodeWrapper page, Locale uiLocale, Collection<String> renderedPaths)
            throws RepositoryException;

    /**
     * Scans a whole site: every page with the content stored under it, and every content item of the
     * site's {@code contents} folder. Each node is read once: content references are not followed (their
     * targets are scanned where they are stored) and the header and footer count for the page that
     * stores them (the home page).
     *
     * @param site     the site, from a session of the workspace and language to scan
     * @param uiLocale language of the labels in the report
     * @return the report, never {@code null}
     * @throws RepositoryException when the site cannot be read
     */
    SiteComplianceReport scanSite(JCRSiteNode site, Locale uiLocale) throws RepositoryException;
}
