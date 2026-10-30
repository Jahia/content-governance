package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.DefaultEventListener;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.query.Query;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Creates a site's first compliance glossary when the module is enabled on it, so editors start from
 * a working example: {@code contents/compliance-glossaries}, restricted to glossaries, holding one
 * glossary in the site's default language with one example term. Done once per site (a flag on the module's settings node), and never
 * on a site that already has a glossary, so an editor who deletes the example does not get it back.
 *
 * <p>Listens to {@code j:installedModules} on site nodes in the edit workspace; events from another
 * cluster node are left to that node. The example is the module's own starting content, written with
 * a system session like the settings node beside it, and left unpublished: the scan reads glossaries
 * in the edit workspace. Its text comes from the module's resource bundle in the site's default
 * language, English when the bundle has no translation.</p>
 */
@Component(service = DefaultEventListener.class, immediate = true)
public class GlossarySeeder extends DefaultEventListener {

    static final String FOLDER = "compliance-glossaries";
    static final String SEEDED = "glossarySeeded";
    private static final String INSTALLED_MODULES = "j:installedModules";
    private static final String EDIT_WORKSPACE = "default";
    private static final String TITLE = "jcr:title";
    private static final String CONTENTS = "/contents";
    private static final String CONTRIBUTE_MODE = "jmix:contributeMode";
    private static final String BUNDLE = "resources.content-governance";
    private static final Logger logger = LoggerFactory.getLogger(GlossarySeeder.class);

    public GlossarySeeder() {
        setWorkspace(EDIT_WORKSPACE);
    }

    @Override
    public int getEventTypes() {
        return Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED;
    }

    @Override
    public String getPath() {
        return "/sites";
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
                    Locale.forLanguageTag(language), (JCRCallback<Void>) session -> {
                        create(session, sitePath, language);
                        return null;
                    });
            logger.info("Created the example compliance glossary of {} ({})", sitePath, language);
        } catch (RepositoryException e) {
            logger.warn("Could not create the example compliance glossary of {}", sitePath, e);
        }
    }

    /** The site's default language when it should be seeded now, else null (flagging a site that has glossaries). */
    private static String toSeed(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        if (!session.nodeExists(sitePath + CONTENTS)
                || !(session.getNode(sitePath) instanceof JCRSiteNode site) || !SiteFeatures.moduleEnabled(site)) {
            return null;
        }
        JCRNodeWrapper store = site.hasNode(SiteFeatures.STORE_NODE) ? site.getNode(SiteFeatures.STORE_NODE) : null;
        if (store != null && store.hasProperty(SEEDED)) {
            return null;
        }
        if (hasGlossary(session, sitePath)) {
            SiteFeatures.storeNode(site).setProperty(SEEDED, true);
            session.save();
            return null;
        }
        return site.getDefaultLanguage();
    }

    private static boolean hasGlossary(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        String root = (sitePath + CONTENTS).replace("'", "''");
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + GlossaryReader.GLOSSARY_TYPE
                + "] AS g WHERE ISDESCENDANTNODE(g, '" + root + "')", Query.JCR_SQL2);
        query.setLimit(1);
        return query.execute().getNodes().hasNext();
    }

    /** The example's text in {@code language}, English when the module has no translation for it. */
    static ResourceBundle seedText(String language) {
        return ResourceBundle.getBundle(BUNDLE, Locale.forLanguageTag(language), GlossarySeeder.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    private static void create(JCRSessionWrapper session, String sitePath, String language) throws RepositoryException {
        ResourceBundle text = seedText(language);
        JCRNodeWrapper contents = session.getNode(sitePath + CONTENTS);
        JCRNodeWrapper folder = contents.hasNode(FOLDER) ? contents.getNode(FOLDER) : contents.addNode(FOLDER, "jnt:contentFolder");
        if (!folder.hasProperty(TITLE)) {
            folder.setProperty(TITLE, text.getString("seed.folder"));
        }
        if (!folder.isNodeType(CONTRIBUTE_MODE)) {
            // jContent then offers only glossaries in this folder; a glossary itself only takes terms.
            folder.addMixin(CONTRIBUTE_MODE);
            folder.setProperty("j:contributeTypes", new String[]{GlossaryReader.GLOSSARY_TYPE});
        }
        JCRNodeWrapper glossary = folder.addNode(JCRContentUtils.findAvailableNodeName(folder, "glossary-" + language),
                GlossaryReader.GLOSSARY_TYPE);
        glossary.setProperty(TITLE, text.getString("seed.glossary"));
        glossary.setProperty("language", language);
        glossary.setProperty("description", text.getString("seed.glossary.description"));
        JCRNodeWrapper term = glossary.addNode("example-term", GlossaryReader.TERM_TYPE);
        term.setProperty("term", text.getString("seed.term"));
        term.setProperty("reason", text.getString("seed.term.reason"));
        term.setProperty("alternative", text.getString("seed.term.alternative"));
        SiteFeatures.storeNode(session.getNode(sitePath)).setProperty(SEEDED, true);
        session.save();
    }
}
