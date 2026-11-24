package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.rules.ContentDomains;
import org.jahia.community.contentgovernance.rules.Domain;
import org.jahia.community.contentgovernance.rules.DomainValues;

import java.util.List;

/**
 * A domain of the drawer as a site sees it, with its subdomains: a main tab of a panel, such as Page quality.
 */
@GraphQLName("ContentGovernanceDomain")
@GraphQLDescription("A domain of the drawer of Content Governance: a main tab of a panel, which holds subdomains")
public class GqlContentDomain {

    private final Domain domain;
    private final ContentDomains hierarchy;
    private final String language;
    private final boolean includeOff;

    /**
     * @param domain     the domain
     * @param hierarchy  the domains and the subdomains of the site, which give the subdomains of the domain
     * @param language   the language of the texts, or null for English
     * @param includeOff true to list the subdomains that are off for the site too
     */
    GqlContentDomain(Domain domain, ContentDomains hierarchy, String language, boolean includeOff) {
        this.domain = domain;
        this.hierarchy = hierarchy;
        this.language = language;
        this.includeOff = includeOff;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the domain, such as quality")
    public String getId() {
        return domain.id();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The name of the tab, in the language of the call or in English")
    public String getLabel() {
        String label = domain.text(DomainValues.LABEL, language);
        return label == null ? domain.id() : label;
    }

    @GraphQLField
    @GraphQLDescription("What the domain checks, in the language of the call or in English, or null")
    public String getHelp() {
        return domain.text(DomainValues.HELP, language);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The place among the domains of the panel on the site: a lower order comes first")
    public int getOrder() {
        return domain.order();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The panel of the drawer that shows the domain, such as governance")
    public String getPanel() {
        return domain.panel();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The keys of the sites where the domain is on by default, or * for all sites")
    public List<String> getSites() {
        return domain.sites();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the domain is on for the site")
    public boolean isOn() {
        return hierarchy.isOn(domain.id());
    }

    @GraphQLField
    @GraphQLName("isNew")
    @GraphQLNonNull
    @GraphQLDescription("True when the site saved a layout that names the domain neither in an array nor in off, such as an item that a module shipped after the save")
    public boolean isNew() {
        return hierarchy.isNew(domain.id());
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The subdomains of the domain on the site, by order: the ones that are on, or all with includeOff")
    public List<GqlContentSubdomain> getSubdomains() {
        return (includeOff ? hierarchy.allSubdomainsOf(domain.id()) : hierarchy.subdomainsOf(domain.id())).stream()
                .map(subdomain -> new GqlContentSubdomain(subdomain, hierarchy, language))
                .toList();
    }
}
