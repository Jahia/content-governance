package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.rules.CheckType;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.community.contentgovernance.util.Locales;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Creates a site's first list of compliance terms when the module is enabled on it, so editors start from
 * a working example: {@code contents/compliance-glossaries}, which takes rules, holding one folder of rules
 * for the site's default language with one example rule of the evaluator {@code pattern}. Done once per
 * site (a flag on the module's settings node), and never on a site that already has that folder, so an
 * editor who deletes the example does not get it back. Each list is a folder, so that the permissions of
 * the folder of the legal team can differ from those of the brand team.
 *
 * <p>Listens to {@code j:installedModules} on site nodes in the edit workspace; events from another
 * cluster node are left to that node. The example is the module's own starting content, written with
 * a system session like the settings node beside it, and left unpublished: the scan reads the rules
 * in the edit workspace. Its text comes from the module's resource bundle in the site's default
 * language, English when the bundle has no translation. The titles of the two folders also take each
 * language of the bundle, so the Compliance section names a folder in the language of the editor.</p>
 *
 * <p>When the module starts, the seeded folders of an older version get the titles of the languages they
 * lack, as long as their title in the site's default language is still the one of the bundle.</p>
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class GlossarySeeder extends DefaultEventListener {

    static final String FOLDER = "compliance-glossaries";
    static final String SEEDED = "glossarySeeded";
    private static final String INSTALLED_MODULES = "j:installedModules";
    private static final String EDIT_WORKSPACE = "default";
    private static final String TITLE = "jcr:title";
    private static final String SITES = "/sites";
    private static final String CONTENTS = "/contents";
    /** The path of the glossary folder under the path of a site. */
    private static final String FOLDER_PATH = CONTENTS + "/" + FOLDER;
    private static final String CONTRIBUTE_MODE = "jmix:contributeMode";
    private static final String CONTRIBUTE_TYPES = "j:contributeTypes";
    private static final String CONTENT_FOLDER = "jnt:contentFolder";
    private static final String BUNDLE = "resources.content-governance";
    /** The languages of the resource bundle of the module: the languages of the interface of the drawer. */
    static final List<String> BUNDLE_LANGUAGES = List.of("en", "de", "es", "fr");
    private static final String SEED_FOLDER = "seed.folder";
    private static final String SEED_GLOSSARY = "seed.glossary";
    private static final String GLOSSARY_PREFIX = "glossary-";
    private static final Logger logger = LoggerFactory.getLogger(GlossarySeeder.class);

    public GlossarySeeder() {
        setWorkspace(EDIT_WORKSPACE);
    }

    @Activate
    protected void activate() {
        try {
            List<String> sites = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<List<String>>) GlossarySeeder::seededSites);
            sites.forEach(GlossarySeeder::translateTitles);
        } catch (RepositoryException e) {
            logger.warn("Could not translate the titles of the example compliance glossaries", e);
        }
    }

    /** The paths of the sites that hold the folder of the example. */
    private static List<String> seededSites(JCRSessionWrapper session) throws RepositoryException {
        List<String> out = new ArrayList<>();
        if (!session.nodeExists(SITES)) {
            return out;
        }
        for (JCRNodeWrapper site : session.getNode(SITES).getNodes()) {
            if (session.nodeExists(site.getPath() + FOLDER_PATH)) {
                out.add(site.getPath());
            }
        }
        return out;
    }

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String getPath() {
        return SITES;
    }

    @Override
    public String[] getNodeTypes() {
        return new String[]{"jnt:virtualsite"};
    }

    @Override
    public void onEvent(EventIterator events) {
        Set<String> sites = new LinkedHashSet<>();
        while (events.hasNext()) {
            Event event = events.nextEvent();
            try {
                String site = isExternal(event) ? null : sitePathOf(event.getPath());
                if (site != null) {
                    sites.add(site);
                }
            } catch (RepositoryException e) {
                logger.debug("unreadable event", e);
            }
        }
        sites.forEach(GlossarySeeder::seed);
    }

    /** {@code /sites/<key>} for a change of that site's {@code j:installedModules}, else null. */
    static String sitePathOf(String propertyPath) {
        String suffix = "/" + INSTALLED_MODULES;
        if (propertyPath == null || !propertyPath.endsWith(suffix)) {
            return null;
        }
        String site = propertyPath.substring(0, propertyPath.length() - suffix.length());
        return site.matches("/sites/[^/]+") ? site : null;
    }

    /** Seeds the site when it enables the module and was never seeded. Failures are logged, never thrown. */
    static void seed(String sitePath) {
        try {
            String language = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<String>) session -> toSeed(session, sitePath));
            if (language == null) {
                return;
            }
            JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE,
                    Locales.of(language), (JCRCallback<Void>) session -> {
                        create(session, sitePath, language);
                        return null;
                    });
            translateTitles(sitePath);
            logger.info("Created the example compliance glossary of {} ({})", sitePath, language);
        } catch (RepositoryException e) {
            logger.warn("Could not create the example compliance glossary of {}", sitePath, e);
        }
    }

    /** The site's default language when it should be seeded now, else null (flagging a site that has terms). */
    private static String toSeed(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        if (!session.nodeExists(sitePath + CONTENTS)
                || !(session.getNode(sitePath) instanceof JCRSiteNode site) || !SiteFeatures.moduleEnabled(site)) {
            return null;
        }
        JCRNodeWrapper store = site.hasNode(SiteFeatures.STORE_NODE) ? site.getNode(SiteFeatures.STORE_NODE) : null;
        if (store != null && store.hasProperty(SEEDED)) {
            return null;
        }
        if (hasTerms(session, sitePath)) {
            SiteFeatures.storeNode(site).setProperty(SEEDED, true);
            session.save();
            return null;
        }
        return site.getDefaultLanguage();
    }

    /**
     * True when the site keeps compliance terms: the folder of the example, a glossary of an older version,
     * or a rule of the subdomain {@value ContentRules#TERMS} anywhere under its contents.
     */
    private static boolean hasTerms(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        if (session.nodeExists(sitePath + FOLDER_PATH)) {
            return true;
        }
        String root = (sitePath + CONTENTS).replace("'", "''");
        return found(session, "SELECT * FROM [ccs:glossary] AS g WHERE ISDESCENDANTNODE(g, '" + root + "')")
                || found(session, "SELECT * FROM [" + ContentRules.TYPE + "] AS r WHERE ISDESCENDANTNODE(r, '" + root
                + "') AND r.[" + RuleValues.SUBDOMAIN + "] = '" + ContentRules.TERMS + "'");
    }

    private static boolean found(JCRSessionWrapper session, String sql) throws RepositoryException {
        Query query = session.getWorkspace().getQueryManager().createQuery(sql, Query.JCR_SQL2);
        query.setLimit(1);
        return query.execute().getNodes().hasNext();
    }

    /** The example's text in {@code language}, English when the module has no translation for it. */
    static ResourceBundle seedText(String language) {
        return ResourceBundle.getBundle(BUNDLE, Locales.of(language), GlossarySeeder.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    private static void create(JCRSessionWrapper session, String sitePath, String language) throws RepositoryException {
        ResourceBundle text = seedText(language);
        JCRNodeWrapper contents = session.getNode(sitePath + CONTENTS);
        JCRNodeWrapper folder = contents.hasNode(FOLDER) ? contents.getNode(FOLDER) : contents.addNode(FOLDER, CONTENT_FOLDER);
        if (!folder.hasProperty(TITLE)) {
            folder.setProperty(TITLE, text.getString(SEED_FOLDER));
        }
        takesRules(folder);
        JCRNodeWrapper list = folder.addNode(JCRContentUtils.findAvailableNodeName(folder, GLOSSARY_PREFIX + language),
                CONTENT_FOLDER);
        list.setProperty(TITLE, text.getString(SEED_GLOSSARY));
        list.setProperty("jcr:description", text.getString("seed.glossary.description"));
        takesRules(list);
        JCRNodeWrapper rule = list.addNode("example-term", ContentRules.TYPE);
        // The mixin of the check type declares the pattern and the other keys of a term.
        rule.addMixin(CheckType.PATTERN.mixin());
        rule.setProperty(RuleValues.RULE_ID, "compliance.example-term");
        rule.setProperty(RuleValues.EVALUATOR, ContentRule.PATTERN);
        rule.setProperty(RuleValues.CHECK_TYPE, CheckType.PATTERN.name());
        rule.setProperty(RuleValues.SUBDOMAIN, ContentRules.TERMS);
        rule.setProperty(RuleValues.SEVERITY, "serious");
        rule.setProperty(RuleValues.KIND, "replace");
        rule.setProperty(RuleValues.PATTERN, text.getString("seed.term"));
        rule.setProperty(RuleValues.MATCH_MODE, "term");
        rule.setProperty(RuleValues.REPLACEMENT, text.getString("seed.term.alternative"));
        rule.setProperty(RuleValues.RULE_LANGUAGE, language);
        // Internationalized: the session is in the language of the site, so the editors read it there.
        rule.setProperty(RuleValues.ADVICE, text.getString("seed.term.reason"));
        SiteFeatures.storeNode(session.getNode(sitePath)).setProperty(SEEDED, true);
        session.save();
    }

    /**
     * Gives the folder of the example and each of its lists {@code glossary-<language>} the title of each language
     * of the bundle that they lack, when their title in the site's default language is the one of the bundle.
     * A folder whose title an editor changed keeps its titles. Failures are logged, never thrown.
     */
    static void translateTitles(String sitePath) {
        try {
            String language = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, null,
                    (JCRCallback<String>) session -> session.getNode(sitePath) instanceof JCRSiteNode site
                            ? site.getDefaultLanguage() : null);
            if (language == null) {
                return;
            }
            Map<String, String> untouched = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null,
                    EDIT_WORKSPACE, Locales.of(language),
                    (JCRCallback<Map<String, String>>) session -> untouchedFolders(session, sitePath, seedText(language)));
            for (String other : BUNDLE_LANGUAGES) {
                if (untouched.isEmpty() || other.equals(language)) {
                    continue;
                }
                ResourceBundle text = seedText(other);
                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, EDIT_WORKSPACE, Locales.of(other),
                        (JCRCallback<Void>) session -> {
                            if (setMissingTitles(session, untouched, text)) {
                                session.save();
                            }
                            return null;
                        });
            }
        } catch (RepositoryException e) {
            logger.warn("Could not translate the titles of the example compliance glossary of {}", sitePath, e);
        }
    }

    /**
     * @return the path of each seeded folder whose title in the language of the session is the one of the bundle,
     *     with the key of its title in the bundle
     */
    private static Map<String, String> untouchedFolders(JCRSessionWrapper session, String sitePath, ResourceBundle text)
            throws RepositoryException {
        Map<String, String> out = new LinkedHashMap<>();
        String path = sitePath + FOLDER_PATH;
        if (!session.nodeExists(path)) {
            return out;
        }
        JCRNodeWrapper folder = session.getNode(path);
        if (hasTitle(folder, text.getString(SEED_FOLDER))) {
            out.put(folder.getPath(), SEED_FOLDER);
        }
        for (JCRNodeWrapper list : folder.getNodes()) {
            if (list.getName().startsWith(GLOSSARY_PREFIX) && hasTitle(list, text.getString(SEED_GLOSSARY))) {
                out.put(list.getPath(), SEED_GLOSSARY);
            }
        }
        return out;
    }

    private static boolean hasTitle(JCRNodeWrapper node, String title) throws RepositoryException {
        return node.hasProperty(TITLE) && title.equals(node.getProperty(TITLE).getString());
    }

    /** Sets the title of each folder that has none in the language of the session. True when it set one. */
    private static boolean setMissingTitles(JCRSessionWrapper session, Map<String, String> folders, ResourceBundle text)
            throws RepositoryException {
        boolean changed = false;
        for (Map.Entry<String, String> folder : folders.entrySet()) {
            JCRNodeWrapper node = session.getNode(folder.getKey());
            if (!node.hasProperty(TITLE)) {
                node.setProperty(TITLE, text.getString(folder.getValue()));
                changed = true;
            }
        }
        return changed;
    }

    /** jContent then offers only rules in the folder. */
    private static void takesRules(JCRNodeWrapper folder) throws RepositoryException {
        if (!folder.isNodeType(CONTRIBUTE_MODE)) {
            folder.addMixin(CONTRIBUTE_MODE);
            folder.setProperty(CONTRIBUTE_TYPES, new String[]{ContentRules.TYPE});
        }
    }
}
