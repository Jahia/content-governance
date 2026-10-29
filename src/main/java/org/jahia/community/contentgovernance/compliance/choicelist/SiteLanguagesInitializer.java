package org.jahia.community.contentgovernance.compliance.choicelist;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code choicelist[complianceSiteLanguages]}: every language of the site, active on the live site or
 * not. Jahia's {@code siteLanguage} initializer offers the live-active languages only, so a glossary
 * could not be written for a language being prepared, which is when its content needs checking.
 */
@Component(service = ModuleChoiceListInitializer.class)
public class SiteLanguagesInitializer implements ModuleChoiceListInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SiteLanguagesInitializer.class);

    static final String KEY = "complianceSiteLanguages";

    private String key = KEY;

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition definition, String param,
                                                     List<ChoiceListValue> values, Locale locale,
                                                     Map<String, Object> context) {
        JCRSiteNode site = site(context);
        if (site == null) {
            return new ArrayList<>();
        }
        Set<String> languages = site.getLanguages();
        List<ChoiceListValue> choices = new ArrayList<>(languages.size());
        for (String language : languages) {
            ChoiceListValue value = new ChoiceListValue();
            value.setStringValue(language);
            value.setDisplayName(LanguageCodeConverters.languageCodeToLocale(language)
                    .getDisplayName(locale != null ? locale : Locale.ENGLISH));
            choices.add(value);
        }
        Collections.sort(choices);
        return choices;
    }

    private static JCRSiteNode site(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        // The edited node, else its parent when creating; a failure on one still tries the other.
        for (String entry : new String[]{"contextNode", "contextParent"}) {
            try {
                if (context.get(entry) instanceof JCRNodeWrapper node && node.getResolveSite() != null) {
                    return node.getResolveSite();
                }
            } catch (RepositoryException e) {
                logger.warn("Could not resolve the site of a glossary from its {}: {}", entry, e.toString());
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
