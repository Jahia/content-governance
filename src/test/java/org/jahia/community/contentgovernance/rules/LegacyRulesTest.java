package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyRulesTest {

    @Test
    @DisplayName("a writing rule keeps its pattern and its examples on the mixin of the check type PATTERN when its type changes")
    void convert_movesTheKeysOfAWritingRuleToThePatternMixin() throws RepositoryException {
        Node node = mock(Node.class);
        Value forbidden = mock(Value.class);
        when(forbidden.getString()).thenReturn("forbidden");
        Property kind = single(node, RuleValues.KIND, forbidden);
        Value mail = mock(Value.class);
        Property pattern = single(node, RuleValues.PATTERN, mail);
        Value[] examples = {mock(Value.class), mock(Value.class)};
        Property wrong = mock(Property.class);
        when(wrong.isMultiple()).thenReturn(true);
        when(wrong.getValues()).thenReturn(examples);
        when(node.hasProperty(RuleValues.WRONG_EXAMPLES)).thenReturn(true);
        when(node.getProperty(RuleValues.WRONG_EXAMPLES)).thenReturn(wrong);
        when(node.getNodes("j:translation_*")).thenReturn(mock(NodeIterator.class));

        LegacyRules.convert(node, "writing.mail");

        InOrder order = inOrder(node, kind, pattern, wrong);
        order.verify(kind).remove();
        order.verify(pattern).remove();
        order.verify(wrong).remove();
        order.verify(node).setPrimaryType(ContentRules.TYPE);
        order.verify(node).addMixin(CheckType.PATTERN.mixin());
        order.verify(node).setProperty(RuleValues.KIND, forbidden);
        order.verify(node).setProperty(RuleValues.PATTERN, mail);
        order.verify(node).setProperty(RuleValues.WRONG_EXAMPLES, examples);
        verify(node).setProperty(RuleValues.CHECK_TYPE, "PATTERN");
        verify(node).setProperty(RuleValues.SUBDOMAINS, new String[]{LegacyRules.WRITING});
        verify(node).setProperty(RuleValues.SEVERITY, "moderate");
    }

    private static Property single(Node node, String name, Value value) throws RepositoryException {
        String text = value.getString();
        Property property = mock(Property.class);
        when(property.getValue()).thenReturn(value);
        when(property.getString()).thenReturn(text);
        when(node.hasProperty(name)).thenReturn(true);
        when(node.getProperty(name)).thenReturn(property);
        return property;
    }
}
