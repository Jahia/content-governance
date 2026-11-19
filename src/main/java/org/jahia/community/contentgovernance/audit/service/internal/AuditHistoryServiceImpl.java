package org.jahia.community.contentgovernance.audit.service.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.jcr.InvalidItemStateException;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.audit.model.AuditRun;
import org.jahia.community.contentgovernance.audit.model.AuditSummary;
import org.jahia.community.contentgovernance.audit.model.AuditedPage;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

/**
 * Stores the runs in the site's private node of the module, with system sessions in the default
 * workspace: one node per language and page, holding the summary of the latest Page quality audit,
 * and one child per run holding the id of its tab and what it stores. A run stored before the runs
 * carried a tab is a Page quality audit.
 */
@Component(service = AuditHistoryService.class, immediate = true)
public class AuditHistoryServiceImpl implements AuditHistoryService {

    private static final String WORKSPACE = "default";
    private static final String NODE_TYPE = "nt:unstructured";
    static final String AUDITS = "audits";
    private static final String RUN_PREFIX = "run-";
    private static final String P_PATH = "path";
    private static final String P_TITLE = "title";
    private static final String P_AT = "auditedAt";
    private static final String P_BY = "auditedBy";
    private static final String P_SUMMARY = "summary";
    private static final String P_PREVIOUS_TOTAL = "previousTotal";
    private static final String P_RESULTS = "results";
    private static final String P_TAB = "tab";

    /** The page a run belongs to; the path and title are null for a run of a tab other than Page quality. */
    private record Page(String sitePath, String pageId, String language, String path, String title) {
    }

    /** One run to store. */
    private record Run(String tab, long auditedAt, String user, AuditSummary summary, String results) {
    }

    @Override
    public AuditRun save(String sitePath, String pageId, String language, String path, String title, String user,
                         JSONObject results) throws RepositoryException {
        return save(new Page(sitePath, pageId, language, path, title), AUDIT_TAB, user, AuditSummary.of(results), results);
    }

    @Override
    public AuditRun saveFindings(String sitePath, String pageId, String language, String tab, String user,
                                 JSONObject findings) throws RepositoryException {
        if (AUDIT_TAB.equals(tab)) {
            throw new IllegalArgumentException("Page quality stores its results, not findings");
        }
        AuditSummary summary = AuditSummary.ofFindings(tab, findings.getJSONArray(AuditResults.FINDINGS));
        return save(new Page(sitePath, pageId, language, null, null), tab, user, summary, findings);
    }

    private static AuditRun save(Page page, String tab, String user, AuditSummary summary, JSONObject stored)
            throws RepositoryException {
        Run run = new Run(tab, System.currentTimeMillis(), user, summary, stored.toString());
        // Two runs of one page saved together conflict on the page node: the second tries again.
        try {
            store(page, run);
        } catch (InvalidItemStateException e) {
            store(page, run);
        }
        return new AuditRun(tab, run.auditedAt(), user, summary, null);
    }

    private static void store(Page target, Run entry) throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, session -> {
            JCRNodeWrapper store = SiteFeatures.storeNode(session.getNode(target.sitePath()));
            JCRNodeWrapper page = child(child(child(store, AUDITS), target.language()), target.pageId());

            String name = RUN_PREFIX + entry.auditedAt();
            for (int i = 1; page.hasNode(name); i++) {
                name = RUN_PREFIX + entry.auditedAt() + "-" + i;
            }
            String summary = entry.summary().toJson();
            JCRNodeWrapper run = page.addNode(name, NODE_TYPE);
            run.setProperty(P_TAB, entry.tab());
            run.setProperty(P_AT, entry.auditedAt());
            run.setProperty(P_BY, entry.user());
            run.setProperty(P_SUMMARY, summary);
            run.setProperty(P_RESULTS, entry.results());

            // The page node holds the latest Page quality audit, which the site dashboard lists
            if (AUDIT_TAB.equals(entry.tab())) {
                int previousTotal = page.hasProperty(P_SUMMARY)
                        ? AuditSummary.fromJson(page.getProperty(P_SUMMARY).getString()).total() : -1;
                page.setProperty(P_PREVIOUS_TOTAL, previousTotal);
                page.setProperty(P_PATH, target.path());
                page.setProperty(P_TITLE, target.title());
                page.setProperty(P_AT, entry.auditedAt());
                page.setProperty(P_BY, entry.user());
                page.setProperty(P_SUMMARY, summary);
            }

            List<JCRNodeWrapper> runs = runs(page, entry.tab());
            for (JCRNodeWrapper old : runs.subList(Math.min(MAX_RUNS, runs.size()), runs.size())) {
                old.remove();
            }
            session.save();
            return null;
        });
    }

    @Override
    public List<AuditRun> history(String sitePath, String pageId, String language, String tab) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, session -> {
            JCRNodeWrapper page = existing(session.getNode(sitePath), SiteFeatures.STORE_NODE, AUDITS, language, pageId);
            List<AuditRun> out = new ArrayList<>();
            if (page == null) {
                return out;
            }
            for (JCRNodeWrapper run : runs(page, tab)) {
                out.add(new AuditRun(tab, run.getProperty(P_AT).getLong(), string(run, P_BY),
                        AuditSummary.fromJson(string(run, P_SUMMARY)), out.isEmpty() ? string(run, P_RESULTS) : null));
            }
            return out;
        });
    }

    @Override
    public List<AuditedPage> pages(String sitePath, String language) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, WORKSPACE, null, session -> {
            JCRNodeWrapper languageNode = existing(session.getNode(sitePath), SiteFeatures.STORE_NODE, AUDITS, language);
            List<AuditedPage> out = new ArrayList<>();
            if (languageNode == null) {
                return out;
            }
            NodeIterator pages = languageNode.getNodes();
            while (pages.hasNext()) {
                JCRNodeWrapper page = (JCRNodeWrapper) pages.nextNode();
                if (page.hasProperty(P_AT)) {
                    AuditRun last = new AuditRun(AUDIT_TAB, page.getProperty(P_AT).getLong(), string(page, P_BY),
                            AuditSummary.fromJson(string(page, P_SUMMARY)), null);
                    int previous = page.hasProperty(P_PREVIOUS_TOTAL) ? (int) page.getProperty(P_PREVIOUS_TOTAL).getLong() : -1;
                    out.add(new AuditedPage(page.getName(), string(page, P_PATH), string(page, P_TITLE), last, previous));
                }
            }
            return out;
        });
    }

    /** The descendant reached through {@code names}, one child at a time, or null when one is missing. */
    private static JCRNodeWrapper existing(JCRNodeWrapper parent, String... names) throws RepositoryException {
        JCRNodeWrapper node = parent;
        for (String name : names) {
            if (!node.hasNode(name)) {
                return null;
            }
            node = node.getNode(name);
        }
        return node;
    }

    /** The runs of a tab on a page node, newest first; a run with no tab is a Page quality audit. */
    private static List<JCRNodeWrapper> runs(JCRNodeWrapper page, String tab) throws RepositoryException {
        List<JCRNodeWrapper> runs = new ArrayList<>();
        NodeIterator children = page.getNodes();
        while (children.hasNext()) {
            JCRNodeWrapper run = (JCRNodeWrapper) children.nextNode();
            String runTab = run.hasProperty(P_TAB) ? run.getProperty(P_TAB).getString() : AUDIT_TAB;
            if (run.getName().startsWith(RUN_PREFIX) && run.hasProperty(P_AT) && tab.equals(runTab)) {
                runs.add(run);
            }
        }
        runs.sort(Comparator.comparingLong(AuditHistoryServiceImpl::auditedAt).reversed());
        return runs;
    }

    private static long auditedAt(JCRNodeWrapper run) {
        try {
            return run.getProperty(P_AT).getLong();
        } catch (RepositoryException e) {
            return 0;
        }
    }

    private static String string(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.hasProperty(property) ? node.getProperty(property).getString() : null;
    }

    private static JCRNodeWrapper child(JCRNodeWrapper parent, String name) throws RepositoryException {
        return parent.hasNode(name) ? parent.getNode(name) : parent.addNode(name, NODE_TYPE);
    }
}
