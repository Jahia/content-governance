package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.decorator.JCRSiteNode;

import java.util.ArrayList;
import java.util.List;

/**
 * What a site uses of the module: the ids that are off on the site. The drawer's registry lists the panels and the
 * tree of the system site the domains and the subdomains, so an id that is not listed here is on. Built once per
 * field, never cached.
 */
@GraphQLName("ContentGovernanceSiteSettings")
@GraphQLDescription("The panels, domains and subdomains of the Content Governance drawer that are off on a site")
public class GqlSiteSettings {

    private final String siteKey;
    private final boolean moduleEnabled;
    private final boolean canEdit;
    private final List<String> disabled;

    private GqlSiteSettings(String siteKey, boolean moduleEnabled, boolean canEdit, List<String> disabled) {
        this.siteKey = siteKey;
        this.moduleEnabled = moduleEnabled;
        this.canEdit = canEdit;
        this.disabled = disabled;
    }

    /**
     * @param site the site, from the caller's session
     * @return the settings of the site
     */
    static GqlSiteSettings of(JCRSiteNode site) {
        boolean enabled = SiteFeatures.moduleEnabled(site);
        List<String> off = new ArrayList<>(SiteFeatures.off(ContentDomains.forSite(site.getSiteKey())));
        boolean canEdit = enabled && site.hasPermission(SiteFeatures.SETTINGS_PERMISSION);
        return new GqlSiteSettings(site.getSiteKey(), enabled, canEdit, off);
    }

    /** @return the key of the site */
    @GraphQLField
    @GraphQLNonNull
    public String getSiteKey() {
        return siteKey;
    }

    /** @return whether the site enables the module */
    @GraphQLField
    @GraphQLDescription("Whether the site enables the module, directly or through a module it enables")
    public boolean isModuleEnabled() {
        return moduleEnabled;
    }

    /** @return whether the caller may change the settings */
    @GraphQLField
    @GraphQLDescription("Whether the caller may change the layout of the site (site-admin on the site)")
    public boolean isCanEdit() {
        return canEdit;
    }

    /** @return the ids switched off */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Ids that are off on the site: the panels (chat), domains (geo) and subdomains (geo.citations) "
            + "that its layout switches off, and the domains and subdomains that are off by default or under an item that is off")
    public List<String> getDisabled() {
        return disabled;
    }
}
