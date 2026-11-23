package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRValueWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The tree of the system site that the seeder writes, on a repository in memory. */
class TreeSeederTest {

    private static final String ACME = "acme";
    private static final String CONTENTS = ContentDomains.SYSTEM_SITE + "/contents";

    @Test
    @DisplayName("a node with the defaults of its type has the fingerprint of a file that leaves them out, and the domain counts but the order does not")
    void digest_countsTheDefaultsAndTheDomain() {
        Map<String, Object> seo = Map.of(DomainValues.ID, "seo", DomainValues.DOMAIN, "geo", DomainValues.LABEL, "SEO");
        Map<String, Object> domain = Map.of(DomainValues.ID, "quality", DomainValues.LABEL, "Page quality");
        Map<String, Object> governance = new HashMap<>(domain);
        governance.put(DomainValues.PANEL, DomainValues.GOVERNANCE);

        assertThat(DomainValues.digest(Level.SUBDOMAIN, changed(seo, DomainValues.SITES, List.of("*"))))
                .isEqualTo(DomainValues.digest(Level.SUBDOMAIN, seo));
        assertThat(DomainValues.digest(Level.DOMAIN, governance)).isEqualTo(DomainValues.digest(Level.DOMAIN, domain));
        assertThat(DomainValues.digest(Level.SUBDOMAIN, changed(seo, DomainValues.ORDER, 30L)))
                .as("the tree gives the place").isEqualTo(DomainValues.digest(Level.SUBDOMAIN, seo));
        assertThat(DomainValues.digest(Level.SUBDOMAIN, changed(seo, DomainValues.DOMAIN, "quality")))
                .as("a move to another domain").isNotEqualTo(DomainValues.digest(Level.SUBDOMAIN, seo));
        assertThat(DomainValues.digest(Level.SUBDOMAIN, changed(seo, DomainValues.LABEL, "Search")))
                .isNotEqualTo(DomainValues.digest(Level.SUBDOMAIN, seo));
    }

    @Test
    @DisplayName("a new subdomain goes at the place of its order, its rules go under it, and each keeps its fingerprint and its module")
    void seed_addsTheNodesOfAModuleAtTheirPlace() throws RepositoryException {
        Repository repository = new Repository();
        Fake compliance = repository.domain("compliance");
        repository.subdomain(compliance, "readability");
        repository.subdomain(compliance, "terms");
        repository.subdomain(compliance, "writing");
        DomainFile prices = subdomainFile("acme.prices", "compliance", 25, "Prices");
        RuleFile price = rule("acme.price", "acme.prices", "maximum: 60\n");

        seed(repository, shipment(List.of(price, rule("acme.waiting", "acme.later", "maximum: 60\n")),
                List.of(prices, subdomainFile("acme.lost", "acme", 10, "Lost")), true));

        assertThat(compliance.childNames()).containsExactly("readability", "terms", "acme.prices", "writing");
        Fake pricesNode = compliance.child("acme.prices");
        assertThat(pricesNode.properties).containsEntry(Level.SUBDOMAIN.idProperty(), "acme.prices")
                .containsEntry(DomainValues.LABEL, "Prices").containsEntry(TreeSeeder.SEED_MODULE, ACME)
                .containsEntry(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, prices.values()))
                .doesNotContainKeys(DomainValues.DOMAIN, DomainValues.ORDER);
        assertThat(DomainValues.digest(Level.SUBDOMAIN, DomainNodes.read(pricesNode.node, Level.SUBDOMAIN)))
                .as("the parent node gives the domain: a node that nobody changed has the fingerprint of its file")
                .isEqualTo(DomainValues.digest(Level.SUBDOMAIN, prices.values()));
        assertThat(pricesNode.childNames()).containsExactly("acme.price");
        Fake priceNode = pricesNode.child("acme.price");
        assertThat(priceNode.properties).containsEntry(RuleValues.RULE_ID, "acme.price")
                .containsEntry(RuleValues.MAXIMUM, 60.0).containsEntry(TreeSeeder.SEED_MODULE, ACME)
                .containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(price.values()))
                .doesNotContainKey(RuleValues.SUBDOMAIN);
        assertThat(RuleNodes.read(priceNode.node)).containsEntry(RuleValues.SUBDOMAIN, "acme.prices");
        assertThat(RuleValues.digest(RuleNodes.read(priceNode.node))).isEqualTo(RuleValues.digest(price.values()));
        // A subdomain of a domain that the tree lacks, and a rule of a subdomain that the tree lacks, wait
        assertThat(repository.names()).doesNotContain("acme.lost", "acme.waiting");
    }

    @Test
    @DisplayName("the seeder never adds a rule that the system site holds outside the tree")
    void seed_leavesOutARuleThatTheSystemSiteHoldsOutsideTheTree() throws RepositoryException {
        Repository repository = new Repository();
        Fake writing = repository.subdomain(repository.domain("compliance"), "writing");
        Fake folder = repository.contents.add("acme-rules", "jnt:contentFolder");
        folder.add("acme.price", ContentRules.TYPE).properties.put(RuleValues.RULE_ID, "acme.price");

        seed(repository, shipment(List.of(rule("acme.price", "writing", "maximum: 60\n")), List.of(), true));

        assertThat(writing.childNames()).isEmpty();
        assertThat(folder.childNames()).containsExactly("acme.price");
    }

    @Test
    @DisplayName("a node that nobody changed moves when its file moves it, and a node that an administrator moved keeps its place")
    void seed_movesANodeThatNobodyChanged_andKeepsANodeThatAnAdministratorMoved() throws RepositoryException {
        Repository repository = new Repository();
        Fake compliance = repository.domain("compliance");
        Fake quality = repository.domain("quality");
        Fake writing = repository.subdomain(compliance, "writing");
        Fake terms = repository.subdomain(compliance, "terms");
        repository.seeded(compliance, subdomainFile("acme.prices", "compliance", 25, "Prices"));
        repository.seeded(writing, rule("acme.price", "writing", "maximum: 60\n"));
        RuleFile moved = rule("acme.moved", "writing", "maximum: 60\n");
        // An administrator moved the node to terms: the seeder wrote it in writing
        Fake movedNode = repository.seeded(terms, moved);
        RuleFile price = rule("acme.price", "terms", "maximum: 60\n");
        DomainFile prices = subdomainFile("acme.prices", "quality", 25, "Prices");

        seed(repository, shipment(List.of(price, moved), List.of(prices), true));

        assertThat(writing.childNames()).isEmpty();
        assertThat(terms.childNames()).containsExactly("acme.moved", "acme.price");
        assertThat(terms.child("acme.price").properties).containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(price.values()));
        assertThat(movedNode.properties).containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(moved.values()));
        assertThat(compliance.childNames()).doesNotContain("acme.prices");
        assertThat(quality.childNames()).containsExactly("acme.prices");
        assertThat(quality.child("acme.prices").properties)
                .containsEntry(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, prices.values()));
    }

    @Test
    @DisplayName("a node that the module no longer ships goes if nobody changed it and it holds no node, stays marked otherwise, and a node of another module or of nobody stays")
    void seed_retiresTheNodesThatTheModuleNoLongerShips() throws RepositoryException {
        Repository repository = new Repository();
        Fake compliance = repository.domain("compliance");
        Fake writing = repository.subdomain(compliance, "writing");
        Fake gone = repository.seeded(compliance, subdomainFile("acme.gone", "compliance", 50, "Gone"));
        Fake kept = repository.rule(gone, "brand.kept");
        repository.seeded(compliance, subdomainFile("acme.empty", "compliance", 60, "Empty"));
        repository.seeded(writing, rule("acme.old", "writing", "maximum: 60\n"));
        Fake changed = repository.seeded(writing, rule("acme.changed", "writing", "maximum: 60\n"));
        changed.properties.put(RuleValues.MAXIMUM, 70.0);
        Fake other = repository.seeded(writing, rule("other.rule", "writing", "maximum: 60\n"));
        other.properties.put(TreeSeeder.SEED_MODULE, "other");
        Fake own = repository.rule(writing, "brand.own");

        seed(repository, shipment(List.of(), List.of(), false));

        assertThat(writing.childNames()).as("an incomplete folder retires nothing")
                .containsExactly("acme.old", "acme.changed", "other.rule", "brand.own");
        assertThat(compliance.childNames()).contains("acme.gone", "acme.empty");

        seed(repository, shipment(List.of(), List.of(), true));

        assertThat(writing.childNames()).containsExactly("acme.changed", "other.rule", "brand.own");
        assertThat(changed.properties).containsEntry(RuleNodes.RETIRED, true);
        assertThat(other.properties).doesNotContainKey(RuleNodes.RETIRED);
        assertThat(own.properties).doesNotContainKey(RuleNodes.RETIRED);
        assertThat(compliance.childNames()).containsExactly("writing", "acme.gone");
        assertThat(gone.properties).as("it holds a rule of the administrator").containsEntry(RuleNodes.RETIRED, true);
        assertThat(kept.properties).doesNotContainKey(RuleNodes.RETIRED);
    }

    @Test
    @DisplayName("a new version overwrites a subdomain that nobody changed, and keeps its fingerprint up to date")
    void update_overwritesASubdomainThatNobodyChanged() throws RepositoryException {
        Repository repository = new Repository();
        Fake seo = repository.seeded(repository.domain("geo"), subdomainFile("seo", "geo", 20, "SEO"));
        DomainFile shipped = subdomainFile("seo", "geo", 30, "Search engines");

        assertThat(TreeSeeder.update(seo.node, shipped)).isEqualTo(TreeSeeder.Outcome.OVERWRITTEN);

        assertThat(seo.properties).containsEntry(DomainValues.LABEL, "Search engines")
                .containsEntry(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, shipped.values()));
    }

    @Test
    @DisplayName("a new version keeps the values of a subdomain that an administrator changed or moved, and only fills what it lacks")
    void update_keepsASubdomainThatAnAdministratorChanged() throws RepositoryException {
        Repository repository = new Repository();
        DomainFile before = subdomainFile("seo", "geo", 20, "SEO");
        Fake seo = repository.seeded(repository.domain("geo"), before);
        seo.properties.put(DomainValues.LABEL, "Search");

        assertThat(TreeSeeder.update(seo.node, subdomainFile("seo", "geo", 30, "Search engines")))
                .isEqualTo(TreeSeeder.Outcome.UNCHANGED);
        DomainFile counting = DomainFile.parse("---\nid: seo\ndomain: geo\nlabel: Search engines\ncounts: [seo.title]\n---\nThe tags.\n",
                "seo.md", Level.SUBDOMAIN);
        assertThat(TreeSeeder.update(seo.node, counting)).isEqualTo(TreeSeeder.Outcome.FILLED);

        assertThat(seo.properties).containsEntry(DomainValues.LABEL, "Search")
                .containsEntry(DomainValues.COUNTS, new String[]{"seo.title"})
                .containsEntry(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, before.values()));

        // Moved to the domain quality by an administrator: the file of geo does not take it back
        Fake moved = repository.seeded(repository.domain("quality"), subdomainFile("links", "quality", 20, "Links"));
        moved.properties.put(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN,
                subdomainFile("links", "geo", 20, "Links").values()));
        assertThat(TreeSeeder.update(moved.node, subdomainFile("links", "geo", 10, "Links")))
                .isEqualTo(TreeSeeder.Outcome.UNCHANGED);
    }

    @Test
    @DisplayName("a subdomain that the module ships the same values of is unchanged, and a retired one that it ships again comes back")
    void update_bringsBackARetiredSubdomain() throws RepositoryException {
        Repository repository = new Repository();
        DomainFile file = subdomainFile("seo", "geo", 20, "SEO");
        Fake seo = repository.seeded(repository.domain("geo"), file);

        assertThat(TreeSeeder.update(seo.node, file)).isEqualTo(TreeSeeder.Outcome.UNCHANGED);

        seo.properties.put(RuleNodes.RETIRED, true);
        assertThat(TreeSeeder.update(seo.node, file)).isEqualTo(TreeSeeder.Outcome.FILLED);
        assertThat(seo.properties).doesNotContainKey(RuleNodes.RETIRED);
    }

    @Test
    @DisplayName("a new version overwrites a rule that nobody changed, and keeps its fingerprint up to date")
    void update_overwritesARuleThatNobodyChanged() throws RepositoryException {
        Repository repository = new Repository();
        Fake title = repository.seeded(repository.subdomain(repository.domain("geo"), "seo"),
                rule("seo.x", "seo", "maximum: 60\n"));
        RuleFile shipped = rule("seo.x", "seo", "maximum: 50\n");

        assertThat(TreeSeeder.update(title.node, shipped)).isEqualTo(TreeSeeder.Outcome.OVERWRITTEN);

        assertThat(title.properties).containsEntry(RuleValues.MAXIMUM, 50.0)
                .containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(shipped.values()))
                .doesNotContainKey(RuleValues.SUBDOMAIN);
    }

    @Test
    @DisplayName("a rule that an administrator changed keeps its values and gets the ones it lacks, and a change back to the file reads as nobody changed it")
    void update_keepsARuleThatAnAdministratorChanged() throws RepositoryException {
        Repository repository = new Repository();
        RuleFile before = rule("seo.x", "seo", "maximum: 60\n");
        Fake title = repository.seeded(repository.subdomain(repository.domain("geo"), "seo"), before);
        title.properties.put(RuleValues.MAXIMUM, 70.0);

        assertThat(TreeSeeder.update(title.node, rule("seo.x", "seo", "minimum: 10\nmaximum: 50\n")))
                .isEqualTo(TreeSeeder.Outcome.FILLED);
        assertThat(title.properties).containsEntry(RuleValues.MAXIMUM, 70.0).containsEntry(RuleValues.MINIMUM, 10.0)
                .containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(before.values()));

        RuleFile again = rule("seo.x", "seo", "minimum: 10\nmaximum: 70\n");
        assertThat(TreeSeeder.update(title.node, again)).isEqualTo(TreeSeeder.Outcome.FILLED);
        assertThat(title.properties).containsEntry(RuleNodes.SEED_DIGEST, RuleValues.digest(again.values()));
        assertThat(TreeSeeder.update(title.node, again)).isEqualTo(TreeSeeder.Outcome.UNCHANGED);
    }

    @Test
    @DisplayName("a rule that an administrator moved to another subdomain keeps its place, and one that its module moves to the browser takes the shipped evaluator")
    void update_keepsAMovedRule_andFollowsAMoveToTheBrowser() throws RepositoryException {
        Repository repository = new Repository();
        Fake geo = repository.domain("geo");
        Fake links = repository.subdomain(geo, "links");
        RuleFile before = rule("seo.x", "seo", "maximum: 60\n");
        Fake moved = repository.seeded(links, before);

        assertThat(TreeSeeder.update(moved.node, rule("seo.x", "seo", "maximum: 50\n")))
                .isEqualTo(TreeSeeder.Outcome.UNCHANGED);
        assertThat(moved.properties).containsEntry(RuleValues.MAXIMUM, 60.0);

        RuleFile browser = RuleFile.parse("---\nid: seo.x\nevaluator: seo.x\ncheckType: CODE\nsubdomain: seo\nseverity: serious\n"
                + "unit: characters\nmaximum: 60\nruntime: browser\n---\nCheck the price.\n", "seo.x.md");
        assertThat(TreeSeeder.update(moved.node, browser)).isEqualTo(TreeSeeder.Outcome.FILLED);
        assertThat(moved.properties).containsEntry(RuleValues.EVALUATOR, "seo.x").containsEntry(RuleValues.RUNTIME, "browser");
    }

    /** The values with one value changed. */
    private static Map<String, Object> changed(Map<String, Object> values, String key, Object value) {
        Map<String, Object> out = new HashMap<>(values);
        out.put(key, value);
        return out;
    }

    private static RuleFile rule(String id, String subdomain, String limits) {
        return RuleFile.parse("---\nid: " + id + "\nevaluator: threshold\ncheckType: CODE\nsubdomain: " + subdomain
                + "\nseverity: serious\nunit: characters\n" + limits + "---\nCheck the price.\n", id + ".md");
    }

    private static DomainFile subdomainFile(String id, String domain, int order, String label) {
        return DomainFile.parse("---\nid: " + id + "\ndomain: " + domain + "\norder: " + order + "\nlabel: " + label
                + "\n---\nThe tab.\n", id + ".md", Level.SUBDOMAIN);
    }

    private static ShippedRules.Shipment shipment(List<RuleFile> rules, List<DomainFile> subdomains, boolean complete) {
        Map<String, RuleFile> rulesById = new LinkedHashMap<>();
        rules.forEach(rule -> rulesById.put(rule.id(), rule));
        Map<String, DomainFile> subdomainsById = new LinkedHashMap<>();
        subdomains.forEach(subdomain -> subdomainsById.put(subdomain.id(), subdomain));
        return new ShippedRules.Shipment(ACME, rulesById, Map.of(), subdomainsById, complete);
    }

    /** Seeds the repository with a shipment of the module acme, which is started meanwhile. */
    private static void seed(Repository repository, ShippedRules.Shipment shipment) throws RepositoryException {
        ShippedRules.put(shipment);
        try (MockedStatic<JCRTemplate> jcr = mockStatic(JCRTemplate.class);
             MockedStatic<ContentRules> rules = mockStatic(ContentRules.class, CALLS_REAL_METHODS)) {
            // The query of the rule nodes of the system site: a query result of Jahia needs Solr to be mocked
            rules.when(() -> ContentRules.nodes(repository.session, ContentDomains.SYSTEM_SITE))
                    .thenAnswer(invocation -> repository.ruleNodes());
            JCRTemplate template = mock(JCRTemplate.class);
            jcr.when(JCRTemplate::getInstance).thenReturn(template);
            when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                    .thenAnswer(invocation -> invocation.<JCRCallback<?>>getArgument(3).doInJCR(repository.session));
            TreeSeeder.seed(shipment);
        } finally {
            ShippedRules.remove(ACME);
        }
    }

    /**
     * The system site in memory: its folder contents holds the tree content-governance. A session reads it by
     * path and moves a node.
     */
    private static final class Repository {

        private final JCRSessionWrapper session;
        private final Node real;
        private final Fake contents;
        private final Fake tree;

        Repository() throws RepositoryException {
            contents = new Fake(this, null, "contents", "jnt:contentFolder");
            tree = contents.add("content-governance", TreeSeeder.TREE_TYPE);
            NodeIterator noTranslation = mock(NodeIterator.class);
            real = mock(Node.class);
            when(real.getNodes(anyString())).thenReturn(noTranslation);
            session = mock(JCRSessionWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
                case "nodeExists" -> find(invocation.getArgument(0)) != null;
                case "getNode" -> find(invocation.getArgument(0)).node;
                case "move" -> {
                    move(invocation.getArgument(0), invocation.getArgument(1));
                    yield null;
                }
                case "hasPendingChanges" -> true;
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }

        Fake domain(String id) {
            Fake domain = tree.add(id, Level.DOMAIN.type());
            domain.properties.put(Level.DOMAIN.idProperty(), id);
            return domain;
        }

        Fake subdomain(Fake domain, String id) {
            Fake subdomain = domain.add(id, Level.SUBDOMAIN.type());
            subdomain.properties.put(Level.SUBDOMAIN.idProperty(), id);
            return subdomain;
        }

        /** A rule node that an administrator wrote: the seeder never wrote it. */
        Fake rule(Fake parent, String id) {
            Fake rule = parent.add(id, ContentRules.TYPE);
            rule.properties.put(RuleValues.RULE_ID, id);
            return rule;
        }

        /** A subdomain node as the seeder of the module acme wrote it from the file. */
        Fake seeded(Fake domain, DomainFile file) throws RepositoryException {
            Fake subdomain = domain.add(file.id(), Level.SUBDOMAIN.type());
            DomainNodes.write(subdomain.node, Level.SUBDOMAIN, file.values(), true);
            subdomain.properties.put(RuleNodes.SEED_DIGEST, DomainValues.digest(Level.SUBDOMAIN, file.values()));
            subdomain.properties.put(TreeSeeder.SEED_MODULE, ACME);
            return subdomain;
        }

        /** A rule node as the seeder of the module acme wrote it from the file. */
        Fake seeded(Fake subdomain, RuleFile file) throws RepositoryException {
            Fake rule = subdomain.add(file.id(), ContentRules.TYPE);
            RuleNodes.write(rule.node, file.values(), true);
            rule.properties.put(RuleNodes.SEED_DIGEST, RuleValues.digest(file.values()));
            rule.properties.put(TreeSeeder.SEED_MODULE, ACME);
            return rule;
        }

        /** The names of every node under contents. */
        List<String> names() {
            List<String> out = new ArrayList<>();
            collect(contents, out);
            return out;
        }

        private static void collect(Fake node, List<String> out) {
            for (Fake child : node.children) {
                out.add(child.name);
                collect(child, out);
            }
        }

        /** Every rule node under contents, as {@link ContentRules#nodes} gives them. */
        List<JCRNodeWrapper> ruleNodes() {
            return rules(contents, new ArrayList<>());
        }

        private static List<JCRNodeWrapper> rules(Fake node, List<JCRNodeWrapper> out) {
            for (Fake child : node.children) {
                if (child.types.contains(ContentRules.TYPE)) {
                    out.add(child.node);
                }
                rules(child, out);
            }
            return out;
        }

        private Fake find(String path) {
            if (!path.equals(CONTENTS) && !path.startsWith(CONTENTS + "/")) {
                return null;
            }
            Fake node = contents;
            for (String name : path.substring(CONTENTS.length()).split("/")) {
                if (!name.isEmpty()) {
                    node = node == null ? null : node.child(name);
                }
            }
            return node;
        }

        private void move(String from, String to) {
            Fake node = find(from);
            Fake target = find(to.substring(0, to.lastIndexOf('/')));
            node.parent.children.remove(node);
            node.parent = target;
            target.children.add(node);
        }
    }

    /** A node in memory: a mock of JCRNodeWrapper reads and writes its types, its properties and its child nodes in order. */
    private static final class Fake implements Answer<Object> {

        private final Repository repository;
        private final String name;
        private final Set<String> types = new HashSet<>();
        private final Map<String, Object> properties = new HashMap<>();
        private final List<Fake> children = new ArrayList<>();
        private final JCRNodeWrapper node;
        private Fake parent;

        Fake(Repository repository, Fake parent, String name, String type) {
            this.repository = repository;
            this.parent = parent;
            this.name = name;
            types.add(type);
            node = mock(JCRNodeWrapper.class, this);
        }

        Fake add(String childName, String type) {
            Fake child = new Fake(repository, this, childName, type);
            children.add(child);
            return child;
        }

        Fake child(String childName) {
            return children.stream().filter(child -> child.name.equals(childName)).findFirst().orElse(null);
        }

        List<String> childNames() {
            return children.stream().map(child -> child.name).toList();
        }

        private String path() {
            return parent == null ? CONTENTS : parent.path() + "/" + name;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getName" -> name;
                case "getPath", "toString" -> path();
                case "getParent" -> parent == null ? null : parent.node;
                case "isNodeType" -> types.contains((String) args[0]);
                case "addMixin" -> {
                    types.add((String) args[0]);
                    yield null;
                }
                case "removeMixin" -> {
                    types.remove((String) args[0]);
                    yield null;
                }
                case "getNodes" -> iterator(children.stream().map(child -> child.node).toList());
                case "hasNodes" -> !children.isEmpty();
                case "hasNode" -> child((String) args[0]) != null;
                case "getNode" -> child((String) args[0]).node;
                case "addNode" -> add((String) args[0], (String) args[1]).node;
                case "orderBefore" -> {
                    Fake moved = child((String) args[0]);
                    children.remove(moved);
                    children.add(children.indexOf(child((String) args[1])), moved);
                    yield null;
                }
                case "hasProperty" -> properties.containsKey((String) args[0]);
                case "getProperty" -> property((String) args[0]);
                case "setProperty" -> {
                    properties.put((String) args[0], args[1]);
                    yield null;
                }
                case "remove" -> {
                    parent.children.remove(this);
                    yield null;
                }
                case "getSession" -> repository.session;
                case "getRealNode" -> repository.real;
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        }

        private JCRPropertyWrapper property(String key) {
            Object value = properties.get(key);
            return mock(JCRPropertyWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
                case "getString" -> String.valueOf(value);
                case "getLong" -> ((Number) value).longValue();
                case "getDouble" -> ((Number) value).doubleValue();
                case "getBoolean" -> value;
                case "getValues" -> values((String[]) value);
                case "remove" -> {
                    properties.remove(key);
                    yield null;
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }

        private static JCRValueWrapper[] values(String[] items) {
            JCRValueWrapper[] out = new JCRValueWrapper[items.length];
            for (int i = 0; i < items.length; i++) {
                String item = items[i];
                out[i] = mock(JCRValueWrapper.class, invocation -> "getString".equals(invocation.getMethod().getName())
                        ? item : RETURNS_DEFAULTS.answer(invocation));
            }
            return out;
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
