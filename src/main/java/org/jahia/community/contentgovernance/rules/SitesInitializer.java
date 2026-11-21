package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * {@code choicelist[contentGovernanceSites]}: "All sites", saved as {@value RuleValues#ALL_SITES}, then each
 * site that the editor can read, by title. The property {@code sites} of a rule, a domain and a subdomain
 * takes these values.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class SitesInitializer implements ModuleChoiceListInitializer {

    static final String KEY = "contentGovernanceSites";
    private static final String BUNDLE = "resources.content-governance";
    private static final String SYSTEM_SITE = "systemsite";
    private static final Logger logger = LoggerFactory.getLogger(SitesInitializer.class);

    private String key = KEY;

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition definition, String param,
                                                     List<ChoiceListValue> values, Locale locale,
                                                     Map<String, Object> context) {
        List<ChoiceListValue> choices = new ArrayList<>();
        choices.add(choice(RuleValues.ALL_SITES, allSites(locale)));
        JCRSessionWrapper session = session(context);
        if (session == null) {
            return choices;
        }
        try {
            List<ChoiceListValue> sites = new ArrayList<>();
            for (JCRSiteNode site : JahiaSitesService.getInstance().getSitesNodeList(session)) {
                if (!SYSTEM_SITE.equals(site.getSiteKey())) {
                    String title = site.getTitle();
                    sites.add(choice(site.getSiteKey(), title == null || title.isBlank() ? site.getSiteKey()
                            : title + " (" + site.getSiteKey() + ")"));
                }
            }
            sites.sort(Comparator.comparing(ChoiceListValue::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            choices.addAll(sites);
        } catch (RepositoryException e) {
            logger.warn("Could not list the sites for a content rule: {}", e.toString());
        }
        return choices;
    }

    private static ChoiceListValue choice(String value, String displayName) {
        ChoiceListValue choice = new ChoiceListValue();
        choice.setStringValue(value);
        choice.setDisplayName(displayName);
        return choice;
    }

    private static String allSites(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale != null ? locale : Locale.ENGLISH,
                    SitesInitializer.class.getClassLoader()).getString("contentGovernanceSites.all");
        } catch (MissingResourceException e) {
            return "All sites";
        }
    }

    /** The session of the edited node, else of its parent when creating. */
    private static JCRSessionWrapper session(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        for (String entry : new String[]{"contextNode", "contextParent"}) {
            if (context.get(entry) instanceof JCRNodeWrapper node) {
                try {
                    return node.getSession();
                } catch (RepositoryException e) {
                    logger.debug("No session for the {} of a content rule", entry, e);
                }
            }
        }
        return null;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
