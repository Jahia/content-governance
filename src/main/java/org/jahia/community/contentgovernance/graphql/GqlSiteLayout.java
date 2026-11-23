package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.rules.SiteLayout;

import java.util.List;

/**
 * The layout that a site saved, as JSON and as the ids that it switches off.
 */
@GraphQLName("ContentGovernanceSiteLayout")
@GraphQLDescription("How a site arranges the domains, the subdomains and the rules of the system site")
public class GqlSiteLayout {

    private final SiteLayout layout;

    GqlSiteLayout(SiteLayout layout) {
        this.layout = layout;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the site saved a layout. False when it keeps the layout of the system site")
    public boolean isSaved() {
        return layout.saved();
    }

    @GraphQLField
    @GraphQLDescription("The JSON of the layout, {domains: [{id, subdomains: [{id, rules: [id]}]}], off: [id]}, or null when the site saved none")
    public String getJson() {
        return layout.saved() ? layout.toJson() : null;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The ids of the domains, the subdomains and the rules that the site switches off")
    public List<String> getOff() {
        return List.copyOf(layout.off());
    }
}
