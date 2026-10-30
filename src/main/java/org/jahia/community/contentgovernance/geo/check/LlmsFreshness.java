package org.jahia.community.contentgovernance.geo.check;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the published `llms.txt` still describes the site it is about.
 *
 * The file is generated once and then served unchanged, so it goes out of date
 * silently: nothing fails, nothing 500s, an assistant is simply handed a map of
 * a site that has moved on. That happened on the test site the moment a vanity
 * url was added - the file kept pointing at `/home/agencies.html`, which now
 * redirects, and there was no signal anywhere that it had.
 *
 * **Each link is checked against the site.** The file is written by hand in the llms module, so
 * which pages it lists is the author's choice: what can go wrong is a link to a page that moved
 * (its old address now redirects) or that is no longer published. Those are reported; a page the
 * author chose not to list is not.
 *
 * Deliberately not tied to the readiness score: the score is about crawler access and what
 * arrives in the HTML, and fixing this file does not change it.
 */
public final class LlmsFreshness {

    /** `- [Title](url): description`, the link line of the llms.txt format. */
    private static final Pattern LINK = Pattern.compile("(?m)^\\s*-\\s*\\[([^\\]]*)\\]\\(([^)]+)\\)");

    private static final int MAX_REPORTED = 100;

    /**
     * One key name, two shapes, which is worth naming once rather than four
     * times. check() writes it as a COUNT of the pages llms.txt lists;
     * listingFor() writes it as a BOOLEAN saying whether one page is among them.
     * Different objects, read by different callers - but a reader that confuses
     * them gets a silent wrong answer, which is exactly what happened to this
     * class's "stale" and "outdated" in GeoReport.
     */
    private static final String LISTED = "listed";

    private static final String PRESENT = "present";
    private static final String OUTDATED = "outdated";
    private static final String PATH = "path";
    private static final String TITLE = "title";

    private LlmsFreshness() {
    }

    /**
     * @param served    the body currently served at /llms.txt, or null when the file is absent
     * @param published the site's published pages, by public path
     * @param sitePath  {@code /sites/<key>}
     * @param siteBase  the site's public base URL; links to another host are not judged
     * @return {present, listed, listedPaths, stale, outdated}
     */
    public static JSONObject check(String served, Map<String, PublishedMap.Entry> published, String sitePath,
            String siteBase) {
        JSONObject out = new JSONObject();
        boolean present = served != null && !served.trim().isEmpty();
        out.put(PRESENT, present);
        if (!present || published == null) {
            // Absent is already reported as its own check on the site files. Only these two keys
            // are written on this path, which a reader depends on.
            out.put(OUTDATED, false);
            return out;
        }

        Map<String, String> listed = linksOf(served, hostOf(siteBase));
        out.put(LISTED, listed.size());
        JSONArray stale = staleOf(listed, published, sitePath);

        // The listed paths themselves, so the drawer can answer "is this page in
        // llms.txt" definitively rather than inferring it from the findings.
        //
        // Capped like stale is: the file is written by hand and may list thousands of paths.
        out.put("listedPaths", new JSONArray(capped(listed.keySet())));
        out.put("stale", stale);
        out.put(OUTDATED, stale.length() > 0);
        return out;
    }

    /** At most MAX_REPORTED entries, in the order the file listed them. */
    private static List<String> capped(Collection<String> paths) {
        List<String> out = new ArrayList<>(paths);
        return out.size() <= MAX_REPORTED ? out : out.subList(0, MAX_REPORTED);
    }

    /**
     * Links of the served file that no published page answers. Each carries WHY, because it decides
     * what an editor does: a page that moved needs the new address, one that is gone needs the line
     * removed.
     */
    private static JSONArray staleOf(Map<String, String> listed, Map<String, PublishedMap.Entry> published,
            String sitePath) {
        JSONArray stale = new JSONArray();
        for (Map.Entry<String, String> e : listed.entrySet()) {
            String path = e.getKey();
            if (stale.length() >= MAX_REPORTED) {
                break;
            }
            if (published.get(path) == null) {
                stale.put(staleEntry(path, e.getValue(), published, sitePath));
            }
        }
        return stale;
    }

    private static JSONObject staleEntry(String path, String title, Map<String, PublishedMap.Entry> published,
            String sitePath) {
        JSONObject r = new JSONObject();
        r.put(PATH, path);
        r.put(TITLE, title);
        r.put("why", PublishedMap.movedFrom(published, sitePath, path) != null ? "addressChanged" : "gone");
        return r;
    }

    /**
     * Whether one page is listed in the served llms.txt: {listed}, or null when there is no file.
     * A page that is not listed is the author's choice, so nothing more is said about it.
     */
    public static JSONObject listingFor(JSONObject report, String path) {
        if (report == null || !report.optBoolean(PRESENT, false) || path == null) {
            return null;
        }
        boolean listed = false;
        JSONArray paths = report.optJSONArray("listedPaths");
        for (int i = 0; paths != null && i < paths.length() && !listed; i++) {
            listed = path.equals(paths.optString(i));
        }
        JSONObject out = new JSONObject();
        out.put(LISTED, listed);
        return out;
    }

    /**
     * Public path to link text, for every link of the file to this site: relative links and links
     * to {@code siteHost}. Links to other sites are the author's business and are not judged.
     */
    private static Map<String, String> linksOf(String body, String siteHost) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = LINK.matcher(body);
        while (m.find()) {
            String url = m.group(2).trim();
            String host = hostOf(url);
            if (host != null && siteHost != null && !host.equalsIgnoreCase(siteHost)) {
                continue;
            }
            out.putIfAbsent(PublishedMap.pathOf(url), m.group(1).trim());
        }
        return out;
    }

    /** The host of an absolute http(s) URL, or null for a relative link or anything unreadable. */
    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.isAbsolute() ? uri.getHost() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
