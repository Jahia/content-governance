/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.jahia.modules.mcp.spi.tool.ToolContext;
import org.jahia.modules.mcp.spi.tool.ToolError;
import org.jahia.modules.mcp.spi.tool.ToolResult;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditToolsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode input(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void readsAPathUnderASiteAndALanguage() throws Exception {
        AuditTools.Request request = AuditTools.read(input("{\"path\":\"/sites/digitall/home/about\",\"locale\":\"fr_FR\"}"));
        assertNotNull(request);
        assertEquals("digitall", request.siteKey());
        assertEquals("/sites/digitall/home/about", request.path());
    }

    @Test
    void refusesAPathOutsideASiteAndAnInvalidLanguage() throws Exception {
        assertNull(AuditTools.read(input("{\"path\":\"/modules/content-governance\",\"locale\":\"en\"}")));
        assertNull(AuditTools.read(input("{\"path\":\"/sites/digitall/../systemsite\",\"locale\":\"en\"}")));
        assertNull(AuditTools.read(input("{\"path\":\"/sites/digitall/home\",\"locale\":\"en'><x\"}")));
        assertNull(AuditTools.read(input("{\"path\":\"/sites/digitall/home\"}")));
    }

    @Test
    void answersAnInvalidInputWithoutASessionAndRecordsTheCall() throws Exception {
        HtmlCheck check = mock(HtmlCheck.class);
        ToolContext ctx = mock(ToolContext.class);
        List<String> events = new ArrayList<>();
        when(ctx.audit()).thenReturn((event, details) -> events.add(event));

        ToolResult result = new AccessibilityAuditTool(check).execute(input("{\"path\":\"/x\",\"locale\":\"en\"}"), ctx);

        assertEquals(ToolError.CODE_VALIDATION, result.error().code());
        assertEquals(List.of("audit.accessibility"), events);
        verifyNoInteractions(check);
    }

    @Test
    void reportsEachFindingWithItsRuleCriterionAndScope() throws Exception {
        HtmlCheck.Rendered rendered = new HtmlCheck.Rendered("/sites/digitall/home", "jnt:page", Scope.PAGE,
                Jsoup.parse(""), Set.of("localhost"));
        AuditTools.Request request = new AuditTools.Request("/sites/digitall/home", "digitall", "en");

        JsonNode report = AccessibilityAuditTool.report(rendered, request, "default", List.of(
                new Finding("html.imageAltMissing", "critical", "<img src=\"a.png\">", null),
                new Finding("html.imageAltMissing", "critical", "<img src=\"b.png\">", null),
                new Finding("html.headingH1Missing", "moderate", "<h2>", null),
                new Finding("acme.erpPrice", "serious", "12.50 EUR", null)));

        assertEquals("page", report.path("scope").asText());
        assertEquals(4, report.path("findingCount").asInt());
        JsonNode first = report.path("findings").get(0);
        assertEquals(Map.of("rule", "html.imageAltMissing", "severity", "critical", "wcag", "1.1.1", "scope", "item",
                        "element", "<img src=\"a.png\">"),
                MAPPER.convertValue(first, Map.class));
        assertEquals(2, report.path("countByRule").path("html.imageAltMissing").asInt());
        assertEquals(Map.of("rule", "acme.erpPrice", "severity", "serious", "element", "12.50 EUR"),
                MAPPER.convertValue(report.path("findings").get(3), Map.class), "a rule of another module has no criterion");
    }
}
