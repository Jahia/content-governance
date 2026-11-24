package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The switches of a version before 2.0.0, and their move into the layout of each site. */
class LegacyFeaturesTest {

    private static final String SITE = "/sites/digitall";

    @Test
    @DisplayName("a key of a drawer tab gives the id of its domain or subdomain, and any other id gives itself")
    void layoutId_mapsTheKeysThatChanged() {
        assertThat(LegacyFeatures.layoutId("audit")).isEqualTo("quality");
        assertThat(LegacyFeatures.layoutId("audit.jahia")).isEqualTo("health");
        assertThat(LegacyFeatures.layoutId("audit.seo")).isEqualTo("seo");
        assertThat(LegacyFeatures.layoutId("geo.crawler")).isEqualTo("geo.crawlers");
        assertThat(LegacyFeatures.layoutId("geo")).isEqualTo("geo");
        assertThat(LegacyFeatures.layoutId("geo.citations")).isEqualTo("geo.citations");
        assertThat(LegacyFeatures.layoutId("governance")).isEqualTo("governance");
        assertThat(LegacyFeatures.layoutId("chat")).isEqualTo("chat");
    }

    @Test
    @DisplayName("the switches of a site with no layout become a layout that places no domain, and the property goes")
    void moveSwitches_writesTheOffList() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper site = site(session);
        JCRPropertyWrapper disabled = disabled(site, "audit.jahia", "geo.crawler", "chat");
        JCRNodeWrapper contents = mock(JCRNodeWrapper.class);
        JCRNodeWrapper folder = mock(JCRNodeWrapper.class);
        when(site.hasNode("contents")).thenReturn(true);
        when(session.getNode(SITE + "/contents")).thenReturn(contents);
        when(contents.addNode("content-governance", SiteLayout.FOLDER_TYPE)).thenReturn(folder);
        when(session.hasPendingChanges()).thenReturn(true);

        assertThat(LegacyFeatures.moveSwitches(session)).isEqualTo(1);

        SiteLayout written = written(folder);
        assertThat(written.domains()).isEmpty();
        assertThat(written.off()).containsExactly("health", "geo.crawlers", "chat");
        verify(disabled).remove();
        verify(session).save();
    }

    @Test
    @DisplayName("the switches of a site that saved a layout join its off list, and its order stays")
    void moveSwitches_keepsTheSavedLayout() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JCRNodeWrapper site = site(session);
        disabled(site, "audit");
        JCRNodeWrapper contents = mock(JCRNodeWrapper.class);
        JCRNodeWrapper folder = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper layout = mock(JCRPropertyWrapper.class);
        when(site.hasNode("contents")).thenReturn(true);
        when(session.getNode(SITE + "/contents")).thenReturn(contents);
        when(session.nodeExists(SITE + "/contents/content-governance")).thenReturn(true);
        when(session.getNode(SITE + "/contents/content-governance")).thenReturn(folder);
        when(contents.hasNode("content-governance")).thenReturn(true);
        when(contents.getNode("content-governance")).thenReturn(folder);
        when(folder.isNodeType(SiteLayout.MIXIN)).thenReturn(true);
        when(folder.hasProperty(SiteLayout.PROPERTY)).thenReturn(true);
        when(folder.getProperty(SiteLayout.PROPERTY)).thenReturn(layout);
        when(layout.getString()).thenReturn("{\"domains\":[{\"id\":\"geo\",\"subdomains\":[]}],\"off\":[\"vitals\"]}");

        LegacyFeatures.moveSwitches(session);

        SiteLayout written = written(folder);
        assertThat(written.domainOrder("geo")).isZero();
        assertThat(written.off()).containsExactly("vitals", "quality");
    }

    /** The layout that the upgrade wrote on the folder. */
    private static SiteLayout written(JCRNodeWrapper folder) throws RepositoryException {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(folder).setProperty(eq(SiteLayout.PROPERTY), json.capture());
        return SiteLayout.parse(json.getValue());
    }

    @Test
    @DisplayName("a site with no switch is left as it is")
    void moveSwitches_leavesASiteWithNoSwitch() throws RepositoryException {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        site(session);

        assertThat(LegacyFeatures.moveSwitches(session)).isZero();
        verify(session, never()).save();
    }

    /** The only site of the repository, with its private node. */
    private static JCRNodeWrapper site(JCRSessionWrapper session) throws RepositoryException {
        JCRNodeWrapper sites = mock(JCRNodeWrapper.class);
        JCRNodeWrapper site = mock(JCRNodeWrapper.class);
        JCRNodeWrapper store = mock(JCRNodeWrapper.class);
        when(session.getNode("/sites")).thenReturn(sites);
        JCRNodeIteratorWrapper children = iterator(List.of(site));
        when(sites.getNodes()).thenReturn(children);
        when(site.getPath()).thenReturn(SITE);
        when(site.getName()).thenReturn("digitall");
        when(site.hasNode(LegacyFeatures.STORE_NODE)).thenReturn(true);
        when(site.getNode(LegacyFeatures.STORE_NODE)).thenReturn(store);
        return site;
    }

    /** Gives the private node of the site the switches of a version before 2.0.0. */
    private static JCRPropertyWrapper disabled(JCRNodeWrapper site, String... ids) throws RepositoryException {
        JCRNodeWrapper store = site.getNode(LegacyFeatures.STORE_NODE);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        List<JCRValueWrapper> values = new ArrayList<>();
        for (String id : ids) {
            JCRValueWrapper value = mock(JCRValueWrapper.class);
            when(value.getString()).thenReturn(id);
            values.add(value);
        }
        when(store.hasProperty(LegacyFeatures.DISABLED)).thenReturn(true);
        when(store.getProperty(LegacyFeatures.DISABLED)).thenReturn(property);
        when(property.getValues()).thenReturn(values.toArray(new JCRValueWrapper[0]));
        return property;
    }

    /** An iterator over a copy of the nodes, as a for loop reads it. */
    private static JCRNodeIteratorWrapper iterator(List<JCRNodeWrapper> nodes) {
        Iterator<JCRNodeWrapper> items = new ArrayList<>(nodes).iterator();
        return mock(JCRNodeIteratorWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
            case "iterator" -> items;
            case "hasNext" -> items.hasNext();
            case "next", "nextNode" -> items.next();
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
