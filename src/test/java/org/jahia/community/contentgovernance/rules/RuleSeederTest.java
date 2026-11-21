package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class RuleSeederTest {

    private static final Map<String, Object> TITLE = Map.of(RuleValues.RULE_ID, "seo.title",
            RuleValues.EVALUATOR, "threshold", RuleValues.SEVERITY, "serious", RuleValues.UNIT, "characters",
            RuleValues.MINIMUM, 30.0, RuleValues.MAXIMUM, 60.0);

    @Test
    @DisplayName("a node with the defaults of its type has the fingerprint of a file that leaves them out")
    void digest_countsTheDefaults() {
        Map<String, Object> node = new HashMap<>(TITLE);
        node.put(RuleValues.ENABLED, true);
        node.put(RuleValues.BLOCKING, false);
        node.put(RuleValues.FIXER, "editor");
        node.put(RuleValues.SITES, List.of("*"));

        assertThat(RuleValues.digest(node)).isEqualTo(RuleValues.digest(TITLE));
    }

    @Test
    @DisplayName("a site that changes one value changes the fingerprint")
    void digest_seesAChange() {
        Map<String, Object> changed = new HashMap<>(TITLE);
        changed.put(RuleValues.MINIMUM, 40.0);

        assertThat(RuleValues.digest(changed)).isNotEqualTo(RuleValues.digest(TITLE));
    }

    @Test
    @DisplayName("a new version overwrites a rule that nobody changed, and only fills a rule that the site changed")
    void update_followsTheFingerprint() {
        assertThat(RuleSeeder.updateOf("a", "a", "a")).isEqualTo(RuleSeeder.Update.NONE);
        assertThat(RuleSeeder.updateOf("a", "a", "b")).isEqualTo(RuleSeeder.Update.OVERWRITE);
        assertThat(RuleSeeder.updateOf("a", "c", "b")).isEqualTo(RuleSeeder.Update.FILL);
        assertThat(RuleSeeder.updateOf(null, "a", "a")).isEqualTo(RuleSeeder.Update.FILL);
    }

    @Test
    @DisplayName("a rule that the module no longer ships goes if nobody changed it, and stays marked if the site did")
    void retirement_followsTheFingerprint() {
        assertThat(RuleSeeder.retirementOf("a", "a")).isEqualTo(RuleSeeder.Retirement.REMOVE);
        assertThat(RuleSeeder.retirementOf("a", "b")).isEqualTo(RuleSeeder.Retirement.MARK);
        assertThat(RuleSeeder.retirementOf(null, "b")).isEqualTo(RuleSeeder.Retirement.KEEP);
    }

    @Test
    @DisplayName("a writing rule gets the evaluator pattern, blocking as a flag next to its severity, and a style rule is minor")
    void writingRule_mapsToAContentRule() {
        assertThat(LegacyRules.ruleOf("writing.a", "forbidden", "warning"))
                .containsEntry(RuleValues.EVALUATOR, "pattern").containsEntry(RuleValues.SEVERITY, "moderate")
                .containsEntry(RuleValues.BLOCKING, false).containsEntry(RuleValues.SUBDOMAINS, List.of("writing"));
        assertThat(LegacyRules.ruleOf("writing.b", "replace", "blocking"))
                .containsEntry(RuleValues.SEVERITY, "serious").containsEntry(RuleValues.BLOCKING, true);
        assertThat(LegacyRules.ruleOf("writing.c", "style", "warning"))
                .containsEntry(RuleValues.EVALUATOR, "pattern").containsEntry(RuleValues.SEVERITY, "minor");
    }

    @Test
    @DisplayName("only a change of the installed modules of a site triggers a seed")
    void sitePathOf_readsTheInstalledModules() {
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/j:installedModules")).isEqualTo("/sites/digitall");
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/home/j:installedModules")).isNull();
        assertThat(RuleSeeder.sitePathOf("/sites/digitall/j:title")).isNull();
    }

    @Test
    @DisplayName("only the processing server converts the rules, the terms and the domains of an older version")
    void activate_convertsOnTheProcessingServerOnly() {
        for (boolean processing : List.of(false, true)) {
            try (MockedStatic<LegacyRules> rules = mockStatic(LegacyRules.class);
                 MockedStatic<LegacyTerms> terms = mockStatic(LegacyTerms.class);
                 MockedStatic<LegacyDomains> domains = mockStatic(LegacyDomains.class)) {
                RuleSeeder seeder = seeder(processing);

                seeder.activate(mock(BundleContext.class));

                rules.verify(LegacyRules::migrate, processing ? times(1) : never());
                terms.verify(LegacyTerms::migrate, processing ? times(1) : never());
                domains.verify(LegacyDomains::migrate, processing ? times(1) : never());
                seeder.deactivate();
            }
        }
    }

    @Test
    @DisplayName("every node reads the rules that a module ships, and only the processing server seeds the system site and the sites")
    void addingBundle_seedsOnTheProcessingServerOnly() throws RepositoryException {
        ShippedRules.Shipment shipment = new ShippedRules.Shipment("acme", Map.of(), true);
        for (boolean processing : List.of(false, true)) {
            try (MockedStatic<LegacyRules> rules = mockStatic(LegacyRules.class);
                 MockedStatic<LegacyTerms> terms = mockStatic(LegacyTerms.class);
                 MockedStatic<LegacyDomains> domains = mockStatic(LegacyDomains.class);
                 MockedStatic<DomainSeeder> domainSeeder = mockStatic(DomainSeeder.class);
                 MockedStatic<ShippedRules> shipped = mockStatic(ShippedRules.class);
                 MockedStatic<JCRTemplate> jcr = mockStatic(JCRTemplate.class)) {
                Bundle bundle = mock(Bundle.class);
                shipped.when(() -> ShippedRules.load(bundle)).thenReturn(shipment);
                JCRTemplate template = mock(JCRTemplate.class);
                jcr.when(JCRTemplate::getInstance).thenReturn(template);
                when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any())).thenReturn(List.of());
                RuleSeeder seeder = seeder(processing);
                seeder.activate(mock(BundleContext.class));

                assertThat(seeder.addingBundle(bundle, null)).isEqualTo("acme");

                shipped.verify(() -> ShippedRules.put(shipment));
                domainSeeder.verify(() -> DomainSeeder.seed(shipment), processing ? times(1) : never());
                jcr.verify(JCRTemplate::getInstance, processing ? times(1) : never());
                seeder.deactivate();
            }
        }
    }

    @Test
    @DisplayName("a rule that names the subdomain of a module that starts later comes back when that module starts")
    void addingBundle_readsAgainARuleThatWaitsForItsSubdomain() {
        Bundle rules = bundle("acme-rules", Map.of("content-rules/acme.price.md",
                "---\nevaluator: erp.price\nsubdomains: [acme.prices]\nseverity: serious\n---\nCheck the price.\n"));
        Bundle subdomains = bundle("acme-subdomains", Map.of("content-rules/subdomains/acme.prices.md",
                "---\ndomain: compliance\nlabel: Prices\n---\nThe prices that the ERP gives.\n"));
        RuleSeeder seeder = seeder(false);
        seeder.activate(mock(BundleContext.class));
        try {
            seeder.addingBundle(rules, null);
            assertThat(ShippedRules.of("acme-rules").rules()).isEmpty();

            seeder.addingBundle(subdomains, null);

            assertThat(ShippedRules.of("acme-rules").rules()).containsOnlyKeys("acme.price");
            assertThat(ShippedRules.of("acme-rules").complete()).isTrue();
        } finally {
            seeder.removedBundle(rules, null, "acme-rules");
            seeder.removedBundle(subdomains, null, "acme-subdomains");
            seeder.deactivate();
        }
    }

    /** A seeder on the processing server, or on a browsing node of a cluster. */
    private static RuleSeeder seeder(boolean processing) {
        RuleSeeder seeder = new RuleSeeder();
        seeder.processingServerCheck = () -> processing;
        return seeder;
    }

    /** A started bundle whose manifest names the rule folder content-rules, with its files by path. */
    private static Bundle bundle(String module, Map<String, String> files) {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn(module);
        when(bundle.getHeaders()).thenReturn(new Hashtable<>(Map.of(ShippedRules.HEADER, "content-rules")));
        List<URL> entries = new ArrayList<>();
        files.forEach((path, text) -> entries.add(entry(module, path, text)));
        when(bundle.findEntries("content-rules", "*.md", true)).thenAnswer(invocation -> Collections.enumeration(entries));
        return bundle;
    }

    /** An entry of a bundle, whose path starts at the root of the bundle, as the framework gives it. */
    private static URL entry(String module, String path, String text) {
        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                        // The text is in memory.
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
                    }
                };
            }
        };
        try {
            return new URL("bundle", module, -1, "/" + path, handler);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
