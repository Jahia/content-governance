package org.jahia.community.contentgovernance.audit.service.internal;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jahia.community.contentgovernance.audit.model.AuditSummary;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Checks the results an editor's browser sends before they are stored: a JSON object of bounded
 * size, keeping only the result of each tab, the page facts and the version of their shape. What other editors later read is
 * rendered as text by the drawer, never as markup.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class AuditResults {

    /** The largest results accepted, in characters: an audit with every tab is a few tens of kB. */
    public static final int MAX_CHARS = 1_000_000;
    /** The most findings a tab stores in one run. */
    public static final int MAX_FINDINGS = 500;
    /** The longest text of a finding that is stored, in characters. */
    public static final int MAX_FIELD_CHARS = 2_000;
    /** The key of the findings in what a run of a tab stores. */
    public static final String FINDINGS = "findings";
    private static final String PAGE_KEY = "page";
    private static final String VERSION = "version";
    private static final String KEY = "key";
    private static final String SEVERITY = "severity";
    private static final String COUNT = "count";
    private static final List<String> TEXT_FIELDS = List.of(KEY, "rule", "tab", "title", "detail");
    private static final Set<String> SEVERITIES = Set.of("critical", "serious", "moderate", "minor");

    private AuditResults() {
    }

    /**
     * @param json the results as sent by the drawer
     * @return the results reduced to the tab results, the page facts and the version of their shape
     * @throws IllegalArgumentException when {@code json} is missing, too large, not a JSON object, or
     *                                  holds no tab result
     */
    public static JSONObject parse(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("No results");
        }
        if (json.length() > MAX_CHARS) {
            throw new IllegalArgumentException("The results are too large");
        }
        JSONObject sent;
        try {
            sent = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("The results are not a JSON object");
        }
        JSONObject kept = new JSONObject();
        copyObjects(sent, kept, AuditSummary.TAB_RESULTS.values());
        if (kept.isEmpty()) {
            throw new IllegalArgumentException("The results hold no tab");
        }
        JSONObject page = sent.optJSONObject(PAGE_KEY);
        if (page != null) {
            kept.put(PAGE_KEY, page);
        }
        if (sent.opt(VERSION) instanceof Number version) {
            kept.put(VERSION, version.intValue());
        }
        return kept;
    }

    /**
     * Checks the findings that a tab of the drawer stores as a run of its history.
     *
     * @param json the findings as sent by the drawer: a JSON array of {@code {key, rule?, tab?, severity?,
     *             title?, detail?, count?}}
     * @return {@code {"findings": [...]}}, each finding reduced to those fields, its texts cut to
     *     {@link #MAX_FIELD_CHARS}, and a severity the drawer does not know replaced by {@code minor}
     * @throws IllegalArgumentException when {@code json} is missing, too large, not a JSON array of
     *                                  objects, holds more than {@link #MAX_FINDINGS}, or a finding has no key
     */
    public static JSONObject parseFindings(String json) {
        JSONArray sent = findingsArray(json);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < sent.length(); i++) {
            kept.put(finding(sent.optJSONObject(i)));
        }
        return new JSONObject().put(FINDINGS, kept);
    }

    /** The findings as a JSON array of bounded size, or the reason they are refused. */
    private static JSONArray findingsArray(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("No findings");
        }
        if (json.length() > MAX_CHARS) {
            throw new IllegalArgumentException("The findings are too large");
        }
        JSONArray sent;
        try {
            sent = new JSONArray(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("The findings are not a JSON array");
        }
        if (sent.length() > MAX_FINDINGS) {
            throw new IllegalArgumentException("Too many findings");
        }
        return sent;
    }

    /** One finding reduced to the fields a run stores, or the reason it is refused. */
    private static JSONObject finding(JSONObject sent) {
        if (sent == null || sent.optString(KEY).isEmpty()) {
            throw new IllegalArgumentException("A finding has no key");
        }
        JSONObject out = new JSONObject();
        for (String field : TEXT_FIELDS) {
            if (sent.opt(field) instanceof String text) {
                out.put(field, text.length() > MAX_FIELD_CHARS ? text.substring(0, MAX_FIELD_CHARS) : text);
            }
        }
        String severity = sent.optString(SEVERITY);
        out.put(SEVERITY, SEVERITIES.contains(severity) ? severity : "minor");
        if (sent.opt(COUNT) instanceof Number count) {
            out.put(COUNT, count.intValue());
        }
        return out;
    }

    private static void copyObjects(JSONObject from, JSONObject to, Collection<String> keys) {
        for (String key : keys) {
            JSONObject value = from.optJSONObject(key);
            if (value != null) {
                to.put(key, value);
            }
        }
    }
}
