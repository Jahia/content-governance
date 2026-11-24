package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A domain or a subdomain as one site sees it: the values of its node, with the shipped values for the
 * properties that the node lacks.
 *
 * @param level  a domain or a subdomain
 * @param id     the id, such as {@code quality} or {@code seo}
 * @param parent the domain of a subdomain, or null for a domain
 * @param panel  the panel of a domain, such as {@value DomainValues#GOVERNANCE}, or null for a subdomain
 * @param order  the place among its siblings: a lower order comes first
 * @param sites  the keys of the sites that it applies to, or {@value RuleValues#ALL_SITES}
 * @param values every value, keyed as {@link DomainValues} keys them
 */
public record Domain(Level level, String id, String parent, String panel, int order, List<String> sites,
                     Map<String, Object> values) {

    public Domain {
        sites = List.copyOf(sites);
        values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    /**
     * @param level  a domain or a subdomain
     * @param values the values, with an id
     * @return the domain or the subdomain that the values describe
     */
    static Domain of(Level level, Map<String, Object> values) {
        Map<String, Object> all = DomainValues.withDefaults(level, values);
        Object order = all.get(DomainValues.ORDER);
        return new Domain(level, String.valueOf(all.get(DomainValues.ID)),
                level == Level.SUBDOMAIN ? string(all, DomainValues.DOMAIN) : null,
                level == Level.DOMAIN ? string(all, DomainValues.PANEL) : null,
                order instanceof Number number ? (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, number.longValue())) : 0,
                RuleValues.strings(all, DomainValues.SITES), all);
    }

    /**
     * @param parentId the domain of a subdomain on the site, or null for a domain
     * @param place    the order among its siblings on the site
     * @return this domain or subdomain at that place
     */
    Domain at(String parentId, int place) {
        return new Domain(level, id, parentId, panel, place, sites, values);
    }

    /** The ids of the rules of other subdomains that a subdomain counts, such as the rules of the GEO score. */
    public List<String> counts() {
        return RuleValues.strings(values, DomainValues.COUNTS);
    }

    /** True when the domain or the subdomain is on by default for the site, or for every site with a null key. */
    public boolean includes(String siteKey) {
        return siteKey == null || RuleValues.includes(sites, siteKey);
    }

    /**
     * A text in a language, or in English when there is none in that language.
     *
     * @param key      {@link DomainValues#LABEL} or {@link DomainValues#HELP}
     * @param language a language such as {@code fr}, or null for English
     * @return the text, or null
     */
    public String text(String key, String language) {
        if (language != null && !"en".equals(language)
                && values.get(DomainValues.TRANSLATIONS) instanceof Map<?, ?> translations
                && translations.get(language) instanceof Map<?, ?> texts
                && texts.get(key) instanceof String text && !text.isBlank()) {
            return text;
        }
        Object english = values.get(key);
        return english == null || english.toString().isBlank() ? null : english.toString();
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}
