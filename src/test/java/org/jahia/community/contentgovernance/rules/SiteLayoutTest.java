package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteLayoutTest {

    private static final String SITE = "/sites/digitall";
    private static final String FOLDER = SITE + "/" + SiteLayout.FOLDER;
    private static final String JSON = """
            {"domains": [{"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title", "seo.metaDescription"]},
                                                      {"id": "geo.score", "rules": []}]},
                         {"id": "quality", "subdomains": [{"id": "accessibility", "rules": ["html.imageAltMissing"]}]}],
             "off": ["vitals", "seo.indexable"]}
            """;

    @Test
    @DisplayName("a layout reads its domains, subdomains and rules in their order, and the ids that it switches off")
    void parse_readsTheLayout() {
        SiteLayout layout = SiteLayout.parse(JSON);

        assertThat(layout.saved()).isTrue();
        assertThat(layout.domains()).extracting(SiteLayout.DomainPlace::id).containsExactly("geo", "quality");
        assertThat(layout.domains().get(0).subdomains()).extracting(SiteLayout.SubdomainPlace::id)
                .containsExactly("seo", "geo.score");
        assertThat(layout.domains().get(0).subdomains().get(0).rules()).containsExactly("seo.title", "seo.metaDescription");
        assertThat(layout.off()).containsExactly("vitals", "seo.indexable");
    }

    @Test
    @DisplayName("the JSON of a layout reads back as the same layout")
    void toJson_roundTrips() {
        SiteLayout layout = SiteLayout.parse(JSON);

        SiteLayout again = SiteLayout.parse(layout.toJson());

        assertThat(again.domains()).isEqualTo(layout.domains());
        assertThat(again.off()).containsExactlyElementsOf(layout.off());
        assertThat(again.toJson()).isEqualTo(layout.toJson());
    }

    @Test
    @DisplayName("each item has the place of its array, the parent of its array, and an item that the layout does not name has none")
    void indexes_givePlacesAndParents() {
        SiteLayout layout = SiteLayout.parse(JSON);

        assertThat(layout.domainOrder("geo")).isZero();
        assertThat(layout.domainOrder("quality")).isEqualTo(1);
        assertThat(layout.domainOf("geo.score")).isEqualTo("geo");
        assertThat(layout.domainOf("accessibility")).isEqualTo("quality");
        assertThat(layout.subdomainOrder("seo")).isLessThan(layout.subdomainOrder("geo.score"));
        assertThat(layout.subdomainOf("seo.metaDescription")).isEqualTo("seo");
        assertThat(layout.subdomainOf("html.imageAltMissing")).isEqualTo("accessibility");
        assertThat(layout.ruleOrder("seo.title")).isLessThan(layout.ruleOrder("seo.metaDescription"));
        assertThat(layout.places("geo")).isTrue();
        assertThat(layout.places("geo.score")).isTrue();
        assertThat(layout.places("seo.title")).isTrue();
        assertThat(layout.places("vitals")).as("off, but not placed").isFalse();
        assertThat(layout.isOff("vitals")).isTrue();
        assertThat(layout.isOff("seo")).isFalse();
        assertThat(layout.domainOrder("compliance")).isNull();
        assertThat(layout.domainOf("terms")).isNull();
        assertThat(layout.subdomainOrder("terms")).isNull();
        assertThat(layout.subdomainOf("seo.canonical")).isNull();
        assertThat(layout.ruleOrder("seo.canonical")).isNull();
    }

    @Test
    @DisplayName("no JSON is no layout, and an empty object is a saved layout that places nothing")
    void parse_readsNoLayout() {
        assertThat(SiteLayout.parse(null)).isSameAs(SiteLayout.NONE);
        assertThat(SiteLayout.parse("  ")).isSameAs(SiteLayout.NONE);
        assertThat(SiteLayout.NONE.saved()).isFalse();
        assertThat(SiteLayout.NONE.places("seo")).isFalse();

        SiteLayout empty = SiteLayout.parse("{}");
        assertThat(empty.saved()).isTrue();
        assertThat(empty.domains()).isEmpty();
        assertThat(empty.off()).isEmpty();
        assertThat(SiteLayout.parse(empty.toJson()).saved()).isTrue();
    }

    @Test
    @DisplayName("a text that is not a layout, an id that is not one, an id named twice, or too many ids is refused")
    void parse_refusesABadLayout() {
        assertThatThrownBy(() -> SiteLayout.parse("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteLayout.parse("{\"domains\": [{\"subdomains\": []}]}"))
                .as("a domain with no id").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteLayout.parse("{\"domains\": [\"geo\"]}"))
                .as("a domain that is not an object").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SiteLayout.parse("{\"domains\": [{\"id\": \"a/b\"}]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a/b");
        assertThatThrownBy(() -> SiteLayout.parse("{\"off\": [\"a b\"]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("a b");
        assertThatThrownBy(() -> SiteLayout.parse("""
                {"domains": [{"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title"]}]},
                             {"id": "quality", "subdomains": [{"id": "links", "rules": ["seo.title"]}]}]}
                """)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seo.title twice");
        assertThatThrownBy(() -> SiteLayout.parse("{\"domains\": [{\"id\": \"seo\", \"subdomains\": [{\"id\": \"seo\"}]}]}"))
                .as("a domain and a subdomain of one id").hasMessageContaining("seo twice");
        assertThatThrownBy(() -> SiteLayout.parse("{\"off\": [\"seo\", \"seo\"]}")).hasMessageContaining("seo twice");

        List<String> rules = new ArrayList<>();
        for (int i = 0; i < SiteLayout.MAX_IDS; i++) {
            rules.add("\"r." + i + "\"");
        }
        String full = "{\"domains\": [{\"id\": \"geo\", \"subdomains\": [{\"id\": \"seo\", \"rules\": ["
                + String.join(",", rules) + "]}]}]}";
        assertThatThrownBy(() -> SiteLayout.parse(full)).hasMessageContaining(String.valueOf(SiteLayout.MAX_IDS));
        assertThat(SiteLayout.parse(full.replace("\"r.0\",", "").replace("\"r.1\",", "")).places("r.2")).isTrue();
    }

    @Test
    @DisplayName("an id that the layout places may be switched off too")
    void parse_acceptsAPlacedIdThatIsOff() {
        SiteLayout layout = SiteLayout.parse("{\"domains\": [{\"id\": \"geo\"}], \"off\": [\"geo\"]}");

        assertThat(layout.places("geo")).isTrue();
        assertThat(layout.isOff("geo")).isTrue();
        assertThat(layout.off()).isEqualTo(Set.of("geo"));
    }

    @Test
    @DisplayName("a site with no folder, no layout or a layout that cannot be read has none")
    void read_givesNoneForWhatItCannotRead() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        assertThat(SiteLayout.read(session, SITE)).isSameAs(SiteLayout.NONE);

        JCRNodeWrapper folder = folder(session);
        assertThat(SiteLayout.read(session, SITE)).isSameAs(SiteLayout.NONE);

        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(folder.hasProperty(SiteLayout.PROPERTY)).thenReturn(true);
        when(folder.getProperty(SiteLayout.PROPERTY)).thenReturn(property);
        when(property.getString()).thenReturn("{\"domains\": [{\"id\": \"a/b\"}]}");
        assertThat(SiteLayout.read(session, SITE)).isSameAs(SiteLayout.NONE);

        when(property.getString()).thenReturn(JSON);
        assertThat(SiteLayout.read(session, SITE).places("seo.title")).isTrue();
    }

    @Test
    @DisplayName("a write puts the layout on the folder of the site, which it creates with its mixin when it is missing")
    void write_putsTheLayoutOnTheFolder() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper contents = mock(JCRNodeWrapper.class);
        JCRNodeWrapper folder = mock(JCRNodeWrapper.class);
        when(session.getNode(SITE + "/contents")).thenReturn(contents);
        when(contents.addNode("content-governance", SiteLayout.FOLDER_TYPE)).thenReturn(folder);
        SiteLayout layout = SiteLayout.parse(JSON);

        SiteLayout.write(session, SITE, layout);

        verify(folder).addMixin(SiteLayout.MIXIN);
        verify(folder).setProperty(SiteLayout.PROPERTY, layout.toJson());

        // The folder exists, with its mixin: only the layout changes
        JCRNodeWrapper existing = mock(JCRNodeWrapper.class);
        when(contents.hasNode("content-governance")).thenReturn(true);
        when(contents.getNode("content-governance")).thenReturn(existing);
        when(existing.isNodeType(SiteLayout.MIXIN)).thenReturn(true);
        SiteLayout.write(session, SITE, layout);
        verify(existing, never()).addMixin(anyString());
        verify(existing).setProperty(SiteLayout.PROPERTY, layout.toJson());
    }

    @Test
    @DisplayName("a write of no layout removes the layout of the site, and creates nothing")
    void write_removesTheLayoutForNone() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper folder = folder(session);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(folder.hasProperty(SiteLayout.PROPERTY)).thenReturn(true);
        when(folder.getProperty(SiteLayout.PROPERTY)).thenReturn(property);

        SiteLayout.write(session, SITE, SiteLayout.NONE);

        verify(property).remove();
        verify(session, never()).getNode(SITE + "/contents");
    }

    /** The folder of the layout of the site, which the session finds. */
    private static JCRNodeWrapper folder(JCRSessionWrapper session) throws RepositoryException {
        JCRNodeWrapper folder = mock(JCRNodeWrapper.class);
        when(session.nodeExists(FOLDER)).thenReturn(true);
        when(session.getNode(FOLDER)).thenReturn(folder);
        return folder;
    }

    @Test
    @DisplayName("an id that the saved layout names neither in an array nor in off is new, and an id that off names is not")
    void isNew_leavesOutAnIdThatOffNames() {
        SiteLayout layout = SiteLayout.parse("{\"domains\": [{\"id\": \"geo\"}], \"off\": [\"seo.indexable\"]}");

        assertThat(layout.isNew("geo")).isFalse();
        assertThat(layout.isNew("seo.indexable")).isFalse();
        assertThat(layout.isNew("seo.openGraph")).isTrue();
        assertThat(SiteLayout.NONE.isNew("seo.openGraph")).as("a site with no layout has nothing new").isFalse();
    }
}
