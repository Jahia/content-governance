package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainFileTest {

    @Test
    @DisplayName("a subdomain file gives its domain, its order, its label in each language and its body as the help")
    void subdomain() {
        DomainFile file = DomainFile.parse("""
                ---
                domain: geo
                order: 20
                label:
                  en: "SEO"
                  fr: "Référencement"
                help:
                  fr: Les balises que lisent les moteurs.
                ---
                The tags that search engines read.
                """, "seo.md", Level.SUBDOMAIN);

        assertThat(file.id()).isEqualTo("seo");
        assertThat(file.values()).containsEntry(DomainValues.DOMAIN, "geo").containsEntry(DomainValues.ORDER, 20L)
                .containsEntry(DomainValues.LABEL, "SEO").containsEntry(DomainValues.HELP, "The tags that search engines read.")
                .containsEntry(DomainValues.SITES, List.of("*"))
                .containsEntry(DomainValues.TRANSLATIONS, Map.of("fr", Map.of(DomainValues.LABEL, "Référencement",
                        DomainValues.HELP, "Les balises que lisent les moteurs.")));
        Domain seo = Domain.of(Level.SUBDOMAIN, file.values());
        assertThat(seo.parent()).isEqualTo("geo");
        assertThat(seo.panel()).isNull();
        assertThat(seo.text(DomainValues.LABEL, "fr")).isEqualTo("Référencement");
        assertThat(seo.text(DomainValues.LABEL, "de")).isEqualTo("SEO");
    }

    @Test
    @DisplayName("a domain file takes the governance panel and all sites when it names none")
    void domain_defaults() {
        DomainFile file = DomainFile.parse("---\nid: quality\nlabel: Page quality\n---\n", "x.md", Level.DOMAIN);
        Domain quality = Domain.of(Level.DOMAIN, file.values());

        assertThat(quality.id()).isEqualTo("quality");
        assertThat(quality.panel()).isEqualTo(DomainValues.GOVERNANCE);
        assertThat(quality.order()).isZero();
        assertThat(quality.includes("digitall")).isTrue();
        assertThat(DomainValues.digest(Level.DOMAIN, Map.of(DomainValues.ID, "quality", DomainValues.LABEL, "Page quality")))
                .as("a node that holds the defaults has the fingerprint of a file that leaves them out")
                .isEqualTo(DomainValues.digest(Level.DOMAIN, Map.of(DomainValues.ID, "quality", DomainValues.LABEL, "Page quality",
                        DomainValues.PANEL, "governance", DomainValues.SITES, List.of("*"))));
    }

    @Test
    @DisplayName("a domain of some sites applies to those sites only")
    void sites_narrow() {
        Domain brand = Domain.of(Level.DOMAIN, DomainFile.parse("---\nid: brand\nlabel: Brand\nsites: [digitall, luxe]\n---\n",
                "brand.md", Level.DOMAIN).values());

        assertThat(brand.includes("digitall")).isTrue();
        assertThat(brand.includes("acme")).isFalse();
        assertThat(brand.includes(null)).as("the shipped hierarchy of no site").isTrue();
    }

    @Test
    @DisplayName("an unknown key, a subdomain with no domain, no label, a bad order, a bad site or an English help in the frontmatter is refused")
    void invalidFile_isRefused() {
        assertThatThrownBy(() -> DomainFile.parse("---\nlabel: X\npanel: chat\n---\n", "x.md", Level.SUBDOMAIN))
                .hasMessageContaining("unknown key panel");
        assertThatThrownBy(() -> DomainFile.parse("---\nlabel: X\n---\n", "x.md", Level.SUBDOMAIN))
                .hasMessageContaining("names no domain");
        assertThatThrownBy(() -> DomainFile.parse("---\npanel: governance\n---\n", "x.md", Level.DOMAIN))
                .hasMessageContaining("no English label");
        assertThatThrownBy(() -> DomainFile.parse("---\nlabel: X\norder: first\n---\n", "x.md", Level.DOMAIN))
                .hasMessageContaining("order is a number");
        assertThatThrownBy(() -> DomainFile.parse("---\nlabel: X\nsites: [\"a b\"]\n---\n", "x.md", Level.DOMAIN))
                .hasMessageContaining("a b");
        assertThatThrownBy(() -> DomainFile.parse("---\nlabel: X\nhelp: Read it.\n---\n", "x.md", Level.DOMAIN))
                .hasMessageContaining("body");
        assertThatThrownBy(() -> DomainFile.parse("---\nid: a/b\nlabel: X\n---\n", "x.md", Level.DOMAIN))
                .hasMessageContaining("a/b");
    }
}
