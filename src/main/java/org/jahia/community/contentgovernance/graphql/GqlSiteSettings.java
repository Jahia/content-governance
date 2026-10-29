package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.ai.LlmConfigService;
import org.jahia.community.contentgovernance.settings.Features;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a site uses of the module: its sections and tabs, switched on or off, and the AI status the
 * drawer greys AI features out with. Built once per field, never cached.
 */
@GraphQLName("ContentGovernanceSiteSettings")
@GraphQLDescription("Sections and tabs of Content Governance on a site")
public class GqlSiteSettings {

    private final String siteKey;
    private final boolean moduleEnabled;
    private final boolean canEdit;
    private final List<String> disabled;
    private final List<GqlSection> sections;
    private final GqlAiStatus ai;

    private GqlSiteSettings(String siteKey, boolean moduleEnabled, boolean canEdit, List<String> disabled,
                            List<GqlSection> sections, GqlAiStatus ai) {
        this.siteKey = siteKey;
        this.moduleEnabled = moduleEnabled;
        this.canEdit = canEdit;
        this.disabled = disabled;
        this.sections = sections;
        this.ai = ai;
    }

    /**
     * @param site the site, from the caller's session
     * @param llm  the shared model configuration
     * @return the settings of the site
     */
    static GqlSiteSettings of(JCRSiteNode site, LlmConfigService llm) {
        boolean enabled = SiteFeatures.moduleEnabled(site);
        Set<String> off;
        try {
            off = SiteFeatures.read(site);
        } catch (RepositoryException e) {
            throw new DataFetchingException("The settings could not be read");
        }
        List<GqlSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<String>> section : Features.SECTIONS.entrySet()) {
            List<GqlTab> tabs = new ArrayList<>();
            for (String tab : section.getValue()) {
                tabs.add(new GqlTab(tab, !off.contains(section.getKey() + "." + tab)));
            }
            sections.add(new GqlSection(section.getKey(), enabled && !off.contains(section.getKey()), tabs));
        }
        boolean canEdit = enabled && site.hasPermission(SiteFeatures.SETTINGS_PERMISSION);
        return new GqlSiteSettings(site.getSiteKey(), enabled, canEdit, new ArrayList<>(off), sections,
                new GqlAiStatus(llm.isEnabled(), llm.providerName(), llm.model(), llm.supportsImages()));
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
    @GraphQLDescription("Section and tab ids the site switched off")
    public List<String> getDisabled() {
        return disabled;
    }

    /** @return every section, in drawer order */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Every section of the drawer, in order, with its tabs")
    public List<GqlSection> getSections() {
        return sections;
    }

    /** @return the AI status */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Whether a model is configured for the AI features")
    public GqlAiStatus getAi() {
        return ai;
    }

    /** One section of the drawer. */
    @GraphQLName("ContentGovernanceSection")
    @GraphQLDescription("A section of the Content Governance drawer")
    public static class GqlSection {
        private final String id;
        private final boolean enabled;
        private final List<GqlTab> tabs;

        GqlSection(String id, boolean enabled, List<GqlTab> tabs) {
            this.id = id;
            this.enabled = enabled;
            this.tabs = tabs;
        }

        /** @return the section id: audit, geo or compliance */
        @GraphQLField
        @GraphQLNonNull
        public String getId() {
            return id;
        }

        /** @return whether the site uses the section */
        @GraphQLField
        public boolean isEnabled() {
            return enabled;
        }

        /** @return the tabs of the section */
        @GraphQLField
        @GraphQLNonNull
        public List<GqlTab> getTabs() {
            return tabs;
        }
    }

    /** One tab of a section. */
    @GraphQLName("ContentGovernanceTab")
    @GraphQLDescription("A tab of a Content Governance section")
    public static class GqlTab {
        private final String id;
        private final boolean enabled;

        GqlTab(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }

        /** @return the tab id within its section */
        @GraphQLField
        @GraphQLNonNull
        public String getId() {
            return id;
        }

        /** @return whether the site uses the tab (its section may still be off) */
        @GraphQLField
        public boolean isEnabled() {
            return enabled;
        }
    }

    /** Whether AI can be used, and with what. Never the key. */
    @GraphQLName("ContentGovernanceAiStatus")
    @GraphQLDescription("The model shared by the AI features of Content Governance")
    public static class GqlAiStatus {
        private final boolean configured;
        private final String provider;
        private final String model;
        private final boolean images;

        GqlAiStatus(boolean configured, String provider, String model, boolean images) {
            this.configured = configured;
            this.provider = provider;
            this.model = model;
            this.images = images;
        }

        /** @return whether a provider, a model and a key are configured */
        @GraphQLField
        public boolean isConfigured() {
            return configured;
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

        /** @return whether the model reads images (alt text assist) */
        @GraphQLField
        public boolean isImages() {
            return images;
        }
    }
}
