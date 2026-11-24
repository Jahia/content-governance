package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The domains and the subdomains of a site, for the tests of the packages that read them:
 * quality › health, links; geo › seo, geo.citations; assist (panel chat) › answers; legacy (off by default) › old.
 */
public final class Hierarchies {

    private Hierarchies() {
    }

    /**
     * @param siteKey    the key of the site
     * @param layoutJson the JSON of the layout of the site, or null for none
     * @return the hierarchy of the site
     */
    public static ContentDomains site(String siteKey, String layoutJson) {
        Map<String, Map<String, Object>> domains = new LinkedHashMap<>();
        domains.put("quality", domain("quality", 10, "governance", true));
        domains.put("geo", domain("geo", 20, "governance", true));
        domains.put("assist", domain("assist", 30, "chat", true));
        domains.put("legacy", domain("legacy", 40, "governance", false));
        Map<String, Map<String, Object>> subdomains = new LinkedHashMap<>();
        subdomains.put("health", subdomain("health", "quality", 10));
        subdomains.put("links", subdomain("links", "quality", 20));
        subdomains.put("seo", subdomain("seo", "geo", 10));
        subdomains.put("geo.citations", subdomain("geo.citations", "geo", 20));
        subdomains.put("answers", subdomain("answers", "assist", 10));
        subdomains.put("old", subdomain("old", "legacy", 10));
        Map<Level, Map<String, Map<String, Object>>> values = new EnumMap<>(Level.class);
        values.put(Level.DOMAIN, domains);
        values.put(Level.SUBDOMAIN, subdomains);
        return new ContentDomains(siteKey, values, SiteLayout.parse(layoutJson));
    }

    private static Map<String, Object> domain(String id, int order, String panel, boolean enabled) {
        return Map.of(DomainValues.ID, id, DomainValues.ORDER, (long) order, DomainValues.PANEL, panel,
                DomainValues.ENABLED, enabled, DomainValues.SITES, List.of("*"));
    }

    private static Map<String, Object> subdomain(String id, String parent, int order) {
        return Map.of(DomainValues.ID, id, DomainValues.DOMAIN, parent, DomainValues.ORDER, (long) order,
                DomainValues.SITES, List.of("*"));
    }
}
