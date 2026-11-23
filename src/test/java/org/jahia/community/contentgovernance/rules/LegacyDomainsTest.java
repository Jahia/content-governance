package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("the removal takes the property off a node that holds it, and leaves a node without it as it is")
    void remove_takesThePropertyOff() throws RepositoryException {
        Node node = mock(Node.class);
        Property domain = mock(Property.class);
        when(node.hasProperty(LegacyDomains.DOMAIN)).thenReturn(true);
        when(node.getProperty(LegacyDomains.DOMAIN)).thenReturn(domain);

        LegacyDomains.remove(node, LegacyDomains.DOMAIN);

        verify(domain).remove();

        // A node without the property is left as it is
        Node without = mock(Node.class);
        LegacyDomains.remove(without, LegacyDomains.SUBDOMAINS);
        verify(without, never()).getProperty(anyString());
    }

    @Test
    @DisplayName("a shipped rule takes its shipped subdomain, and a rule of the site keeps the stored one")
    void subdomainOf_followsTheShippedRule() {
        assertThat(LegacyDomains.subdomainOf("geo.fetched", "geo")).isEqualTo("geo.crawlers");
        assertThat(LegacyDomains.subdomainOf("seo.title", "seo")).isEqualTo("seo");
        assertThat(LegacyDomains.subdomainOf("seo.title", "geo.score")).as("the file wins over a stored subdomain")
                .isEqualTo("seo");
        assertThat(LegacyDomains.subdomainOf("writing.tone", "writing")).isEqualTo("writing");
        assertThat(LegacyDomains.subdomainOf("glossary.access-token", "glossary")).isEqualTo("glossary");
        assertThat(LegacyDomains.subdomainOf("brand.tone", null)).isNull();
    }

    @Test
    @DisplayName("the keys of the fingerprint of a 2.0.0 snapshot are the keys of now, with subdomains in the place of subdomain")
    void keys2_holdSubdomainsWhereSubdomainIsNow() {
        int place = RuleValues.KEYS.indexOf(RuleValues.SUBDOMAIN);

        assertThat(LegacyDomains.KEYS_2).hasSameSizeAs(RuleValues.KEYS).doesNotContain(RuleValues.SUBDOMAIN);
        assertThat(LegacyDomains.KEYS_2.get(place)).isEqualTo(LegacyDomains.SUBDOMAINS);
    }

    @Test
    @DisplayName("a node of 1.x that nobody changed takes the subdomain of its file, loses domain, and keeps a fingerprint of nobody changed it")
    void convert_movesTheDomainOfAnUnchangedNode() throws RepositoryException {
        Map<String, Object> values = Map.of(RuleValues.RULE_ID, "geo.fetched", RuleValues.EVALUATOR, "geo.fetched",
                RuleValues.SEVERITY, "serious");
        Map<String, Object> before = new HashMap<>(values);
        before.put(LegacyDomains.DOMAIN, "geo");
        JCRNodeWrapper node = ruleNode(values, LegacyDomains.legacyDigest(before));
        Property domain = domainOf(node, "geo");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAIN, "geo.crawlers");
        verify(domain).remove();
        Map<String, Object> after = new HashMap<>(values);
        after.put(RuleValues.SUBDOMAIN, "geo.crawlers");
        verify(node).setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(after));
    }

    @Test
    @DisplayName("a node with a runtime that a 1.x build fingerprinted, runtime last, reads as unchanged")
    void convert_readsTheFingerprintWithRuntimeLastAsUnchanged() throws RepositoryException, NoSuchAlgorithmException {
        Map<String, Object> values = Map.of(RuleValues.RULE_ID, "axe.document-title", RuleValues.EVALUATOR, "axe",
                RuleValues.RUNTIME, "browser", RuleValues.SEVERITY, "serious");
        // RuleValues.digest of 1.12.0-SNAPSHOT with #152: runtime after label, its last key.
        String seeded = sha256("""
                ruleId=axe.document-title
                evaluator=axe
                domain=accessibility
                enabled=true
                severity=serious
                blocking=false
                fixer=editor
                runtime=browser
                """);
        JCRNodeWrapper node = ruleNode(values, seeded);
        domainOf(node, "accessibility");

        LegacyDomains.convert(node, node.getRealNode());

        Map<String, Object> after = new HashMap<>(values);
        after.put(RuleValues.SUBDOMAIN, "accessibility");
        verify(node).setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(after));
    }

    @Test
    @DisplayName("a node of 1.x that the site changed takes its domain as its subdomain, loses domain, and keeps its fingerprint")
    void convert_keepsTheFingerprintOfAChangedNode() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model",
                RuleValues.SEVERITY, "minor"), "changed by the site");
        Property domain = domainOf(node, "writing");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAIN, "writing");
        verify(domain).remove();
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    @Test
    @DisplayName("a node of 1.x in the domain compliance, which is no subdomain, goes to terms")
    void convert_renamesCompliance() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "compliance.click-here", RuleValues.EVALUATOR, "pattern"),
                "changed by the site");
        domainOf(node, "compliance");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAIN, ContentRules.TERMS);
    }

    @Test
    @DisplayName("a node of a 2.0.0 snapshot that nobody changed takes the subdomain of its file, loses subdomains, and keeps a fingerprint of nobody changed it")
    void convert_givesOneSubdomainToAnUnchangedNodeOfASnapshot() throws RepositoryException, NoSuchAlgorithmException {
        // RuleValues.digest of 2.0.0-SNAPSHOT: subdomains as a list, each item followed by the unit separator U+001F.
        String seeded = sha256("""
                ruleId=seo.title
                evaluator=threshold
                enabled=true
                severity=serious
                blocking=false
                fixer=editor
                unit=characters
                minimum=30
                maximum=60
                subdomains=seo\037geo.score\037
                sites=*\037
                """);
        JCRNodeWrapper node = ruleNode(TITLE, seeded);
        Property subdomains = subdomainsOf(node, "seo", "geo.score");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAIN, "seo");
        verify(subdomains).remove();
        Map<String, Object> after = new HashMap<>(TITLE);
        after.put(RuleValues.SUBDOMAIN, "seo");
        verify(node).setProperty(RuleNodes.SEED_DIGEST, RuleValues.digest(after));
    }

    @Test
    @DisplayName("a node of a 2.0.0 snapshot that the site changed takes its first subdomain, loses subdomains, and keeps its fingerprint")
    void convert_givesTheFirstSubdomainToAChangedNodeOfASnapshot() throws RepositoryException {
        JCRNodeWrapper node = ruleNode(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model",
                RuleValues.SEVERITY, "minor"), "changed by the site");
        Property subdomains = subdomainsOf(node, "tone", "writing");

        LegacyDomains.convert(node, node.getRealNode());

        verify(node).setProperty(RuleValues.SUBDOMAIN, "tone");
        verify(subdomains).remove();
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    @Test
    @DisplayName("a node of a 2.0.0 snapshot with no subdomain loses subdomains and gets none, and a node that names a subdomain keeps it")
    void convert_keepsTheSubdomainThatANodeNames() throws RepositoryException {
        JCRNodeWrapper empty = ruleNode(Map.of(RuleValues.RULE_ID, "brand.tone", RuleValues.EVALUATOR, "model"), null);
        Property none = subdomainsOf(empty);

        LegacyDomains.convert(empty, empty.getRealNode());

        verify(none).remove();
        verify(empty, never()).setProperty(eq(RuleValues.SUBDOMAIN), anyString());

        JCRNodeWrapper named = ruleNode(Map.of(RuleValues.RULE_ID, "brand.voice", RuleValues.EVALUATOR, "model",
                RuleValues.SUBDOMAIN, "writing"), null);
        Property both = subdomainsOf(named, "tone");

        LegacyDomains.convert(named, named.getRealNode());

        verify(both).remove();
        verify(named, never()).setProperty(eq(RuleValues.SUBDOMAIN), anyString());
    }

    /** A rule node outside the tree with text values, and the fingerprint that a seeder kept on it, or none. */
    private static JCRNodeWrapper ruleNode(Map<String, Object> values, String seedDigest) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        Map<String, Object> properties = new HashMap<>(values);
        if (seedDigest != null) {
            properties.put(RuleNodes.SEED_DIGEST, seedDigest);
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            when(property.getString()).thenReturn(entry.getValue().toString());
            if (entry.getValue() instanceof Double number) {
                when(property.getDouble()).thenReturn(number);
            }
            when(node.hasProperty(entry.getKey())).thenReturn(true);
            when(node.getProperty(entry.getKey())).thenReturn(property);
        }
        when(node.getParent()).thenReturn(mock(JCRNodeWrapper.class));
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

    /** The property subdomains of a 2.0.0 snapshot, which only the node of the repository shows. */
    private static Property subdomainsOf(JCRNodeWrapper node, String... ids) throws RepositoryException {
        Value[] items = new Value[ids.length];
        for (int i = 0; i < ids.length; i++) {
            items[i] = mock(Value.class);
            when(items[i].getString()).thenReturn(ids[i]);
        }
        Property subdomains = mock(Property.class);
        when(subdomains.getValues()).thenReturn(items);
        Node real = node.getRealNode();
        when(real.hasProperty(LegacyDomains.SUBDOMAINS)).thenReturn(true);
        when(real.getProperty(LegacyDomains.SUBDOMAINS)).thenReturn(subdomains);
        return subdomains;
    }
}
