package org.jahia.community.contentgovernance.audit.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The number of issues per tab id of one stored run, computed on the server from what the run
 * stores, never taken from the client. A Page quality audit counts the recommendations of each of its
 * tabs ({@code accessibility}, {@code seo}...), and for accessibility the violations of axe-core too. A
 * run of another tab of the drawer counts its findings under the id of that tab.
 *
 * @param counts               issues per tab id, for the tabs the run measured
 * @param total                the sum of the counts
 * @param seriousAccessibility accessibility issues of impact {@code critical} or {@code serious}, or -1
 *                             when unknown (a run of another tab, or a summary stored before it was counted)
 */
public record AuditSummary(Map<String, Integer> counts, int total, int seriousAccessibility) {

    private static final Set<String> SERIOUS = Set.of("critical", "serious");
    private static final String SERIOUS_KEY = "seriousAccessibility";
    private static final String COUNTS_KEY = "counts";
    private static final String TOTAL_KEY = "total";
    private static final String VIOLATIONS = "violations";
    private static final String RECOMMENDATIONS = "recommendations";

    /** Tab id to the key of its result in the Page quality results, in the drawer's order. */
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
     * @param results Page quality results ({@code {a11y: {violations: [...]}, seo: {recommendations: [...]}, ...}})
     * @return the counts of the tabs present in {@code results}
     */
    public static AuditSummary of(JSONObject results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        JSONObject a11y = results.optJSONObject("a11y");
        int serious = a11y == null ? 0 : serious(a11y.optJSONArray(VIOLATIONS), "impact")
                + serious(a11y.optJSONArray(RECOMMENDATIONS), "severity");
        for (Map.Entry<String, String> tab : TAB_RESULTS.entrySet()) {
            JSONObject result = results.optJSONObject(tab.getValue());
            if (result != null) {
                int count = length(result.optJSONArray(RECOMMENDATIONS));
                if ("accessibility".equals(tab.getKey())) {
                    count += length(result.optJSONArray(VIOLATIONS));
                }
                counts.put(tab.getKey(), count);
                total += count;
            }
        }
        return new AuditSummary(Collections.unmodifiableMap(counts), total, serious);
    }

    private static int length(JSONArray issues) {
        return issues == null ? 0 : issues.length();
    }

    /** The issues whose impact, under the key, is critical or serious. */
    private static int serious(JSONArray issues, String key) {
        int serious = 0;
        for (int i = 0; issues != null && i < issues.length(); i++) {
            JSONObject issue = issues.optJSONObject(i);
            if (issue != null && SERIOUS.contains(issue.optString(key))) {
                serious++;
            }
        }
        return serious;
    }

    /**
     * @param tab      the id of the tab that found them
     * @param findings the findings the run stores
     * @return one count, under the id of the tab
     */
    public static AuditSummary ofFindings(String tab, JSONArray findings) {
        return new AuditSummary(Map.of(tab, findings.length()), findings.length(), -1);
    }

    /** @return the summary as stored: {@code {"counts": {...}, "total": n, "seriousAccessibility": n}} */
    public String toJson() {
        return new JSONObject().put(COUNTS_KEY, new JSONObject(counts)).put(TOTAL_KEY, total)
                .put(SERIOUS_KEY, seriousAccessibility).toString();
    }

    /**
     * @param json a stored summary ({@link #toJson()})
     * @return the summary, its counts in the order of {@link #TAB_RESULTS} then by tab id; empty counts
     *     when {@code json} is missing or unreadable
     */
    public static AuditSummary fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new AuditSummary(Map.of(), 0, -1);
        }
        JSONObject object = new JSONObject(json);
        JSONObject stored = object.optJSONObject(COUNTS_KEY);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (stored != null) {
            List<String> tabs = new ArrayList<>(TAB_RESULTS.keySet());
            tabs.retainAll(stored.keySet());
            stored.keySet().stream().filter(tab -> !TAB_RESULTS.containsKey(tab)).sorted().forEach(tabs::add);
            for (String tab : tabs) {
                counts.put(tab, stored.optInt(tab));
            }
        }
        return new AuditSummary(Collections.unmodifiableMap(counts), object.optInt(TOTAL_KEY), object.optInt(SERIOUS_KEY, -1));
    }
}
