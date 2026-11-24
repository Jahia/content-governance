package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The order that the defaults of the system site give to the child nodes of the tree. */
class TreeLayoutTest {

    @Test
    @DisplayName("the named child nodes come first, in the order of the layout, then the others in their order")
    void ordered_putsTheNamedNodesFirst() {
        assertThat(TreeLayout.ordered(List.of("health", "links", "vitals", "seo"), List.of("seo", "health")))
                .containsExactly("seo", "health", "links", "vitals");
    }

    @Test
    @DisplayName("a name that is not a child node, or that comes twice, is left out")
    void ordered_leavesOutWhatIsNotAChild() {
        assertThat(TreeLayout.ordered(List.of("health", "links"), List.of("links", "seo", "links")))
                .containsExactly("links", "health");
        assertThat(TreeLayout.ordered(List.of("health", "links"), List.of())).containsExactly("health", "links");
    }
}
