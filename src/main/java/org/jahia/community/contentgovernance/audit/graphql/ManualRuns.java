package org.jahia.community.contentgovernance.audit.graphql;

import graphql.schema.DataFetchingEnvironment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.audit.service.internal.ManualResults;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRSessionFactory;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fields that run the rules on demand of a subdomain ({@code trigger: manual}) on a page, and read their
 * stored runs: permissions first ({@link AuditAccess}), then the run, then the store ({@link AuditHistoryService}).
 * A run applies the rules of the server on the page rendered as the caller, and takes the results of the rules of
 * the browser that the drawer of the caller measured on the preview.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class ManualRuns {

    private static final Logger logger = LoggerFactory.getLogger(ManualRuns.class);
    private static final String WORKSPACE = "default";
    static final int MAX_RUNS_PER_REQUEST = 1;
    static final int MAX_READS_PER_REQUEST = 20;

    private ManualRuns() {
    }

    /**
     * Runs the rules on demand of a subdomain on a page, and stores the run as the latest of the subdomain.
     *
     * @param service        the run store
     * @param pages          the check of a rendered page
     * @param path           path of the page
     * @param language       content language
     * @param subdomain      id of the subdomain of the rules, which the site keeps on
     * @param browserResults the results of the rules of the browser as a JSON array, or null
     * @param environment    the request, to refuse repeated runs in one request
     * @return the stored run, with its results
     */
    public static GqlAuditRun run(AuditHistoryService service, HtmlCheck pages, String path, String language,
                                  String subdomain, String browserResults, DataFetchingEnvironment environment) {
        AuditAccess.count(environment, "runManualRules", MAX_RUNS_PER_REQUEST);
        String user = AuditAccess.user();
        AuditAccess.Target target = AuditAccess.subdomainPage(path, language, subdomain);
        List<ContentRule> rules = manualRules(ContentRules.forPath(target.page().getPath()), subdomain);
        if (rules.isEmpty()) {
            throw new DataFetchingException("The subdomain " + subdomain + " has no rule that runs on demand");
        }
        Set<String> serverIds = rules.stream().filter(rule -> !rule.inBrowser()).map(ContentRule::id)
                .collect(Collectors.toSet());
        Map<String, String> browserSeverities = new LinkedHashMap<>();
        rules.stream().filter(ContentRule::inBrowser).forEach(rule -> browserSeverities.put(rule.id(), rule.severity()));
        JSONArray results = serverIds.isEmpty() ? new JSONArray() : serverResults(pages, target, language, serverIds);
        try {
            ManualResults.ofBrowser(browserResults, browserSeverities).forEach(results::put);
        } catch (IllegalArgumentException e) {
            throw new DataFetchingException(e.getMessage());
        }
        try {
            return new GqlAuditRun(service.saveManualRun(target.site().getPath(), target.page().getIdentifier(), language,
                    subdomain, user, ManualResults.stored(results)));
        } catch (RepositoryException e) {
            logger.warn("could not store the run of the rules of {} on {}: {}", subdomain, forLog(path), e.toString());
            throw new DataFetchingException("The run could not be stored");
        }
    }

    /**
     * @param service     the run store
     * @param path        path of a page
     * @param language    content language
     * @param subdomain   id of the subdomain, which the site keeps on
     * @param environment the request, to bound the reads of one request
     * @return the stored runs of the rules on demand of the subdomain on the page, newest first, the newest with
     *     its results
     */
    public static List<GqlAuditRun> history(AuditHistoryService service, String path, String language, String subdomain,
                                            DataFetchingEnvironment environment) {
        AuditAccess.count(environment, "manualRuns", MAX_READS_PER_REQUEST);
        AuditAccess.Target target = AuditAccess.subdomainPage(path, language, subdomain);
        try {
            return service.history(target.site().getPath(), target.page().getIdentifier(), language,
                    AuditHistoryService.manualTab(subdomain)).stream().map(GqlAuditRun::new).toList();
        } catch (RepositoryException e) {
            logger.warn("could not read the runs of {} on {}: {}", subdomain, forLog(path), e.toString());
            throw new DataFetchingException("The runs could not be read");
        }
    }

    /**
     * @param rules     the rules of the site
     * @param subdomain the id of a subdomain
     * @return the rules on demand that the site keeps on in the subdomain, in the order of the rules
     */
    static List<ContentRule> manualRules(ContentRules rules, String subdomain) {
        return rules.all().stream()
                .filter(rule -> rule.manual() && rule.enabled() && rule.placed() && subdomain.equals(rule.subdomain()))
                .toList();
    }

    /** The results of the rules of the server on the page, rendered as the caller in the edit workspace. */
    private static JSONArray serverResults(HtmlCheck pages, AuditAccess.Target target, String language, Set<String> ids) {
        Locale locale = Locales.of(language);
        HtmlCheck.Outcome outcome;
        try {
            outcome = pages.render(JCRSessionFactory.getInstance().getCurrentUserSession(WORKSPACE, locale),
                    target.page().getPath());
        } catch (RepositoryException e) {
            throw new DataFetchingException("The page could not be read");
        }
        if (!(outcome instanceof HtmlCheck.Rendered rendered)) {
            throw new DataFetchingException("Jahia could not render " + target.page().getPath());
        }
        return ManualResults.ofServer(pages.manualResults(rendered, locale, ids));
    }

    /** A path for the log: line breaks removed, so a caller cannot forge log lines. */
    private static String forLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }
}
