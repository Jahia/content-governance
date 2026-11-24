package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.contentgovernance.settings.DrawerDisplay;

/**
 * What the drawer shows to every editor of the server, as the cfg of the module sets it.
 */
@GraphQLName("ContentGovernanceDrawerDisplay")
@GraphQLDescription("The elements of the Content Governance drawer that the server shows to every editor")
public class GqlDrawerDisplay {

    private final DrawerDisplay.Shown shown;

    GqlDrawerDisplay(DrawerDisplay.Shown shown) {
        this.shown = shown;
    }

    /** @return whether the drawer shows "Fix first" */
    @GraphQLField
    @GraphQLDescription("Whether the drawer shows the Fix first list (SHOW_FIX_FIRST)")
    public boolean isFixFirst() {
        return shown.fixFirst();
    }

    /** @return whether Page quality shows its preview button */
    @GraphQLField
    @GraphQLDescription("Whether Page quality shows its Show preview button (SHOW_PREVIEW_BUTTON)")
    public boolean isPreviewButton() {
        return shown.previewButton();
    }

    /** @return whether Jahia health shows its page summary */
    @GraphQLField
    @GraphQLDescription("Whether Jahia health shows its Page summary (SHOW_PAGE_SUMMARY)")
    public boolean isPageSummary() {
        return shown.pageSummary();
    }
}
