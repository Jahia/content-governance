package org.jahia.community.contentgovernance.audit.service.internal;

import java.util.Collection;
import org.jahia.community.contentgovernance.audit.model.AuditSummary;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Checks the results an editor's browser sends before they are stored: a JSON object of bounded
 * size, keeping only the result of each tab and the page facts. What other editors later read is
 * rendered as text by the drawer, never as markup.
 *
 * <p>Thread-safety: stateless.</p>
 */
public final class AuditResults {

    /** The largest results accepted, in characters: an audit with every tab is a few tens of kB. */
    public static final int MAX_CHARS = 1_000_000;
    private static final String PAGE_KEY = "page";

    private AuditResults() {
    }

    /**
     * @param json the results as sent by the drawer
     * @return the results reduced to the tab results and the page facts
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
        return kept;
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
