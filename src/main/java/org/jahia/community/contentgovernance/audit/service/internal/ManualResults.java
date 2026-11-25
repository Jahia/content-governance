package org.jahia.community.contentgovernance.audit.service.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a run of the rules on demand of a subdomain stores: the result of each rule, in the shape of the results of
 * {@code checkPage}, as {@code {"results": [{ruleId, passed, values, findings: [{ruleId, severity, text, context,
 * locator, values}]}]}}. The server gives the results of its rules, and the browser of the editor sends the results
 * of the rules of the browser, which are checked here first.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class ManualResults {

    /** The key of the results in what a run stores. */
    public static final String RESULTS = "results";
    private static final String RULE_ID = "ruleId";
    private static final String PASSED = "passed";
    private static final String VALUES = "values";
    private static final String FINDINGS = "findings";
    private static final String SEVERITY = "severity";
    private static final List<String> FINDING_TEXTS = List.of("text", "context", "locator");
    private static final Set<String> SEVERITIES = Set.of("critical", "serious", "moderate", "minor");

    private ManualResults() {
    }

    /**
     * @param results the results of the rules of the server, as the registry gives them
     * @return the results as a run stores them, in the same order
     */
    public static JSONArray ofServer(List<CheckResult> results) {
        JSONArray out = new JSONArray();
        for (CheckResult result : results) {
            JSONArray findings = new JSONArray();
            for (Finding finding : result.findings()) {
                findings.put(new JSONObject().put(RULE_ID, result.ruleId()).put(SEVERITY, finding.severity())
                        .put("text", finding.text()).putOpt("context", finding.context())
                        .putOpt("locator", finding.locator()).put(VALUES, new JSONObject(finding.values())));
            }
            out.put(new JSONObject().put(RULE_ID, result.ruleId()).put(PASSED, result.passed())
                    .put(VALUES, new JSONObject(result.values())).put(FINDINGS, findings));
        }
        return out;
    }

    /**
     * Checks the results that the browser of the editor sends for the rules of the browser.
     *
     * @param json       the results as a JSON array of {@code {ruleId, passed?, values?, findings?}}, or null
     * @param severities the severity of each rule of the browser that the run may hold, by rule id
     * @return the results of these rules, one per rule, the first one sent: each keeps its values, its first
     *     {@value CheckRegistry#MAX_FINDINGS} findings with their texts cut to {@link AuditResults#MAX_FIELD_CHARS},
     *     and fails when it has a finding
     * @throws IllegalArgumentException when {@code json} is too large or not a JSON array
     */
    public static JSONArray ofBrowser(String json, Map<String, String> severities) {
        JSONArray out = new JSONArray();
        if (json == null || json.isBlank()) {
            return out;
        }
        if (json.length() > AuditResults.MAX_CHARS) {
            throw new IllegalArgumentException("The results are too large");
        }
        JSONArray sent;
        try {
            sent = new JSONArray(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("The results are not a JSON array");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < sent.length(); i++) {
            JSONObject result = sent.optJSONObject(i);
            String ruleId = result == null ? null : result.optString(RULE_ID, null);
            if (ruleId != null && severities.containsKey(ruleId) && seen.add(ruleId)) {
                out.put(browserResult(result, ruleId, severities.get(ruleId)));
            }
        }
        return out;
    }

    /** One result of the browser, reduced to the fields that a run stores. */
    private static JSONObject browserResult(JSONObject sent, String ruleId, String severity) {
        JSONArray sentFindings = sent.optJSONArray(FINDINGS);
        JSONArray findings = new JSONArray();
        for (int i = 0; sentFindings != null && i < sentFindings.length() && findings.length() < CheckRegistry.MAX_FINDINGS; i++) {
            JSONObject finding = sentFindings.optJSONObject(i);
            if (finding != null) {
                findings.put(browserFinding(finding, ruleId, severity));
            }
        }
        boolean passed = sent.opt(PASSED) instanceof Boolean flag ? flag : findings.isEmpty();
        JSONObject values = sent.optJSONObject(VALUES);
        return new JSONObject().put(RULE_ID, ruleId).put(PASSED, passed && findings.isEmpty())
                .put(VALUES, values == null ? new JSONObject() : values).put(FINDINGS, findings);
    }

    private static JSONObject browserFinding(JSONObject sent, String ruleId, String severity) {
        // A check may give a finding its own severity, such as the one a model judges
        String own = sent.optString(SEVERITY);
        JSONObject out = new JSONObject().put(RULE_ID, ruleId).put(SEVERITY, SEVERITIES.contains(own) ? own : severity);
        for (String field : FINDING_TEXTS) {
            if (sent.opt(field) instanceof String text) {
                out.put(field, text.length() > AuditResults.MAX_FIELD_CHARS ? text.substring(0, AuditResults.MAX_FIELD_CHARS) : text);
            }
        }
        if (!out.has("text")) {
            out.put("text", "");
        }
        JSONObject values = sent.optJSONObject(VALUES);
        return out.put(VALUES, values == null ? new JSONObject() : values);
    }

    /**
     * @param results the results of the server, then those of the browser
     * @return what a run stores
     */
    public static JSONObject stored(JSONArray results) {
        return new JSONObject().put(RESULTS, results);
    }

    /**
     * @param stored what a run stores
     * @return the results that failed, which the drawer counts as issues
     */
    public static int failed(JSONObject stored) {
        JSONArray results = stored.optJSONArray(RESULTS);
        int failed = 0;
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            if (result != null && !result.optBoolean(PASSED, true)) {
                failed++;
            }
        }
        return failed;
    }
}
