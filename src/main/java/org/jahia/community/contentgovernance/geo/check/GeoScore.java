package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Turns the raw report into a small set of pass/fail checks.
 *
 * Deliberately NOT a 0-100 number. Nothing behind such a number would be
 * defensible, and a score an editor cannot argue with is a score they cannot
 * act on. Every check here points at one observed fact in the report, and the
 * UI shows that fact next to the verdict.
 *
 * Three severities:
 *   critical  - an AI crawler cannot read this page at all
 *   important - it can read it, but materially less well than it should
 *   advisory  - worth doing, not worth blocking on
 *
 * Each check applies one rule of the site's catalog, and carries its id. The rule gives the
 * limits, the switch and the severity: serious and moderate show as important, minor as advisory.
 *
 * One rule is deliberately absent: being disallowed in robots.txt is NOT a
 * failure. Refusing a crawler is a legitimate decision. What IS reported is a
 * disagreement between the policy and what the server actually does, because
 * one of the two is then wrong and somebody owns the fix.
 */
public final class GeoScore {

    private static final String CRITICAL = "critical";
    private static final String IMPORTANT = "important";
    private static final String ADVISORY = "advisory";

    private static final String ACCESS = "access";
    private static final String CONTENT = "content";
    private static final String FILES = "files";

    private static final String GUEST_READABLE = "guestReadable";
    private static final String TITLE = "title";
    private static final String PASSED = "passed";
    private static final String SEVERITY = "severity";

    private GeoScore() {
    }

    /**
     * @param report what the fetches of one page found
     * @param rules  the rules of the site of the page
     * @return the checks, and the counts the dashboard and the drawer quote
     */
    public static JSONObject compute(JSONObject report, ContentRules rules) {
        Checks checks = new Checks(rules);
        Facts facts = Facts.of(report);
        access(checks, facts);
        content(checks, facts);
        files(checks, facts);
        return tally(checks.out);
    }

    /** The checks of one page, each added only when the site keeps its rule on. */
    private static final class Checks {
        private final ContentRules rules;
        private final JSONArray out = new JSONArray();

        Checks(ContentRules rules) {
            this.rules = rules;
        }

        ContentRule rule(String ruleId) {
            return rules.get(ruleId);
        }

        /** `value` is the observed fact the UI shows next to the verdict. Never a sentence: the UI translates. */
        void add(String id, String group, String ruleId, boolean passed, Object value) {
            ContentRule rule = rules.get(ruleId);
            if (!rule.enabled()) {
                return;
            }
            JSONObject c = new JSONObject();
            c.put("id", id);
            c.put("rule", ruleId);
            c.put("group", group);
            c.put(SEVERITY, severity(rule.severity()));
            c.put(PASSED, passed);
            c.put("value", value == null ? JSONObject.NULL : value);
            if (rule.minimum() != null) {
                c.put("min", limit(rule.minimum()));
            }
            if (rule.maximum() != null) {
                c.put("max", limit(rule.maximum()));
            }
            out.put(c);
        }

        /** A whole number as an integer, so that the UI shows 30 and not 30.0. */
        private static Object limit(double value) {
            if (value == Math.rint(value)) {
                return (long) value;
            }
            return value;
        }
    }

    /** The word GEO readiness shows for the severity of a rule. */
    static String severity(String ruleSeverity) {
        return switch (ruleSeverity) {
            case CRITICAL -> CRITICAL;
            case "minor" -> ADVISORY;
            default -> IMPORTANT;
        };
    }

    /**
     * Everything the checks read, pulled out of the report once.
     *
     * compute used to open with a dozen lines doing this and then keep all of it
     * as locals, which is most of why Sonar called it a Brain Method with 34
     * variables. Reading it once and passing it around lets each group of checks
     * be its own method without re-deriving the control or re-walking the agents.
     */
    private static final class Facts {
        private final JSONArray agents;
        /** The first agent that answered 200 with html, or null when none did. */
        private final JSONObject control;
        private final int controlWords;
        private final JSONObject robots;
        private final JSONObject llms;
        private final JSONObject visibility;
        private final int blocked;
        private final int unreachable;
        private final String unreachableError;
        private final int mismatches;

        private Facts(JSONObject report) {
            this.agents = report.optJSONArray("agents");
            this.control = controlHtml(agents);
            this.controlWords = report.optInt("controlWords", 0);
            JSONObject siteFiles = report.optJSONObject("siteFiles");
            this.robots = siteFiles == null ? null : siteFiles.optJSONObject("robots");
            this.llms = siteFiles == null ? null : siteFiles.optJSONObject("llms");
            this.visibility = report.optJSONObject("visibility");
            this.blocked = report.optInt("blockedCount", 0);
            this.unreachable = report.optInt("unreachableCount", 0);
            this.unreachableError = report.isNull("unreachableError") ? null : report.optString("unreachableError", null);
            this.mismatches = report.optInt("blockedButAllowedCount", 0)
                    + report.optInt("reachableButDisallowedCount", 0);
        }

        static Facts of(JSONObject report) {
            return new Facts(report);
        }

        /**
         * The control is the first agent that answered 200. Everything is judged
         * against what a normal browser received, not against an ideal.
         */
        private static JSONObject controlHtml(JSONArray agents) {
            if (agents == null) {
                return null;
            }
            for (int i = 0; i < agents.length(); i++) {
                JSONObject a = agents.getJSONObject(i);
                if (a.optInt("status", 0) == 200 && a.optJSONObject("html") != null) {
                    return a.getJSONObject("html");
                }
            }
            return null;
        }

        /** A field of the control's html, or null when there is no control at all. */
        private String text(String key) {
            return control == null || control.isNull(key) ? null : control.optString(key);
        }

        private int number(String key) {
            return control == null ? 0 : control.optInt(key, 0);
        }

        private boolean flag(String key) {
            return control != null && control.optBoolean(key, false);
        }

        private boolean has(String key) {
            return control != null && !control.isNull(key);
        }
    }

    /** Can a crawler get the bytes at all. */
    private static void access(Checks checks, Facts f) {
        // A fetch with no answer proves neither access nor refusal. Say that the server could not
        // reach the page, and only judge "every crawler gets the page" on answers that came back.
        if (f.unreachable > 0) {
            checks.add("fetched", ACCESS, "geo.fetched", false, f.unreachableError);
        }
        if (f.unreachable == 0 || f.blocked > 0) {
            checks.add("reachable", ACCESS, "geo.reachable", f.blocked == 0, f.blocked);
        }
        checks.add("policyMatchesReality", ACCESS, "geo.policyMatchesReality", f.mismatches == 0, f.mismatches);

        boolean redirect = f.has("metaRefresh");
        checks.add("noClientRedirect", ACCESS, "geo.noClientRedirect", !redirect,
                redirect ? f.text("metaRefresh") : null);

        int named = f.robots == null ? 0 : f.robots.optInt("namedAiBotCount", 0);
        checks.add("namedInRobots", ACCESS, "geo.namedInRobots", named > 0, named);

        // GEO-19. A page a guest cannot read is unreadable to every crawler for
        // good, whatever the fetch returned: some servers answer a gated page
        // with a login form and a cheerful 200. Added only when the repository
        // actually answered, so an unavailable check never reads as a failure -
        // which is why this asks has() rather than taking a default.
        if (f.visibility != null && f.visibility.has(GUEST_READABLE)) {
            boolean guestReadable = f.visibility.optBoolean(GUEST_READABLE, true);
            checks.add(GUEST_READABLE, ACCESS, "geo.guestReadable", guestReadable,
                    guestReadable ? null : f.visibility.optString("kind", null));
        }
    }

    /** Is what arrives actually usable. */
    private static void content(Checks checks, Facts f) {
        checks.add("contentInInitialHtml", CONTENT, "geo.contentInInitialHtml",
                f.controlWords >= checks.rule("geo.contentInInitialHtml").min(), f.controlWords);

        int thin = thinCount(f, checks.rule("geo.sameContentForCrawlers").min());
        checks.add("sameContentForCrawlers", CONTENT, "geo.sameContentForCrawlers", thin == 0, thin);

        String title = f.text(TITLE);
        int titleLen = title == null ? 0 : title.length();
        checks.add(TITLE, CONTENT, "seo.title", title != null && checks.rule("seo.title").accepts(titleLen), titleLen);

        int h1 = f.number("h1Count");
        checks.add("singleH1", CONTENT, "seo.singleH1", h1 == 1, h1);

        int h2 = f.number("h2Count");
        checks.add("headingOutline", CONTENT, "geo.headingOutline", h2 > 0, h2);

        // A report stored before the length was read has none: the presence alone then decides.
        boolean description = f.flag("metaDescription");
        boolean measured = f.has("metaDescriptionLength");
        int descriptionLen = f.number("metaDescriptionLength");
        checks.add("metaDescription", CONTENT, "seo.metaDescription",
                description && (!measured || checks.rule("seo.metaDescription").accepts(descriptionLen)),
                description && measured ? descriptionLen : null);
        checks.add("canonical", CONTENT, "seo.canonical", f.flag("canonical"), null);

        String lang = f.text("lang");
        checks.add("langDeclared", CONTENT, "seo.lang", lang != null, lang);

        JSONArray types = f.control == null ? null : f.control.optJSONArray("jsonLdTypes");
        boolean hasSchema = types != null && types.length() > 0;
        checks.add("structuredData", CONTENT, "seo.structuredData", hasSchema, hasSchema ? join(types) : null);

        boolean fresh = f.has("dateModified");
        checks.add("freshness", CONTENT, "geo.dateModified", fresh, fresh ? f.text("dateModified") : null);

        imageAlt(checks, f);
    }

    /** No images is not a failure. It is simply nothing to describe. */
    private static void imageAlt(Checks checks, Facts f) {
        int images = f.number("images");
        int withAlt = f.number("imagesWithAlt");
        boolean altOk = images == 0 || withAlt >= images * checks.rule("geo.imageAlt").min();
        checks.add("imageAlt", CONTENT, "geo.imageAlt", altOk, images == 0 ? null : withAlt + "/" + images);
    }

    /** How many agents received less than {@code ratio} of the text of the control. */
    private static int thinCount(Facts f, double ratio) {
        if (f.agents == null || f.controlWords <= 0) {
            return 0;
        }
        int thin = 0;
        for (int i = 0; i < f.agents.length(); i++) {
            JSONObject h = f.agents.getJSONObject(i).optJSONObject("html");
            if (h != null && h.optInt("words", 0) < f.controlWords * ratio) {
                thin++;
            }
        }
        return thin;
    }

    private static void files(Checks checks, Facts f) {
        checks.add("robotsPresent", FILES, "geo.robotsPresent",
                f.robots != null && f.robots.optBoolean("present", false), null);
        checks.add("llmsPresent", FILES, "geo.llmsPresent",
                f.llms != null && f.llms.optBoolean("present", false), null);
    }

    /** The counts the dashboard and the drawer both quote. */
    private static JSONObject tally(JSONArray checks) {
        int passed = 0;
        int criticalFailed = 0;
        int importantFailed = 0;
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.getJSONObject(i);
            if (c.getBoolean(PASSED)) {
                passed++;
            } else if (CRITICAL.equals(c.getString(SEVERITY))) {
                criticalFailed++;
            } else if (IMPORTANT.equals(c.getString(SEVERITY))) {
                importantFailed++;
            }
        }

        JSONObject out = new JSONObject();
        out.put(PASSED, passed);
        out.put("total", checks.length());
        out.put("criticalFailed", criticalFailed);
        out.put("importantFailed", importantFailed);
        out.put("checks", checks);
        return out;
    }

    private static String join(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a.getString(i));
        }
        return sb.toString();
    }
}
