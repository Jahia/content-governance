package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.community.contentgovernance.audit.graphql.AuditFields;
import org.jahia.community.contentgovernance.audit.graphql.GqlAuditRun;
import org.jahia.community.contentgovernance.audit.graphql.GqlSiteAudits;
import org.jahia.community.contentgovernance.audit.graphql.ManualRuns;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.SiteLayout;
import org.jahia.community.contentgovernance.settings.DrawerDisplay;
import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.services.content.JCRSessionFactory;
import org.jsoup.Jsoup;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Settings and Page quality audit queries. Read-only: nothing is written, so the CSRF guard does not apply.
 */
@GraphQLName("ContentGovernanceQuery")
@GraphQLDescription("Content Governance queries")
public class GqlContentGovernance {

    /** The longest text that checkText takes. */
    static final int MAX_TEXT = 200_000;
    static final String INVALID_PATH = "Invalid path";
    private static final Pattern LANGUAGE = Pattern.compile("^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8}){0,3}$");

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private AuditHistoryService audits;

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private CheckRegistry checks;

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private HtmlCheck pages;

    // graphql-java-annotations injects OSGi services into fields only (@GraphQLOsgiService)
    @SuppressWarnings("java:S6813")
    @Inject
    @GraphQLOsgiService
    private DrawerDisplay display;

    /**
     * What the drawer shows to every editor of the server, as the cfg of the module sets it. Any
     * signed-in user may ask: the drawer of every editor needs it.
     *
     * @return the elements that the cfg shows
     */
    @GraphQLField
    @GraphQLName("drawerDisplay")
    @GraphQLNonNull
    @GraphQLDescription("The elements of the drawer that the server shows to every editor")
    public GqlDrawerDisplay drawerDisplay() {
        SiteAccess.signedIn();
        return new GqlDrawerDisplay(display.shown());
    }

    /**
     * What a site uses of the module, as the drawer and the settings screen read it. Any signed-in
     * user who can read the site may ask: the drawer of every editor needs it.
     *
     * @param siteKey key of the site
     * @return the settings
     */
    @GraphQLField
    @GraphQLName("siteSettings")
    @GraphQLNonNull
    @GraphQLDescription("The tabs of the drawer a site switched off")
    public GqlSiteSettings siteSettings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        return GqlSiteSettings.of(SiteAccess.site(siteKey));
    }

    /**
     * The stored runs of a tab of the drawer on a page, for anyone who may open jContent on its site
     * and read it.
     *
     * @param path        path of the page or main resource
     * @param language    content language
     * @param tab         id of the tab; null for Page quality ({@code audit})
     * @param environment the request
     * @return the runs, newest first; the newest carries what it stores
     */
    @GraphQLField
    @GraphQLName("pageAudits")
    @GraphQLNonNull
    @GraphQLDescription("The last runs of a tab of the drawer on a page in a language, newest first; the newest carries "
            + "what it stores")
    public List<GqlAuditRun> pageAudits(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            @GraphQLName("tab") @GraphQLDescription("Id of the tab; Page quality (audit) when omitted") String tab,
            DataFetchingEnvironment environment) {
        return AuditFields.history(audits, path, language, tab == null ? AuditHistoryService.AUDIT_TAB : tab, environment);
    }

    /**
     * The stored runs of the rules on demand of a subdomain on a page, for anyone who may open jContent on its site
     * and read the page.
     *
     * @param path        path of the page or main resource
     * @param language    content language
     * @param subdomain   id of the subdomain, such as writing
     * @param environment the request
     * @return the runs, newest first; the newest carries its results
     */
    @GraphQLField
    @GraphQLName("manualRuns")
    @GraphQLNonNull
    @GraphQLDescription("The last runs of the rules on demand (trigger: manual) of a subdomain on a page in a language, "
            + "newest first; the newest carries its results as {results: [{ruleId, passed, values, findings}]}")
    public List<GqlAuditRun> manualRuns(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            @GraphQLName("subdomain") @GraphQLNonNull @GraphQLDescription("Id of the subdomain") String subdomain,
            DataFetchingEnvironment environment) {
        return ManualRuns.history(audits, path, language, subdomain, environment);
    }

    /**
     * The latest Page quality audit of every audited page of a site, for the site dashboard.
     *
     * @param siteKey     key of the site
     * @param language    content language
     * @param environment the request
     * @return the audited pages the caller may read, most issues first
     */
    @GraphQLField
    @GraphQLName("siteAudits")
    @GraphQLNonNull
    @GraphQLDescription("The latest Page quality audit of every audited page of a site in a language")
    public GqlSiteAudits siteAudits(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Content language") String language,
            DataFetchingEnvironment environment) {
        return AuditFields.site(audits, siteKey, language, environment);
    }

    /**
     * The rules of a site, as the checks apply them. Any signed-in user who can read the site may ask:
     * the drawer of every editor needs them.
     *
     * @param siteKey  key of the site
     * @param language the language of the texts of the rules, or null for English
     * @return every rule of the catalog, with what the site changed, in the order of the catalog
     */
    @GraphQLField
    @GraphQLName("contentRules")
    @GraphQLNonNull
    @GraphQLDescription("The rules of the catalog as a site applies them")
    public List<GqlContentRule> contentRules(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLDescription("The language of the texts of the rules, such as fr: the interface language of the editor. English when null, and for a rule with no text in that language") String language) {
        if (language != null && !LANGUAGE.matcher(language).matches()) {
            throw new DataFetchingException("Invalid language");
        }
        SiteAccess.site(siteKey);
        ContentRules rules = ContentRules.forSite(siteKey);
        SiteLayout layout = rules.hierarchy().layout();
        return rules.all().stream()
                .map(rule -> new GqlContentRule(rule, checks.status(rule), checks.blocking(rule), language,
                        layout.isNew(rule.id()), rules.switchedOn(rule)))
                .toList();
    }

    /**
     * The domains of a site, each with its subdomains: the hierarchy drawer › panel › domain › subdomain that
     * the rules name. Any signed-in user who can read the site may ask: the drawer of every editor needs it.
     *
     * @param siteKey    key of the site
     * @param language   the language of the labels and the help, or null for English
     * @param includeOff true to list the domains and the subdomains that are off for the site too
     * @return the domains of the site, in the order of its layout, with their subdomains
     */
    @GraphQLField
    @GraphQLName("contentDomains")
    @GraphQLNonNull
    @GraphQLDescription("The domains of the drawer on a site, in the order of its layout, each with its subdomains: the ones that are on, or all with includeOff")
    public List<GqlContentDomain> contentDomains(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLDescription("The language of the labels and the help, such as fr: the interface language of the editor. English when null, and for a text with no translation") String language,
            @GraphQLName("includeOff") @GraphQLDescription("True to list the domains and the subdomains that are off for the site too, as the screen of the layout does") Boolean includeOff) {
        if (language != null && !LANGUAGE.matcher(language).matches()) {
            throw new DataFetchingException("Invalid language");
        }
        SiteAccess.site(siteKey);
        ContentDomains hierarchy = ContentDomains.forSite(siteKey);
        boolean all = Boolean.TRUE.equals(includeOff);
        return (all ? hierarchy.allDomains() : hierarchy.domains()).stream()
                .map(domain -> new GqlContentDomain(domain, hierarchy, language, all))
                .toList();
    }

    /**
     * The layout that a site saved: how it arranges the domains, the subdomains and the rules of the system site.
     * Any signed-in user who can read the site may ask.
     *
     * @param siteKey key of the site
     * @return the layout, which is not saved when the site keeps the layout of the system site
     */
    @GraphQLField
    @GraphQLName("siteLayout")
    @GraphQLNonNull
    @GraphQLDescription("The layout that a site saved: the order and the place of its domains, subdomains and rules, and what it switches off")
    public GqlSiteLayout siteLayout(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        SiteAccess.site(siteKey);
        return new GqlSiteLayout(ContentDomains.forSite(siteKey).layout());
    }

    /**
     * Runs on a text every registered check of the scope, with the rules of the site of the path. Any
     * signed-in user who can read the path may ask.
     *
     * @param path     the path of the node that holds the text, which names the site
     * @param language the language of the text, such as en
     * @param text     a value, or the HTML of a page for the scope PAGE
     * @param scope    VALUE or PAGE; VALUE when null
     * @return the findings, in the order of the rules
     */
    @GraphQLField
    @GraphQLName("checkText")
    @GraphQLNonNull
    @GraphQLDescription("The findings of the registered checks on a text, with the rules of the site of the path")
    public List<GqlCheckFinding> checkText(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the node that holds the text") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Language of the text, such as en") String language,
            @GraphQLName("text") @GraphQLNonNull @GraphQLDescription("The text, or the HTML of a page for the scope PAGE") String text,
            @GraphQLName("scope") @GraphQLDescription("VALUE (the default) or PAGE") String scope) {
        CheckInput input = checkInput(path, language, text, scope);
        SiteAccess.site(siteOf(path));
        try {
            if (!JCRSessionFactory.getInstance().getCurrentUserSession("default").nodeExists(path)) {
                throw new DataFetchingException(SiteAccess.DENIED);
            }
        } catch (RepositoryException e) {
            throw new DataFetchingException("The path could not be read");
        }
        return checks.run(input, Set.of()).stream().map(checked -> new GqlCheckFinding(checked.finding())).toList();
    }

    /**
     * Renders a page or a content item as the current user, in the edit workspace, and runs every check
     * of the server on it: the result of each rule that ran, with the values that the tabs of the drawer
     * show, and the findings that {@code frame.check} gives for the same path. Any signed-in user who can
     * read the path may ask.
     *
     * @param path     the path of a page or of a content item
     * @param language the language of the content, such as en
     * @return the results, in the order of the rules
     */
    @GraphQLField
    @GraphQLName("checkPage")
    @GraphQLNonNull
    @GraphQLDescription("The result of each rule that every check of the server ran on a page or a content item, "
            + "rendered in the edit workspace: passed or failed, its values and its findings")
    public GqlPageCheck checkPage(
            @GraphQLName("path") @GraphQLNonNull @GraphQLDescription("Path of the page or of the content item") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Language of the content, such as en") String language) {
        if (!LANGUAGE.matcher(language).matches()) {
            throw new DataFetchingException("Invalid language");
        }
        SiteAccess.site(siteOf(path));
        Locale locale = Locales.of(language);
        HtmlCheck.Outcome outcome;
        try {
            outcome = pages.render(JCRSessionFactory.getInstance().getCurrentUserSession("default", locale), path);
        } catch (RepositoryException e) {
            throw new DataFetchingException("The path could not be read");
        }
        if (outcome instanceof HtmlCheck.NotFound) {
            throw new DataFetchingException(SiteAccess.DENIED);
        }
        if (!(outcome instanceof HtmlCheck.Rendered rendered)) {
            throw new DataFetchingException("Jahia could not render " + path);
        }
        return new GqlPageCheck(rendered.path(), pages.results(rendered, locale).stream().map(GqlCheckResult::new).toList());
    }

    /**
     * The key of the site of a path that a field reads a node at. The site gate reads the key from the
     * start of the path, so a path that climbs with {@code ..}, such as {@code /sites/a/../b/home}, would
     * pass the gate of one site and read a node of another: it is refused, as {@code frame.check} does.
     *
     * @param path the path that the caller gives
     * @return the key of the site, the segment after {@code /sites/}
     * @throws DataFetchingException when the path is not under {@code /sites/<siteKey>} or has a segment {@code ..}
     */
    static String siteOf(String path) {
        String siteKey = CheckInput.value(path, null, "").siteKey();
        if (siteKey == null || siteKey.isEmpty() || Arrays.asList(path.split("/")).contains("..")) {
            throw new DataFetchingException(INVALID_PATH);
        }
        return siteKey;
    }

    private static CheckInput checkInput(String path, String language, String text, String scope) {
        if (!LANGUAGE.matcher(language).matches()) {
            throw new DataFetchingException("Invalid language");
        }
        if (text.length() > MAX_TEXT) {
            throw new DataFetchingException("The text is longer than " + MAX_TEXT + " characters");
        }
        if (scope == null || ContentCheck.Scope.VALUE.name().equals(scope)) {
            return CheckInput.value(path, language, text);
        }
        if (ContentCheck.Scope.PAGE.name().equals(scope)) {
            return HtmlCheck.input(path, language, Jsoup.parse(text), Scope.PAGE);
        }
        throw new DataFetchingException("The scope is VALUE or PAGE");
    }
}
