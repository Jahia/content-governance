package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainSeederTest {

    private static final Map<String, Object> SEO = Map.of(DomainValues.ID, "seo", DomainValues.DOMAIN, "geo",
            DomainValues.ORDER, 20L, DomainValues.LABEL, "SEO");

    @Test
    @DisplayName("a node with the defaults of its type has the fingerprint of a file that leaves them out")
    void digest_countsTheDefaults() {
        Map<String, Object> subdomain = new HashMap<>(SEO);
        subdomain.put(DomainValues.SITES, List.of("*"));
        Map<String, Object> domain = Map.of(DomainValues.ID, "quality", DomainValues.LABEL, "Page quality");
        Map<String, Object> governance = new HashMap<>(domain);
        governance.put(DomainValues.PANEL, DomainValues.GOVERNANCE);

        assertThat(DomainValues.digest(Level.SUBDOMAIN, subdomain)).isEqualTo(DomainValues.digest(Level.SUBDOMAIN, SEO));
        assertThat(DomainValues.digest(Level.DOMAIN, governance)).isEqualTo(DomainValues.digest(Level.DOMAIN, domain));
        assertThat(DomainValues.digest(Level.SUBDOMAIN, changed(DomainValues.LABEL, "Search")))
                .isNotEqualTo(DomainValues.digest(Level.SUBDOMAIN, SEO));
    }

    @Test
    @DisplayName("a new version overwrites a node that nobody changed, and keeps its fingerprint up to date")
    void update_overwritesANodeThatNobodyChanged() throws RepositoryException {
        JCRNodeWrapper node = subdomainNode(SEO, DomainValues.digest(Level.SUBDOMAIN, SEO));
        Map<String, Object> shipped = changed(DomainValues.ORDER, 30L);

        assertThat(DomainSeeder.update(node, new DomainFile(Level.SUBDOMAIN, "seo", shipped))).isEqualTo(1);

        verify(node).setProperty(DomainValues.ORDER, 30L);
        verify(node).setProperty(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, shipped));
    }

    @Test
    @DisplayName("a new version keeps the values of a node that an administrator changed")
    void update_keepsANodeThatAnAdministratorChanged() throws RepositoryException {
        JCRNodeWrapper node = subdomainNode(changed(DomainValues.LABEL, "Search"), DomainValues.digest(Level.SUBDOMAIN, SEO));

        assertThat(DomainSeeder.update(node, new DomainFile(Level.SUBDOMAIN, "seo", changed(DomainValues.ORDER, 30L))))
                .isZero();

        verify(node, never()).setProperty(eq(DomainValues.ORDER), anyLong());
        verify(node, never()).setProperty(eq(DomainValues.LABEL), anyString());
        verify(node, never()).setProperty(eq(RuleNodes.SEED_DIGEST), anyString());
    }

    @Test
    @DisplayName("a node that the module no longer ships goes if nobody changed it, and stays marked if an administrator did")
    void retire_followsTheFingerprint() throws RepositoryException {
        String seeded = DomainValues.digest(Level.SUBDOMAIN, SEO);
        JCRNodeWrapper unchanged = subdomainNode(SEO, seeded);
        JCRNodeWrapper changed = subdomainNode(changed(DomainValues.LABEL, "Search"), seeded);

        assertThat(DomainSeeder.retire(unchanged, Level.SUBDOMAIN)).isEqualTo(1);
        assertThat(DomainSeeder.retire(changed, Level.SUBDOMAIN)).isEqualTo(1);

        verify(unchanged).remove();
        verify(changed, never()).remove();
        verify(changed).setProperty(RuleNodes.RETIRED, true);
    }

    /** The values of the subdomain seo with one value changed. */
    private static Map<String, Object> changed(String key, Object value) {
        Map<String, Object> values = new HashMap<>(SEO);
        values.put(key, value);
        return values;
    }

    /** A subdomain node that holds the values, and the fingerprint that the seeder kept on it. */
    private static JCRNodeWrapper subdomainNode(Map<String, Object> values, String seedDigest) throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.isNodeType(Level.SUBDOMAIN.type())).thenReturn(true);
        Map<String, Object> properties = new HashMap<>();
        values.forEach((key, value) -> properties.put(Level.SUBDOMAIN.property(key), value));
        properties.put(RuleNodes.SEED_DIGEST, seedDigest);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
            if (entry.getValue() instanceof Long number) {
                when(property.getLong()).thenReturn(number);
            }
            when(property.getString()).thenReturn(entry.getValue().toString());
            when(node.hasProperty(entry.getKey())).thenReturn(true);
            when(node.getProperty(entry.getKey())).thenReturn(property);
        }
        return node;
    }
}
