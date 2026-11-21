package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a site uses of the module: the ids of the tabs it switched off. The drawer's registry lists
 * the tabs, so a tab that no id here names is on. Built once per field, never cached.
 */
@GraphQLName("ContentGovernanceSiteSettings")
@GraphQLDescription("The tabs of the Content Governance drawer that a site switched off")
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
        Set<String> off;
        try {
            off = SiteFeatures.disabled(site.getPath());
        } catch (RepositoryException e) {
            throw new DataFetchingException("The settings could not be read");
        }
        boolean canEdit = enabled && site.hasPermission(SiteFeatures.SETTINGS_PERMISSION);
        return new GqlSiteSettings(site.getSiteKey(), enabled, canEdit, new ArrayList<>(off));
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
    @GraphQLDescription("Whether the caller may change these settings (site-admin on the site)")
    public boolean isCanEdit() {
        return canEdit;
    }

    /** @return the ids switched off */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Ids of the tabs of the drawer (geo) and of their parts (geo.citations) that the site switched off; "
            + "any other tab is on")
    public List<String> getDisabled() {
        return disabled;
    }
}
