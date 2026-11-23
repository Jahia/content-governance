package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCheckTypesTest {

    private static final Map<String, Object> TITLE = Map.of(RuleValues.RULE_ID, "seo.title",
            RuleValues.EVALUATOR, "threshold", RuleValues.SEVERITY, "serious", RuleValues.UNIT, "characters",
            RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0);

    @Test
    @DisplayName("a node that nobody changed takes the check type of its check and its mixin, and keeps a fingerprint of nobody changed it")
    void convert_givesAnUnchangedNodeItsCheckType() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(TITLE, RuleValues.digest(TITLE));

        LegacyCheckTypes.convert(node);

        InOrder order = inOrder(node);
        order.verify(node).addMixin(CheckType.CODE.mixin());
        order.verify(node).setProperty(RuleValues.CHECK_TYPE, "CODE");
        Map<String, Object> after = new HashMap<>(TITLE);
        after.put(RuleValues.CHECK_TYPE, "CODE");
        verify(node).setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(after));
    }

    @Test
    @DisplayName("a node that the site changed takes its check type and keeps its fingerprint")
    void convert_keepsTheFingerprintOfAChangedNode() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "acme.tone", RuleValues.EVALUATOR, "model",
                RuleValues.SEVERITY, "minor"), "changed by the site");

        LegacyCheckTypes.convert(node);

        verify(node).addMixin(CheckType.AI.mixin());
        verify(node).setProperty(RuleValues.CHECK_TYPE, "AI");
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    @Test
    @DisplayName("a node with no evaluator is a writing rule, and a node that no seeder wrote gets no fingerprint")
    void convert_readsNoEvaluatorAsPattern() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "writing.tone"), null);

        LegacyCheckTypes.convert(node);

        verify(node).addMixin(CheckType.PATTERN.mixin());
        verify(node).setProperty(RuleValues.CHECK_TYPE, "PATTERN");
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    @Test
    @DisplayName("the keys of another check type stay in the repository, and the conversion names them")
    void hiddenKeys_namesTheKeysOfAnotherType() throws RepositoryException {
        Node real = mock(Node.class);
        when(real.hasProperty(RuleValues.PATTERN)).thenReturn(true);
        when(real.hasProperty(RuleValues.MINIMUM)).thenReturn(true);

        assertThat(LegacyCheckTypes.hiddenKeys(real, CheckType.CODE)).containsExactly(RuleValues.PATTERN);
        assertThat(LegacyCheckTypes.hiddenKeys(real, CheckType.AI)).containsExactly(RuleValues.PATTERN);
        assertThat(LegacyCheckTypes.hiddenKeys(real, CheckType.PATTERN)).containsExactly(RuleValues.MINIMUM);
        assertThat(LegacyCheckTypes.hiddenKeys(null, CheckType.PATTERN)).isEmpty();
    }

    /** A rule node with these values and the fingerprint that a seeder kept on it, or none. */
    private static JCRNodeWrapper ruleNode(Map<String, Object> values, String seedDigest) throws RepositoryException {
        Map<String, Object> properties = new HashMap<>(values);
        if (seedDigest != null) {
            properties.put(RuleNodes.SEED_DIGEST, seedDigest);
        }
        JCRNodeWrapper node = RuleNodesTest.ruleNode(properties);
        when(node.getPath()).thenReturn("/sites/acme/contents/content-insights/acme/rules/" + values.get(RuleValues.RULE_ID));
        return node;
    }
}
