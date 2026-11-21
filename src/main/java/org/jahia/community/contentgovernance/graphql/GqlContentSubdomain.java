package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
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
    private final String language;

    GqlContentSubdomain(Domain subdomain, String language) {
        this.subdomain = subdomain;
        this.language = language;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the subdomain, such as seo, which the subdomains of a rule name")
    public String getId() {
        return subdomain.id();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id of the domain of the subdomain")
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
    @GraphQLDescription("The place among the subdomains of the domain: a lower order comes first")
    public int getOrder() {
        return subdomain.order();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The keys of the sites that the subdomain applies to, or * for all sites")
    public List<String> getSites() {
        return subdomain.sites();
    }
}
