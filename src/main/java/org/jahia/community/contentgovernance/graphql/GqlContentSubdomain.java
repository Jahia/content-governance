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
 * A subdomain of the drawer as a site sees it: a tab of a domain, such as SEO. A rule names its subdomains.
 */
@GraphQLName("ContentGovernanceSubdomain")
@GraphQLDescription("A subdomain of the drawer of Content Governance: a tab of a domain, which the rules name")
public class GqlContentSubdomain {

    private final Domain subdomain;
    private final ContentDomains hierarchy;
    private final String language;

    GqlContentSubdomain(Domain subdomain, ContentDomains hierarchy, String language) {
        this.subdomain = subdomain;
        this.hierarchy = hierarchy;
        this.language = language;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the subdomain, such as seo, which the subdomain of a rule names")
    public String getId() {
        return subdomain.id();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the domain of the subdomain on the site")
    public String getDomain() {
        return subdomain.parent();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The name of the tab, in the language of the call or in English")
    public String getLabel() {
        String label = subdomain.text(DomainValues.LABEL, language);
        return label == null ? subdomain.id() : label;
    }

    @GraphQLField
    @GraphQLDescription("What the subdomain checks, in the language of the call or in English, or null")
    public String getHelp() {
        return subdomain.text(DomainValues.HELP, language);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The place among the subdomains of the domain on the site: a lower order comes first")
    public int getOrder() {
        return subdomain.order();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The keys of the sites where the subdomain is on by default, or * for all sites")
    public List<String> getSites() {
        return subdomain.sites();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The ids of the rules of other subdomains that the subdomain counts, such as the rules of the GEO score")
    public List<String> getCounts() {
        return subdomain.counts();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the subdomain and its domain are on for the site")
    public boolean isOn() {
        return hierarchy.isOn(subdomain.id());
    }

    @GraphQLField
    @GraphQLName("isNew")
    @GraphQLNonNull
    @GraphQLDescription("True when the site saved a layout that names the subdomain neither in an array nor in off, such as an item that a module shipped after the save")
    public boolean isNew() {
        return hierarchy.isNew(subdomain.id());
    }
}
