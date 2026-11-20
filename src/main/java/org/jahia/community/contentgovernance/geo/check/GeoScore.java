package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

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
 * The checks of the title, the H1, the meta description, the canonical address, the language, the
 * structured data and the alt texts are rules of the page check service. The caller runs the rules of
 * {@link #PAGE_RULES} on the HTML of the control, and puts the ids that it found under
 * {@link #PAGE_FINDINGS}: such a check passes when the service found nothing under its rule, as the
 * drawer and {@code frame.check} report it. The value beside the verdict is what the fetch read.
 *
 * One rule is deliberately absent: being disallowed in robots.txt is NOT a
 * failure. Refusing a crawler is a legitimate decision. What IS reported is a
 * disagreement between the policy and what the server actually does, because
 * one of the two is then wrong and somebody owns the fix.
 */
public final class GeoScore {

    /** The key of the report that holds the ids of the rules that the page check service found on the control. */
    public static final String PAGE_FINDINGS = "pageFindings";
    private static final String TITLE_RULE = "seo.title";
    private static final String DESCRIPTION_RULE = "seo.metaDescription";
    private static final String CANONICAL_RULE = "seo.canonical";
    private static final String SCHEMA_RULE = "seo.structuredData";
    private static final String H1_MISSING = "html.headingH1Missing";
    private static final String H1_MULTIPLE = "html.headingH1Multiple";
    private static final String LANGUAGE_RULE = "html.pageLanguageMissing";
    private static final String ALT_RULE = "html.imageAltMissing";
    /** The rules of the page check service that GEO readiness reports. */
    public static final Set<String> PAGE_RULES = Set.of(TITLE_RULE, DESCRIPTION_RULE, CANONICAL_RULE, SCHEMA_RULE,
            H1_MISSING, H1_MULTIPLE, LANGUAGE_RULE, ALT_RULE);

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
        /** The rules that the page check service broke on the control, or null when it did not run. */
        private final Set<String> pageFindings;

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
            JSONArray found = control == null ? null : report.optJSONArray(PAGE_FINDINGS);
            Set<String> ids = new HashSet<>();
            for (int i = 0; found != null && i < found.length(); i++) {
                ids.add(found.optString(i));
            }
            this.pageFindings = found == null ? null : Set.copyOf(ids);
        }

        /** True when the page check service ran on the control and found nothing under the rule. */
        private boolean passes(String ruleId) {
            return pageFindings != null && !pageFindings.contains(ruleId);
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
        checks.add(TITLE, CONTENT, TITLE_RULE, f.passes(TITLE_RULE), title == null ? 0 : title.length());

        int h1 = f.number("h1Count");
        boolean multiple = f.pageFindings == null ? h1 > 1 : f.pageFindings.contains(H1_MULTIPLE);
        checks.add("singleH1", CONTENT, multiple ? H1_MULTIPLE : H1_MISSING,
                f.passes(H1_MISSING) && f.passes(H1_MULTIPLE), h1);

        int h2 = f.number("h2Count");
        checks.add("headingOutline", CONTENT, "geo.headingOutline", h2 > 0, h2);

        boolean described = f.flag("metaDescription");
        checks.add("metaDescription", CONTENT, DESCRIPTION_RULE, f.passes(DESCRIPTION_RULE),
                described && f.has("metaDescriptionLength") ? f.number("metaDescriptionLength") : null);
        checks.add("canonical", CONTENT, CANONICAL_RULE, f.passes(CANONICAL_RULE), null);

        checks.add("langDeclared", CONTENT, LANGUAGE_RULE, f.passes(LANGUAGE_RULE), f.text("lang"));

        JSONArray types = f.control == null ? null : f.control.optJSONArray("jsonLdTypes");
        boolean hasSchema = types != null && types.length() > 0;
        checks.add("structuredData", CONTENT, SCHEMA_RULE, f.passes(SCHEMA_RULE), hasSchema ? join(types) : null);

        boolean fresh = f.has("dateModified");
        checks.add("freshness", CONTENT, "geo.dateModified", fresh, fresh ? f.text("dateModified") : null);

        imageAlt(checks, f);
    }

    /**
     * Every image has an alt attribute, and an empty one marks a decorative image. No image is not a
     * failure: there is nothing to describe, and a page that no crawler got has none. The value is the
     * images with an alt attribute out of all.
     */
    private static void imageAlt(Checks checks, Facts f) {
        int images = f.number("images");
        int missing = f.number("imagesWithoutAlt");
        checks.add("imageAlt", CONTENT, ALT_RULE, f.control == null || f.passes(ALT_RULE),
                images == 0 ? null : (images - missing) + "/" + images);
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
