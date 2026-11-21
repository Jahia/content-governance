package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyDomainsTest {

    private static final Map<String, Object> TITLE = Map.of(RuleValues.RULE_ID, "seo.title",
            RuleValues.EVALUATOR, "threshold", RuleValues.SEVERITY, "serious", RuleValues.UNIT, "characters",
            RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0);

    private static String sha256(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("the fingerprint of a node before 2.0.0 is the one that its seeder kept: domain third, with the defaults")
    void legacyDigest_isTheFingerprintOfTheSeederBefore200() throws NoSuchAlgorithmException {
        Map<String, Object> node = new HashMap<>(TITLE);
        node.put(LegacyDomains.DOMAIN, "seo");

        // RuleValues.digest of 1.12.0-SNAPSHOT: one key=value line per key, in the order of its keys.
        String before = """
                ruleId=seo.title
                evaluator=threshold
                domain=seo
                enabled=true
                severity=serious
                blocking=false
                fixer=editor
                unit=characters
                minimum=30
                maximum=60
                """;
        assertThat(LegacyDomains.legacyDigest(node)).isEqualTo(sha256(before));
        assertThat(LegacyDomains.legacyDigest(node)).isNotEqualTo(RuleValues.digest(TITLE));
    }

    @Test
    @DisplayName("the node declares any property for the removal of domain only, as the repository refuses an undeclared one")
    void removeDomain_holdsTheResidualMixinForTheRemovalOnly() throws RepositoryException {
        Node node = mock(Node.class);
        Property domain = mock(Property.class);
        when(node.getProperty(LegacyDomains.DOMAIN)).thenReturn(domain);

        LegacyDomains.removeDomain(node);

        InOrder order = inOrder(node, domain);
        order.verify(node).addMixin(LegacyDomains.UNSTRUCTURED);
        order.verify(domain).remove();
        order.verify(node).removeMixin(LegacyDomains.UNSTRUCTURED);

        Node unstructured = mock(Node.class);
        when(unstructured.isNodeType(LegacyDomains.UNSTRUCTURED)).thenReturn(true);
        when(unstructured.getProperty(LegacyDomains.DOMAIN)).thenReturn(domain);
        LegacyDomains.removeDomain(unstructured);
        verify(unstructured, never()).removeMixin(LegacyDomains.UNSTRUCTURED);
    }

    @Test
    @DisplayName("a shipped rule takes its shipped subdomains, a rule of the site takes its domain, and compliance becomes terms")
    void subdomainsOf_followTheShippedRule() {
        assertThat(LegacyDomains.subdomainsOf("geo.fetched", "geo")).containsExactly("geo.crawlers");
        assertThat(LegacyDomains.subdomainsOf("seo.title", "seo")).containsExactly("seo", "geo.score");
        assertThat(LegacyDomains.subdomainsOf("compliance.example-term", "compliance")).containsExactly(ContentRules.TERMS);
        assertThat(LegacyDomains.subdomainsOf("writing.tone", "writing")).containsExactly("writing");
        assertThat(LegacyDomains.subdomainsOf("glossary.access-token", "glossary")).isEqualTo(List.of("glossary"));
    }

    @Test
    @DisplayName("a node of 1.x that nobody changed takes the subdomains of its file, loses domain, and keeps a fingerprint of nobody changed it")
    void convert_movesTheDomainOfAnUnchangedNode() throws RepositoryException {
        Map<String, Object> values = Map.of(RuleValues.RULE_ID, "geo.fetched", RuleValues.EVALUATOR, "geo.fetched",
                RuleValues.SEVERITY, "serious");
        Map<String, Object> before = new HashMap<>(values);
        before.put(LegacyDomains.DOMAIN, "geo");
        JCRNodeWrapper node = ruleNode(values, LegacyDomains.legacyDigest(before));
        Property domain = domainOf(node, "geo");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAINS, new String[]{"geo.crawlers"});
        verify(domain).remove();
        Map<String, Object> after = new HashMap<>(values);
        after.put(RuleValues.SUBDOMAINS, List.of("geo.crawlers"));
        verify(node).setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(after));
    }

    @Test
    @DisplayName("a node of 1.x that the site changed takes its domain as its subdomain, loses domain, and keeps its fingerprint")
    void convert_keepsTheFingerprintOfAChangedNode() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model",
                RuleValues.SEVERITY, "minor"), "changed by the site");
        Property domain = domainOf(node, "writing");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAINS, new String[]{"writing"});
        verify(domain).remove();
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    /** A rule node with text values and the fingerprint that a seeder kept on it. */
    private static JCRNodeWrapper ruleNode(Map<String, Object> values, String seedDigest) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        Map<String, Object> properties = new HashMap<>(values);
        properties.put(RuleNodes.SEED_DIGEST, seedDigest);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(entry.getValue().toString());
            when(node.hasProperty(entry.getKey())).thenReturn(true);
            when(node.getProperty(entry.getKey())).thenReturn(property);
        }
        Node real = mock(Node.class);
        NodeIterator noTranslation = mock(NodeIterator.class);
        when(real.getNodes("j:translation_*")).thenReturn(noTranslation);
        when(node.getRealNode()).thenReturn(real);
        return node;
    }

    /** The property domain of 1.x, which only the node of the repository shows. */
    private static Property domainOf(JCRNodeWrapper node, String value) throws RepositoryException {
        Property domain = mock(Property.class);
        when(domain.getString()).thenReturn(value);
        Node real = node.getRealNode();
        when(real.hasProperty(LegacyDomains.DOMAIN)).thenReturn(true);
        when(real.getProperty(LegacyDomains.DOMAIN)).thenReturn(domain);
        return domain;
    }
}
