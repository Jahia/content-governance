package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.content.JCRWorkspaceWrapper;
import org.jahia.services.content.QueryManagerWrapper;
import org.jahia.services.query.QueryResultAdapter;
import org.jahia.services.query.QueryResultWrapper;
import org.jahia.services.query.QueryResultWrapperImpl;
import org.jahia.services.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.QueryResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The removal of what the seeder of a 2.0.0 snapshot wrote, on a repository in memory. */
class LegacyLayoutTest {

    private static final String CONTENT_FOLDER = "jnt:contentFolder";
    private static final String RULE_FOLDER = "ccs:contentRuleFolder";
    private static final String MODULE = "content-governance";

    @Test
    @DisplayName("a copy that the seeder wrote goes, changed by the site or not, and a rule that the site wrote stays, in the folder of the copies or beside it")
    void migrate_removesTheSeededCopies_andKeepsTheRulesOfTheSite() throws RepositoryException {
        Repository repository = new Repository();
        Fake module = repository.insights("cgmig").add(MODULE, CONTENT_FOLDER);
        Fake rules = module.add("rules", RULE_FOLDER, CONTENT_FOLDER);
        seeded(rules, "seo.title");
        seeded(rules, "seo.canonical").properties.put(RuleValues.MAXIMUM, 70.0);
        rule(rules, "brand.tone");
        rule(module, "brand.direct");

        int removed = LegacyLayout.removeLeftovers(repository.session);

        assertThat(removed).isEqualTo(2);
        assertThat(rules.childNames()).containsExactly("brand.tone");
        assertThat(module.childNames()).as("it holds a rule of the site").containsExactly("rules", "brand.direct");
    }

    @Test
    @DisplayName("a content folder that ends empty goes, up to content-insights, even with a translation, and a node that is not a content folder stays")
    void migrate_removesTheFoldersThatEndEmpty() throws RepositoryException {
        Repository repository = new Repository();
        Fake module = repository.insights("cgempty").add(MODULE, CONTENT_FOLDER);
        module.add("j:translation_en", "jnt:translation");
        seeded(module.add("rules", RULE_FOLDER, CONTENT_FOLDER), "seo.title");
        Fake insights = repository.insights("cgmig");
        seeded(insights.add("acme", CONTENT_FOLDER).add("rules", RULE_FOLDER, CONTENT_FOLDER), "acme.price");
        insights.add("notes", "jnt:contentList");

        int removed = LegacyLayout.removeLeftovers(repository.session);

        assertThat(removed).isEqualTo(2);
        assertThat(repository.contents("cgempty").childNames()).isEmpty();
        assertThat(insights.childNames()).as("a content list is not a content folder").containsExactly("notes");
        assertThat(repository.contents("cgmig").childNames()).containsExactly("content-insights");
    }

    @Test
    @DisplayName("the folder site keeps its rules, even one with a fingerprint, and nothing is saved")
    void migrate_keepsTheFolderOfTheSite() throws RepositoryException {
        Repository repository = new Repository();
        Fake insights = repository.insights("cgmig");
        Fake site = insights.add(LegacyLayout.SITE_FOLDER, CONTENT_FOLDER);
        seeded(site, "brand.copied");
        rule(site, "brand.own");

        int removed = LegacyLayout.removeLeftovers(repository.session);

        assertThat(removed).isZero();
        assertThat(site.childNames()).containsExactly("brand.copied", "brand.own");
        assertThat(insights.childNames()).containsExactly(LegacyLayout.SITE_FOLDER);
        verify(repository.session, never()).save();
    }

    @Test
    @DisplayName("a domain or a subdomain outside the tree of the system site goes, seeded or not, with its emptied folder of domains, and the tree stays")
    void migrate_removesTheDomainsOutsideTheTree() throws RepositoryException {
        Repository repository = new Repository();
        Fake tree = repository.contents("systemsite").add(MODULE, TreeSeeder.TREE_TYPE, CONTENT_FOLDER);
        Fake seo = node(node(tree, Level.DOMAIN, "geo", true), Level.SUBDOMAIN, "seo", true);
        seeded(seo, "seo.title");
        Fake legacy = repository.insights("systemsite").add(MODULE, CONTENT_FOLDER);
        node(legacy.add("domains", ContentDomains.FOLDER_TYPE, CONTENT_FOLDER), Level.DOMAIN, "geo", true);
        node(legacy.add("subdomains", ContentDomains.FOLDER_TYPE, CONTENT_FOLDER), Level.SUBDOMAIN, "seo", true);
        Fake brand = repository.contents("cgmig").add("brand-domains", CONTENT_FOLDER);
        node(node(brand, Level.DOMAIN, "brand", false), Level.SUBDOMAIN, "tone", true);
        node(brand, Level.SUBDOMAIN, "voice", false);

        int removed = LegacyLayout.removeLeftovers(repository.session);

        assertThat(removed).isEqualTo(5);
        assertThat(repository.contents("systemsite").childNames()).as("content-insights went, empty").containsExactly(MODULE);
        assertThat(tree.childNames()).containsExactly("geo");
        assertThat(tree.child("geo").childNames()).containsExactly("seo");
        assertThat(seo.childNames()).containsExactly("seo.title");
        assertThat(brand.childNames()).as("a folder of the site is not a folder of domains: it stays, empty").isEmpty();
        verify(repository.session).save();
    }

    @Test
    @DisplayName("a domain or a subdomain outside the tree that holds a rule stays, with its rule, because no 2.0.0 snapshot wrote one")
    void migrate_keepsADomainOutsideTheTreeThatHoldsNodes() throws RepositoryException {
        Repository repository = new Repository();
        Fake brand = repository.contents("cgmig").add("brand-domains", CONTENT_FOLDER);
        Fake tone = node(brand, Level.SUBDOMAIN, "tone", false);
        rule(tone, "brand.tone");

        int removed = LegacyLayout.removeLeftovers(repository.session);

        assertThat(removed).isZero();
        assertThat(brand.childNames()).containsExactly("tone");
        assertThat(tone.childNames()).containsExactly("brand.tone");
    }

    /** A rule that the site wrote: it has no fingerprint. */
    private static Fake rule(Fake parent, String id) {
        Fake rule = parent.add(id, ContentRules.TYPE);
        rule.properties.put(RuleValues.RULE_ID, id);
        return rule;
    }

    /** A rule as the seeder wrote it, with the fingerprint of its values. */
    private static Fake seeded(Fake parent, String id) {
        Map<String, Object> values = Map.of(RuleValues.RULE_ID, id, RuleValues.EVALUATOR, "threshold",
                RuleValues.SEVERITY, "serious", RuleValues.UNIT, "characters", RuleValues.MAXIMUM, 60.0,
                RuleValues.SUBDOMAIN, "seo");
        Fake rule = parent.add(id, ContentRules.TYPE);
        rule.properties.putAll(values);
        rule.properties.put(RuleNodes.SEED_DIGEST, RuleValues.digest(values));
        return rule;
    }

    /** A domain or a subdomain node, with the fingerprint that the seeder kept when {@code seeded}. */
    private static Fake node(Fake parent, Level level, String id, boolean seeded) {
        Fake node = parent.add(id, level.type());
        node.properties.put(level.idProperty(), id);
        node.properties.put(DomainValues.LABEL, id);
        if (seeded) {
            node.properties.put(RuleNodes.SEED_DIGEST,
                    DomainValues.digest(level, Map.<String, Object>of(DomainValues.ID, id, DomainValues.LABEL, id)));
        }
        return node;
    }

    /**
     * The folder /sites in memory. A session reads a node by path, saves, and runs the query of the domain or the
     * subdomain nodes under /sites, which lists the nodes of that type.
     */
    private static final class Repository {

        private final Fake sites;
        private final Node real;
        private final JCRSessionWrapper session;
        private boolean pending;

        Repository() throws RepositoryException {
            sites = new Fake(this, null, "sites", "jnt:virtualsitesFolder");
            NodeIterator noTranslation = mock(NodeIterator.class);
            real = mock(Node.class);
            when(real.getNodes(anyString())).thenReturn(noTranslation);
            QueryManagerWrapper queryManager = mock(QueryManagerWrapper.class, invocation ->
                    "createQuery".equals(invocation.getMethod().getName())
                            ? query(invocation.getArgument(0)) : RETURNS_DEFAULTS.answer(invocation));
            JCRWorkspaceWrapper workspace = mock(JCRWorkspaceWrapper.class, invocation ->
                    "getQueryManager".equals(invocation.getMethod().getName()) ? queryManager : RETURNS_DEFAULTS.answer(invocation));
            session = mock(JCRSessionWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
                case "getNode" -> find(invocation.getArgument(0)).node;
                case "getWorkspace" -> workspace;
                case "hasPendingChanges" -> pending;
                case "save" -> {
                    pending = false;
                    yield null;
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }

        /** The folder contents of a site, added with the site on the first call. */
        Fake contents(String siteKey) {
            return sites.folder(siteKey, "jnt:virtualsite").folder("contents", CONTENT_FOLDER);
        }

        /** The folder contents/content-insights of a site, which a 2.0.0 snapshot wrote. */
        Fake insights(String siteKey) {
            return contents(siteKey).folder("content-insights", CONTENT_FOLDER);
        }

        private Fake find(String path) {
            Fake node = sites;
            for (String name : path.substring("/sites".length()).split("/")) {
                if (!name.isEmpty()) {
                    node = node.child(name);
                }
            }
            return node;
        }

        /**
         * A query {@code SELECT * FROM [<type>] WHERE ISDESCENDANTNODE('/sites')}, which lists the nodes of the type.
         * Its result is the one of Jahia, around a JCR result: a mock of a result of Jahia needs Solr.
         */
        private QueryWrapper query(String statement) {
            String type = statement.substring(statement.indexOf('[') + 1, statement.indexOf(']'));
            QueryResult found = mock(QueryResult.class, invocation -> switch (invocation.getMethod().getName()) {
                case "getSelectorNames" -> new String[0];
                case "getNodes" -> iterator(sites.descendants(type, new ArrayList<>()));
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
            // The store provider gives each node back as it is: a fake node is a JCRNodeWrapper already
            JCRStoreProvider provider = mock(JCRStoreProvider.class, invocation ->
                    "getNodeWrapper".equals(invocation.getMethod().getName())
                            ? invocation.getArgument(0) : RETURNS_DEFAULTS.answer(invocation));
            QueryResultWrapper result = QueryResultWrapperImpl.wrap(
                    List.of(new QueryResultAdapter(found, provider, session)), Long.MAX_VALUE);
            return mock(QueryWrapper.class, invocation ->
                    "execute".equals(invocation.getMethod().getName()) ? result : RETURNS_DEFAULTS.answer(invocation));
        }
    }

    /** A node in memory: a mock of JCRNodeWrapper reads its types, its properties and its child nodes, and removes itself. */
    private static final class Fake implements Answer<Object> {

        private final Repository repository;
        private final Fake parent;
        private final String name;
        private final Set<String> types;
        private final Map<String, Object> properties = new HashMap<>();
        private final List<Fake> children = new ArrayList<>();
        private final JCRNodeWrapper node;

        Fake(Repository repository, Fake parent, String name, String... types) {
            this.repository = repository;
            this.parent = parent;
            this.name = name;
            this.types = Set.of(types);
            node = mock(JCRNodeWrapper.class, this);
        }

        Fake add(String childName, String... childTypes) {
            Fake child = new Fake(repository, this, childName, childTypes);
            children.add(child);
            return child;
        }

        /** The child of that name, added with the types when it is missing. */
        Fake folder(String childName, String... childTypes) {
            Fake child = child(childName);
            return child == null ? add(childName, childTypes) : child;
        }

        Fake child(String childName) {
            return children.stream().filter(child -> child.name.equals(childName)).findFirst().orElse(null);
        }

        List<String> childNames() {
            return children.stream().map(child -> child.name).toList();
        }

        /** The nodes of the type under this node, in document order. */
        List<JCRNodeWrapper> descendants(String type, List<JCRNodeWrapper> out) {
            for (Fake child : children) {
                if (child.types.contains(type)) {
                    out.add(child.node);
                }
                child.descendants(type, out);
            }
            return out;
        }

        private String path() {
            return parent == null ? "/" + name : parent.path() + "/" + name;
        }

        /** The node at a path relative to this one, such as {@code contents/content-insights}, or null. */
        private Fake find(String relativePath) {
            Fake found = this;
            for (String segment : relativePath.split("/")) {
                found = found == null ? null : found.child(segment);
            }
            return found;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getName" -> name;
                case "getPath", "toString" -> path();
                case "getParent" -> parent == null ? null : parent.node;
                case "isNodeType" -> types.contains((String) args[0]);
                case "getNodes" -> iterator(children.stream().map(child -> child.node).toList());
                case "hasNode" -> find((String) args[0]) != null;
                case "getNode" -> find((String) args[0]).node;
                case "hasProperty" -> properties.containsKey((String) args[0]);
                case "getProperty" -> property(properties.get((String) args[0]));
                case "remove" -> {
                    parent.children.remove(this);
                    repository.pending = true;
                    yield null;
                }
                case "getRealNode" -> repository.real;
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        }

        private static JCRPropertyWrapper property(Object value) {
            return mock(JCRPropertyWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
                case "getString" -> String.valueOf(value);
                case "getDouble" -> ((Number) value).doubleValue();
                case "getBoolean" -> value;
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }
    }

    /** An iterator over a copy of the nodes, as a for loop and a while loop read it. */
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
