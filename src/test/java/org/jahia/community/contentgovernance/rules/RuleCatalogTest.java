package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleCatalogTest {

    @Test
    @DisplayName("the catalog of the module loads, and gives seo.title one range")
    void base_loads() {
        Map<String, ContentRule> base = RuleCatalog.base();

        assertThat(base).isNotEmpty();
        ContentRule title = base.get("seo.title");
        assertThat(title.domain()).isEqualTo("seo");
        assertThat(title.min()).isEqualTo(30);
        assertThat(title.max()).isEqualTo(60);
        assertThat(title.enabled()).isTrue();
    }

    @Test
    @DisplayName("a catalog that repeats an id is refused")
    void repeatedId_isRefused() {
        String rule = "{\"id\": \"seo.a\", \"domain\": \"seo\", \"severity\": \"minor\", \"fixer\": \"editor\", \"advice\": \"x\"}";

        assertThatThrownBy(() -> RuleCatalog.parse("{\"rules\": [" + rule + "," + rule + "]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seo.a");
    }

    @Test
    @DisplayName("a rule with an unknown severity, an id outside its domain or a limit with no unit is refused")
    void invalidRule_isRefused() {
        assertThatThrownBy(() -> RuleCatalog.parse(
                "{\"rules\": [{\"id\": \"seo.a\", \"domain\": \"seo\", \"severity\": \"high\", \"fixer\": \"editor\", \"advice\": \"x\"}]}"))
                .hasMessageContaining("severity");
        assertThatThrownBy(() -> RuleCatalog.parse(
                "{\"rules\": [{\"id\": \"geo.a\", \"domain\": \"seo\", \"severity\": \"minor\", \"fixer\": \"editor\", \"advice\": \"x\"}]}"))
                .hasMessageContaining("domain");
        assertThatThrownBy(() -> RuleCatalog.parse(
                "{\"rules\": [{\"id\": \"seo.a\", \"domain\": \"seo\", \"severity\": \"minor\", \"fixer\": \"editor\", \"advice\": \"x\", \"minimum\": 1}]}"))
                .hasMessageContaining("unit");
    }

    @Test
    @DisplayName("a site change keeps what it does not name, and ignores a limit the rule does not have")
    void siteChange_keepsTheRest() {
        ContentRules site = ContentRules.base()
                .changed("seo.title", null, "bogus", 40.0, null)
                .changed("seo.canonical", false, "minor", 5.0, 5.0)
                .changed("no.such", false, null, null, null);

        ContentRule title = site.get("seo.title");
        assertThat(title.min()).isEqualTo(40);
        assertThat(title.max()).isEqualTo(60);
        assertThat(title.severity()).isEqualTo("serious");
        assertThat(title.accepts(39)).isFalse();
        assertThat(title.accepts(40)).isTrue();

        ContentRule canonical = site.get("seo.canonical");
        assertThat(canonical.enabled()).isFalse();
        assertThat(canonical.severity()).isEqualTo("minor");
        assertThat(canonical.minimum()).isNull();
        assertThat(canonical.maximum()).isNull();
    }

    @Test
    @DisplayName("an id the catalog does not know is a programming error")
    void unknownId_throws() {
        ContentRules base = ContentRules.base();

        assertThatThrownBy(() -> base.get("no.such")).isInstanceOf(IllegalArgumentException.class);
    }
}
