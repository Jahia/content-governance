/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.links.LinkCheck;
import org.jahia.community.contentgovernance.html.render.ServerRender;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.community.contentgovernance.page.HealthChecks;
import org.jahia.community.contentgovernance.page.LinkChecks;
import org.jahia.community.contentgovernance.page.ReadabilityChecks;
import org.jahia.community.contentgovernance.page.SeoChecks;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one service that checks a page or a content item: Jahia renders it on the server, and each check
 * of the scope {@code PAGE} reads the HTML, with the rules of the site. A page gets every check, and an
 * item gets the checks of one item only. The GraphQL fields {@code checkPage} and {@code checkText}, and
 * the tools {@code frame.check} and {@code audit.accessibility} call it, so they give one page the same
 * findings under the same rule ids. The drawer reads the results of {@code checkPage}, with their values,
 * and GEO readiness runs the SEO rules of the service on the HTML that a crawler gets.
 *
 * <p>A render reads the workspace of the session, as its user: in {@code default}, the content that
 * is not published yet. The caller renders inside its session, then checks the HTML outside it. The
 * render also reads, in the session, the state of the content and of its internal links.</p>
 *
 * <p>Thread-safety: stateless.</p>
 */
@Component(service = HtmlCheck.class)
public class HtmlCheck {

    /** Host names that always serve this Jahia, for a link written with one of them. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final String LONG_SENTENCES = "readability.longSentences";
    private static final Logger logger = LoggerFactory.getLogger(HtmlCheck.class);

    @Reference
    private CheckRegistry checks;

    public HtmlCheck() {
        // The constructor of Declarative Services.
    }

    HtmlCheck(CheckRegistry checks) {
        this.checks = checks;
    }

    /** What {@link #render} found at a path. */
    public sealed interface Outcome permits Rendered, NotFound, RenderFailed {
    }

    /**
     * The HTML of a page or of an item. It holds no node, so it stays valid outside the session.
     *
     * @param localHosts the lower-case host names of this Jahia and of the site: a link to one of
     *                   them is an internal link
     * @param facts      what the render read in the repository for the checks, by the key of the fact
     */
    public record Rendered(String path, String primaryType, Scope scope, Document html, Set<String> localHosts,
                           Map<String, Object> facts) implements Outcome {
    }

    /** No node at the path, or none that the user of the session can read. */
    public record NotFound(String path) implements Outcome {
    }

    /** Jahia could not render the node, such as a page whose template is missing. */
    public record RenderFailed(String path, String details) implements Outcome {
    }

    /**
     * Renders the node at the path with the session.
     *
     * @param session the session of the user, in the workspace and the locale to check
     * @param path    the absolute path of a page or of a content item
     */
    public Outcome render(JCRSessionWrapper session, String path) throws RepositoryException {
        JCRNodeWrapper node;
        try {
            node = session.getNode(path);
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return new NotFound(path);
        }
        Scope scope = ServerRender.isPage(node) ? Scope.PAGE : Scope.ITEM;
        String html;
        try {
            html = ServerRender.render(node, session);
        } catch (RenderException e) {
            return new RenderFailed(node.getPath(), e.getMessage() == null ? "" : e.getMessage());
        }
        Document document = Jsoup.parse(html);
        Set<String> hosts = localHosts(node.getResolveSite());
        return new Rendered(node.getPath(), node.getPrimaryNodeTypeName(), scope, document, hosts,
                repositoryFacts(node, document, hosts, session));
    }

    /** The state of the content and of its internal links. A part that cannot be read is left out, and logged. */
    private static Map<String, Object> repositoryFacts(JCRNodeWrapper node, Document html, Set<String> hosts,
                                                       JCRSessionWrapper session) {
        Map<String, Object> facts = new HashMap<>();
        facts.put(LinkChecks.LOCAL_HOSTS, hosts);
        try {
            LinkCheck.InternalLinks internal = LinkCheck.internal(html, hosts, session);
            facts.put(LinkChecks.BROKEN, internal.broken());
            facts.put(LinkChecks.INTERNAL, internal);
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The internal links of {} could not be checked", node.getPath(), e);
        }
        try {
            Locale locale = session.getLocale() == null ? Locale.ENGLISH : session.getLocale();
            facts.put(HealthChecks.CONTENT, HealthChecks.read(node, locale.toString()));
        } catch (RepositoryException | RuntimeException e) {
            logger.warn("The publication and translation state of {} could not be read", node.getPath(), e);
        }
        return Map.copyOf(facts);
    }

    /**
     * Runs the checks of the scope {@code PAGE} on the rules that the site of the node keeps on.
     *
     * @param language the language of the content, such as {@code en}: the language of the page must match it
     * @return the findings, in the order of the rules, at most 50 for each check
     */
    public List<Finding> findings(Rendered rendered, Locale language) {
        return results(rendered, language).stream().flatMap(result -> result.findings().stream()).toList();
    }

    /**
     * Runs the checks of the scope {@code PAGE} on the rules that the site of the node keeps on, and gives
     * the result of each rule that ran, passed or failed, with its values: what the tabs of the drawer show.
     *
     * @param language the language of the content, such as {@code en}: the language of the page must match it
     * @return the results, in the order of the rules, with at most 50 findings each
     */
    public List<CheckResult> results(Rendered rendered, Locale language) {
        ContentRules rules = ContentRules.forPath(rendered.path());
        CheckInput input = input(rendered.path(), language == null ? null : language.getLanguage(), rendered.html(),
                rendered.scope());
        for (Map.Entry<String, Object> fact : rendered.facts().entrySet()) {
            input = input.withFact(fact.getKey(), fact.getValue());
        }
        Integer maxSentenceWords = maxSentenceWords(rules);
        if (maxSentenceWords != null) {
            input = input.withFact(ReadabilityChecks.LONG_SENTENCE_WORDS, maxSentenceWords);
        }
        return checks.results(rules, input);
    }

    /** The maximum of the rule {@code readability.longSentences} of the site, or null. */
    private static Integer maxSentenceWords(ContentRules rules) {
        try {
            Double maximum = rules.get(LONG_SENTENCES).maximum();
            return maximum == null ? null : maximum.intValue();
        } catch (IllegalArgumentException e) {
            // A site with no such rule counts no long sentence.
            return null;
        }
    }

    /**
     * Runs the checks of some rules on HTML that the caller fetched, such as a page as a crawler gets it.
     * The checks read the HTML and its SEO facts only: no fact of the repository and no readability, so a
     * rule of readability gives no finding here.
     *
     * @param rules    the rules of the site of the page
     * @param path     the path of the page
     * @param language the language of the content, such as {@code en}, or null
     * @param html     the HTML of a whole page
     * @param ruleIds  the ids of the rules to run
     * @return the findings of these rules that the site keeps on, in the order of the rules
     */
    public List<Finding> findings(ContentRules rules, String path, String language, Document html, Set<String> ruleIds) {
        // The thresholds of a whole page measure the SEO facts only: no readability is measured for these rules
        CheckInput input = withSeo(CheckInput.page(path, language, html.outerHtml())
                .withFact(HtmlRuleChecks.DOCUMENT, html)
                .withFact(HtmlRuleChecks.HTML_SCOPE, Scope.PAGE), html);
        return checks.runRules(rules, input, ruleIds).stream().map(CheckRegistry.Checked::finding).toList();
    }

    /**
     * The input of the checks of the scope {@code PAGE}: the HTML, and the facts and the values that the
     * thresholds of a page measure on it, such as the length of its title under {@code seo.title}, with its
     * text as a value.
     *
     * @param path     the path of the page or of the item
     * @param language the language of the content, such as {@code en}, or null
     * @param html     the parsed HTML
     * @param scope    what the HTML holds
     */
    public static CheckInput input(String path, String language, Document html, Scope scope) {
        CheckInput input = ReadabilityChecks.withReadability(CheckInput.page(path, language, html.outerHtml())
                .withFact(HtmlRuleChecks.DOCUMENT, html)
                .withFact(HtmlRuleChecks.HTML_SCOPE, scope), html, language);
        return scope == Scope.PAGE ? withSeo(input, html) : input;
    }

    /** The input with the facts and the values of the SEO thresholds of a whole page. */
    private static CheckInput withSeo(CheckInput input, Document html) {
        CheckInput out = input;
        for (Map.Entry<String, Object> fact : SeoChecks.facts(html).entrySet()) {
            out = out.withFact(fact.getKey(), fact.getValue());
        }
        for (Map.Entry<String, Map<String, Object>> values : SeoChecks.values(html).entrySet()) {
            out = out.withValues(values.getKey(), values.getValue());
        }
        return out;
    }

    /** The internal links of the HTML, read in the live workspace. Call it inside the session of the render. */
    public LinkCheck.InternalLinks internalLinks(Rendered rendered, JCRSessionWrapper session) throws RepositoryException {
        return LinkCheck.internal(rendered.html(), rendered.localHosts(), session);
    }

    /** The external links of the HTML, called from this server. Call it outside a session: it waits on the network. */
    public LinkCheck.ExternalLinks externalLinks(Rendered rendered) {
        return LinkCheck.external(rendered.html(), rendered.localHosts());
    }

    private static Set<String> localHosts(JCRSiteNode site) {
        Set<String> hosts = new HashSet<>(LOCAL_HOSTS);
        if (site != null) {
            for (String name : site.getAllServerNames()) {
                if (name != null && !name.isBlank()) {
                    hosts.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(hosts);
    }
}
