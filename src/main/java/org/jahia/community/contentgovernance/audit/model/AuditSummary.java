package org.jahia.community.contentgovernance.audit.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The number of issues per tab of one Page quality audit, as the drawer's badges count them: the
 * accessibility violations, and the recommendations of every other tab. Computed on the server from
 * the stored results, never taken from the client.
 *
 * @param counts               issues per tab id ({@code accessibility}, {@code seo}...), for the tabs the audit measured
 * @param total                the sum of the counts
 * @param seriousAccessibility accessibility violations of impact {@code critical} or {@code serious}, or -1
 *                             when unknown (a summary stored before it was counted)
 */
public record AuditSummary(Map<String, Integer> counts, int total, int seriousAccessibility) {

    private static final Set<String> SERIOUS = Set.of("critical", "serious");
    private static final String SERIOUS_KEY = "seriousAccessibility";

    /** Tab id to the key of its result in the audit results, in the drawer's order. */
    public static final Map<String, String> TAB_RESULTS = tabResults();

    private static Map<String, String> tabResults() {
        Map<String, String> tabs = new LinkedHashMap<>();
        tabs.put("accessibility", "a11y");
        tabs.put("seo", "seo");
        tabs.put("vitals", "vitals");
        tabs.put("readability", "readability");
        tabs.put("ecodesign", "ecodesign");
        tabs.put("links", "links");
        tabs.put("jahia", "jahia");
        return Collections.unmodifiableMap(tabs);
    }

    /**
     * @param results audit results ({@code {a11y: {violations: [...]}, seo: {recommendations: [...]}, ...}})
     * @return the counts of the tabs present in {@code results}
     */
    public static AuditSummary of(JSONObject results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        int serious = 0;
        JSONObject a11y = results.optJSONObject("a11y");
        JSONArray violations = a11y == null ? null : a11y.optJSONArray("violations");
        for (int i = 0; violations != null && i < violations.length(); i++) {
            JSONObject violation = violations.optJSONObject(i);
            if (violation != null && SERIOUS.contains(violation.optString("impact"))) {
                serious++;
            }
        }
        for (Map.Entry<String, String> tab : TAB_RESULTS.entrySet()) {
            JSONObject result = results.optJSONObject(tab.getValue());
            if (result != null) {
                JSONArray issues = result.optJSONArray("accessibility".equals(tab.getKey()) ? "violations" : "recommendations");
                int count = issues == null ? 0 : issues.length();
                counts.put(tab.getKey(), count);
                total += count;
            }
        }
        return new AuditSummary(Collections.unmodifiableMap(counts), total, serious);
    }

    /** @return the summary as stored: {@code {"counts": {...}, "total": n}} */
    public String toJson() {
        return new JSONObject().put("counts", new JSONObject(counts)).put("total", total)
                .put(SERIOUS_KEY, seriousAccessibility).toString();
    }

    /**
     * @param json a stored summary ({@link #toJson()})
     * @return the summary; empty counts when {@code json} is missing or unreadable
     */
    public static AuditSummary fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new AuditSummary(Map.of(), 0, -1);
        }
        JSONObject object = new JSONObject(json);
        JSONObject stored = object.optJSONObject("counts");
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (stored != null) {
            for (String tab : TAB_RESULTS.keySet()) {
                if (stored.has(tab)) {
                    counts.put(tab, stored.optInt(tab));
                }
            }
        }
        return new AuditSummary(Collections.unmodifiableMap(counts), object.optInt("total"), object.optInt(SERIOUS_KEY, -1));
    }
}
