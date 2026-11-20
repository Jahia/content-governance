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
@GraphQLDescription("A folder of rules applied by a compliance scan, such as the terms of the legal team")
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

    /** @return identifier of the folder, or null when the user cannot read it */
    @GraphQLField
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
    @GraphQLDescription("Number of rules of the folder applied")
    public int getTermCount() {
        return glossary.termCount();
    }
}
