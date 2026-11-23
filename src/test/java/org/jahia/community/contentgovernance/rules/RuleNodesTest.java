package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleNodesTest {

    private static final Map<String, Object> TITLE = Map.of(RuleValues.RULE_ID, "seo.title",
            RuleValues.EVALUATOR, "threshold", RuleValues.CHECK_TYPE, "CODE", RuleValues.SEVERITY, "serious",
            RuleValues.UNIT, "characters", RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0);

    @Test
    @DisplayName("a write puts the mixin of the check type on the node before its keys, and takes the mixin of another type off first")
    void write_putsTheMixinOfTheCheckTypeFirst() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of());
        when(node.isNodeType(CheckType.AI.mixin())).thenReturn(true);

        RuleNodes.write(node, TITLE, true);

        InOrder order = inOrder(node);
        order.verify(node).removeMixin(CheckType.AI.mixin());
        order.verify(node).addMixin(CheckType.CODE.mixin());
        order.verify(node).setProperty(RuleValues.UNIT, "characters");
        verify(node).setProperty(RuleValues.CHECK_TYPE, "CODE");
        verify(node, never()).removeMixin(CheckType.PATTERN.mixin());
    }

    @Test
    @DisplayName("a node that holds the mixin of its check type keeps it")
    void mixIn_keepsTheMixinThatTheNodeHolds() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of());
        when(node.isNodeType(CheckType.CODE.mixin())).thenReturn(true);

        assertThat(RuleNodes.mixIn(node, CheckType.CODE)).isFalse();

        verify(node, never()).addMixin(anyString());
        verify(node, never()).removeMixin(anyString());
    }

    @Test
    @DisplayName("a node of the check type AI gets the minimum of a shipped rule of the type CODE, and no unit and no maximum")
    void fillMissing_leavesOutTheKeysOfAnotherCheckType() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.EVALUATOR, "model",
                RuleValues.CHECK_TYPE, "AI", RuleValues.SEVERITY, "serious"));

        assertThat(RuleNodes.fillMissing(node, TITLE)).isTrue();

        verify(node).setProperty(RuleValues.MINIMUM, 30.0);
        verify(node, never()).setProperty(eq(RuleValues.UNIT), anyString());
        verify(node, never()).setProperty(eq(RuleValues.MAXIMUM), anyDouble());
        verify(node, never()).addMixin(anyString());
    }

    @Test
    @DisplayName("a node with no check type takes the one of the shipped rule, with its mixin, and then its keys")
    void fillMissing_givesTheCheckTypeOfTheValues() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "seo.title", RuleValues.EVALUATOR, "threshold"));

        RuleNodes.fillMissing(node, TITLE);

        InOrder order = inOrder(node);
        order.verify(node).addMixin(CheckType.CODE.mixin());
        order.verify(node).setProperty(RuleValues.CHECK_TYPE, "CODE");
        order.verify(node).setProperty(RuleValues.UNIT, "characters");
    }

    /** A rule node with these values, a text, a number or a flag each, and no translation. */
    static JCRNodeWrapper ruleNode(Map<String, Object> values) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        for (Map.Entry<String, Object> entry : new HashMap<>(values).entrySet()) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            Object value = entry.getValue();
            when(property.getString()).thenReturn(value.toString());
            if (value instanceof Double number) {
                when(property.getDouble()).thenReturn(number);
            }
            if (value instanceof Boolean flag) {
                when(property.getBoolean()).thenReturn(flag);
            }
            when(node.hasProperty(entry.getKey())).thenReturn(true);
            when(node.getProperty(entry.getKey())).thenReturn(property);
        }
        Node real = mock(Node.class);
        NodeIterator noTranslation = mock(NodeIterator.class);
        when(real.getNodes("j:translation_*")).thenReturn(noTranslation);
        when(node.getRealNode()).thenReturn(real);
        return node;
    }
}
