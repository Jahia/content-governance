package org.jahia.community.contentgovernance.rules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The base catalog that the module ships: every rule with its default, read once from
 * {@value #RESOURCE}. The seeder copies it into each site, and a site changes its copy.
 */
public final class RuleCatalog {

    static final String RESOURCE = "/META-INF/content-rules/catalog.json";

    private static final Map<String, ContentRule> BASE = load();

    private RuleCatalog() {
    }

    /** Every rule of the module with its default, in the order of the catalog file. */
    public static Map<String, ContentRule> base() {
        return BASE;
    }

    private static Map<String, ContentRule> load() {
        try (InputStream in = RuleCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The module has no rule catalog at " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("The rule catalog could not be read", e);
        }
    }

    /**
     * @param json the text of a catalog file
     * @return its rules by id
     * @throws IllegalArgumentException when a rule repeats an id, or has a domain, a severity or a fixer
     *                                  that the catalog does not allow
     */
    static Map<String, ContentRule> parse(String json) {
        JSONArray rules = new JSONObject(json).getJSONArray("rules");
        Map<String, ContentRule> out = new LinkedHashMap<>();
        for (int i = 0; i < rules.length(); i++) {
            ContentRule rule = rule(rules.getJSONObject(i));
            if (out.put(rule.id(), rule) != null) {
                throw new IllegalArgumentException("The rule catalog repeats the id " + rule.id());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static ContentRule rule(JSONObject o) {
        String id = o.getString("id");
        String domain = o.getString("domain");
        if (!id.startsWith(domain + ".")) {
            throw new IllegalArgumentException("The id of the rule " + id + " does not start with its domain " + domain);
        }
        String severity = o.getString("severity");
        if (!ContentRule.SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown severity " + severity);
        }
        String fixer = o.getString("fixer");
        if (!ContentRule.FIXERS.contains(fixer)) {
            throw new IllegalArgumentException("The rule " + id + " has the unknown fixer " + fixer);
        }
        Double minimum = o.has("minimum") ? o.getDouble("minimum") : null;
        Double maximum = o.has("maximum") ? o.getDouble("maximum") : null;
        String unit = o.optString("unit", null);
        if ((minimum != null || maximum != null) && unit == null) {
            throw new IllegalArgumentException("The rule " + id + " has a limit and no unit");
        }
        return new ContentRule(id, domain, severity, o.optBoolean("enabled", true), fixer,
                o.getString("advice"), unit, minimum, maximum);
    }
}
