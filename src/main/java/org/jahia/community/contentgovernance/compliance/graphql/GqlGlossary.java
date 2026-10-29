package org.jahia.community.contentgovernance.compliance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.compliance.model.Glossary;

/**
 * GraphQL view of a {@link Glossary}.
 */
@GraphQLName("ComplianceGlossary")
@GraphQLDescription("A glossary applied by a compliance scan")
public class GqlGlossary {

    private final Glossary glossary;

    /** @param glossary the glossary to expose */
    public GqlGlossary(Glossary glossary) {
        this.glossary = glossary;
    }

    /** @return path of the glossary */
    @GraphQLField
    @GraphQLNonNull
    public String getPath() {
        return glossary.path();
    }

    /** @return identifier of the glossary */
    @GraphQLField
    @GraphQLNonNull
    public String getUuid() {
        return glossary.uuid();
    }

    /** @return display name of the glossary */
    @GraphQLField
    public String getTitle() {
        return glossary.title();
    }

    /** @return number of terms applied */
    @GraphQLField
    @GraphQLDescription("Number of terms of the glossary applied")
    public int getTermCount() {
        return glossary.termCount();
    }
}
