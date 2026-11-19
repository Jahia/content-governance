package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiService;
import org.jahia.community.contentgovernance.ai.AiStatus;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a site uses of the module: the ids of the tabs it switched off, and the AI status the drawer
 * greys AI features out with. The drawer's registry lists the tabs, so a tab that no id here names
 * is on. Built once per field, never cached.
 */
@GraphQLName("ContentGovernanceSiteSettings")
@GraphQLDescription("The tabs of the Content Governance drawer that a site switched off")
public class GqlSiteSettings {

    private final String siteKey;
    private final boolean moduleEnabled;
    private final boolean canEdit;
    private final List<String> disabled;
    private final GqlAiStatus ai;

    private GqlSiteSettings(String siteKey, boolean moduleEnabled, boolean canEdit, List<String> disabled, GqlAiStatus ai) {
        this.siteKey = siteKey;
        this.moduleEnabled = moduleEnabled;
        this.canEdit = canEdit;
        this.disabled = disabled;
        this.ai = ai;
    }

    /**
     * @param site the site, from the caller's session
     * @param ai   the AI features, served by genai-connector
     * @return the settings of the site
     */
    static GqlSiteSettings of(JCRSiteNode site, AiService ai) {
        boolean enabled = SiteFeatures.moduleEnabled(site);
        Set<String> off;
        try {
            off = SiteFeatures.disabled(site.getPath());
        } catch (RepositoryException e) {
            throw new DataFetchingException("The settings could not be read");
        }
        boolean canEdit = enabled && site.hasPermission(SiteFeatures.SETTINGS_PERMISSION);
        // Every page task runs on the same tier, so the review stands for all of them.
        AiStatus status = ai.status(AiFeature.REVIEW, site.getSiteKey());
        return new GqlSiteSettings(site.getSiteKey(), enabled, canEdit, new ArrayList<>(off),
                new GqlAiStatus(status.ready(), status.reason(), status.provider(), status.model(), status.vision()));
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

    /** @return the AI status */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Whether genai-connector serves a model to the caller for the AI features of this site")
    public GqlAiStatus getAi() {
        return ai;
    }

    /** Whether AI can be used by the caller on the site, and with what. */
    @GraphQLName("ContentGovernanceAiStatus")
    @GraphQLDescription("The model that genai-connector serves to the AI features of Content Governance")
    public static class GqlAiStatus {
        private final boolean configured;
        private final String reason;
        private final String provider;
        private final String model;
        private final boolean images;

        GqlAiStatus(boolean configured, String reason, String provider, String model, boolean images) {
            this.configured = configured;
            this.reason = reason;
            this.provider = provider;
            this.model = model;
            this.images = images;
        }

        /** @return whether a model answers the caller on this site */
        @GraphQLField
        @GraphQLDescription("Whether genai-connector serves a model and the caller holds genaiAccess on the site")
        public boolean isConfigured() {
            return configured;
        }

        /** @return why not: absent, notConfigured or noAccess; empty when configured */
        @GraphQLField
        @GraphQLDescription("Why the AI features are unavailable: absent (no genai-connector), notConfigured "
                + "(no model on the balanced tier) or noAccess (no genaiAccess on the site); empty when available")
        public String getReason() {
            return reason;
        }

        /** @return the provider name, empty when not configured */
        @GraphQLField
        public String getProvider() {
            return provider;
        }

        /** @return the model, empty when not configured */
        @GraphQLField
        public String getModel() {
            return model;
        }

        /** @return whether the vision tier is served (alt text assist) */
        @GraphQLField
        @GraphQLDescription("Whether genai-connector serves the vision tier, which reads the pictures of the alt text assist")
        public boolean isImages() {
            return images;
        }
    }
}
