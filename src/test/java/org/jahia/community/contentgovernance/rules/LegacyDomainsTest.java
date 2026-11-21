package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.jcr.Node;
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
}
