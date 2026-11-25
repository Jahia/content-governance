package org.jahia.community.contentgovernance.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jcr.RepositoryException;
import org.jahia.community.contentgovernance.audit.model.AuditRun;
import org.jahia.community.contentgovernance.audit.service.internal.AuditHistoryServiceImpl;
import org.jahia.community.contentgovernance.audit.service.internal.ManualResults;
import org.jahia.community.contentgovernance.audit.service.spi.AuditHistoryService;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ManualRunsStoreTest {

    private static final String SITE = "/sites/digitall";
    private static final String PAGE_ID = "8f1c";

    @Test
    @DisplayName("the browser results keep the rules of the run only, once each, with bounded findings and their own severity")
    void browserResults_areChecked() {
        String sent = new JSONArray()
                .put(new JSONObject().put("ruleId", "acme.alt").put("passed", true)
                        .put("findings", new JSONArray().put(new JSONObject().put("text", "<img>").put("severity", "critical")
                                .put("locator", "main > img").put("script", "x"))))
                .put(new JSONObject().put("ruleId", "acme.alt").put("passed", true))
                .put(new JSONObject().put("ruleId", "seo.title").put("passed", false))
                .toString();

        JSONArray kept = ManualResults.ofBrowser(sent, Map.of("acme.alt", "minor"));

        assertThat(kept.length()).isEqualTo(1);
        JSONObject result = kept.getJSONObject(0);
        assertThat(result.getBoolean("passed")).as("a result with a finding fails").isFalse();
        JSONObject finding = result.getJSONArray("findings").getJSONObject(0);
        assertThat(finding.getString("severity")).isEqualTo("critical");
        assertThat(finding.keySet()).containsExactlyInAnyOrder("ruleId", "severity", "text", "locator", "values");
        assertThat(ManualResults.ofBrowser(null, Map.of())).isEmpty();
        assertThatThrownBy(() -> ManualResults.ofBrowser("{}", Map.of())).hasMessage("The results are not a JSON array");
    }

    @Test
    @DisplayName("a run on demand is stored under the tab of its subdomain, counts its failed rules, and reads back newest first")
    void manualRun_isStoredAndReadBack() throws RepositoryException {
        Fake site = new Fake("digitall");
        Fake store = site.add(SiteFeatures.STORE_NODE);
        JCRSessionWrapper session = mock(JCRSessionWrapper.class, invocation -> "getNode".equals(invocation.getMethod().getName())
                ? site.node : RETURNS_DEFAULTS.answer(invocation));
        AuditHistoryServiceImpl service = new AuditHistoryServiceImpl();
        JSONObject first = ManualResults.stored(ManualResults.ofServer(List.of(
                new CheckResult("acme.tone", false, Map.of("score", 40), List.of(new Finding("acme.tone", "minor", "Hey", null))),
                new CheckResult("acme.length", true, Map.of(), List.of()))));
        JSONObject second = ManualResults.stored(ManualResults.ofServer(List.of(
                new CheckResult("acme.tone", true, Map.of("score", 90), List.of()))));

        try (MockedStatic<JCRTemplate> jcr = mockStatic(JCRTemplate.class);
             MockedStatic<SiteFeatures> features = mockStatic(SiteFeatures.class)) {
            JCRTemplate template = mock(JCRTemplate.class);
            jcr.when(JCRTemplate::getInstance).thenReturn(template);
            when(template.doExecuteWithSystemSessionAsUser(any(), any(), any(), any()))
                    .thenAnswer(invocation -> invocation.<JCRCallback<?>>getArgument(3).doInJCR(session));
            features.when(() -> SiteFeatures.storeNode(site.node)).thenReturn(store.node);

            AuditRun stored = service.saveManualRun(SITE, PAGE_ID, "en", "writing", "jane", first);
            // The runs are ordered by the millisecond they were stored at, so the first one moves a second back
            store.shiftTime("auditedAt", -1000);
            service.saveManualRun(SITE, PAGE_ID, "en", "writing", "joe", second);
            List<AuditRun> runs = service.history(SITE, PAGE_ID, "en", AuditHistoryService.manualTab("writing"));

            assertThat(stored.tab()).isEqualTo("manual:writing");
            assertThat(stored.summary().total()).isEqualTo(1);
            assertThat(new JSONObject(stored.results()).getJSONArray("results").getJSONObject(0)
                    .getJSONObject("values").getInt("score")).isEqualTo(40);
            assertThat(runs).extracting(AuditRun::auditedBy).containsExactly("joe", "jane");
            assertThat(runs.get(0).summary().total()).isZero();
            assertThat(new JSONObject(runs.get(0).results()).getJSONArray("results").length()).isEqualTo(1);
            assertThat(runs.get(1).results()).as("only the newest carries its results").isNull();
            assertThat(service.history(SITE, PAGE_ID, "en", AuditHistoryService.manualTab("seo"))).isEmpty();
            assertThat(service.history(SITE, PAGE_ID, "en", AuditHistoryService.AUDIT_TAB)).isEmpty();
        }
    }

    /** A node in memory: a mock of JCRNodeWrapper reads and writes its properties and its child nodes in order. */
    private static final class Fake implements Answer<Object> {

        private final String name;
        private final Map<String, Object> properties = new HashMap<>();
        private final List<Fake> children = new ArrayList<>();
        private final JCRNodeWrapper node;
        private Fake parent;

        Fake(String name) {
            this.name = name;
            node = mock(JCRNodeWrapper.class, this);
        }

        Fake add(String childName) {
            Fake child = new Fake(childName);
            child.parent = this;
            children.add(child);
            return child;
        }

        /** Adds {@code delta} to the long property {@code property} of this node and of every descendant that holds it. */
        void shiftTime(String property, long delta) {
            if (properties.get(property) instanceof Number value) {
                properties.put(property, value.longValue() + delta);
            }
            children.forEach(child -> child.shiftTime(property, delta));
        }

        private Fake child(String childName) {
            return children.stream().filter(child -> child.name.equals(childName)).findFirst().orElse(null);
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getName" -> name;
                case "hasNode" -> child((String) args[0]) != null;
                case "getNode" -> child((String) args[0]).node;
                case "addNode" -> add((String) args[0]).node;
                case "hasProperty" -> properties.containsKey((String) args[0]);
                case "setProperty" -> {
                    properties.put((String) args[0], args[1]);
                    yield null;
                }
                case "getProperty" -> property(properties.get((String) args[0]), invocation.getMethod().getReturnType());
                case "getNodes" -> iterator(invocation.getMethod().getReturnType(), new ArrayList<>(children).iterator());
                case "remove" -> {
                    parent.children.remove(this);
                    yield null;
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        }

        private static Object property(Object value, Class<?> type) {
            return mock(type, invocation -> switch (invocation.getMethod().getName()) {
                case "getString" -> String.valueOf(value);
                case "getLong" -> ((Number) value).longValue();
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }

        private static Object iterator(Class<?> type, Iterator<Fake> nodes) {
            return mock(type, invocation -> switch (invocation.getMethod().getName()) {
                case "hasNext" -> nodes.hasNext();
                case "next", "nextNode" -> nodes.next().node;
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
        }
    }
}
