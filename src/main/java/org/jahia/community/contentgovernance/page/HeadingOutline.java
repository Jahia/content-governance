package org.jahia.community.contentgovernance.page;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The headings of a page as the Readability tab of the drawer lists them: one row per heading of the main
 * text, in page order, with the level it skips from and whether it has no text. Each row carries a CSS
 * selector, which the drawer reads in its preview of the page to show the heading.
 *
 * <p>Thread safety: stateless.</p>
 */
final class HeadingOutline {

    /** The headings listed: a longer page keeps its counts, and the list stops there. */
    static final int MAX_HEADINGS = 200;
    /** The characters of a heading kept in the list. */
    static final int MAX_HEADING_CHARS = 160;
    private static final String LEVEL = "level";

    private HeadingOutline() {
    }

    /**
     * @param html the whole HTML, whose H1 headings are counted
     * @param main the main text, whose headings are listed
     * @return {@code h1Count}, {@code headingSkips} and {@code headingCount}, which count every heading, and
     *     {@code outline}, the first {@link #MAX_HEADINGS} as {@code {level, text, selector, skippedFrom?, empty?}}
     */
    static Map<String, Object> of(Document html, Element main) {
        List<Map<String, Object>> outline = new ArrayList<>();
        int skips = 0;
        int previous = 0;
        List<Element> headings = main.select("h1, h2, h3, h4, h5, h6");
        for (Element heading : headings) {
            int level = heading.normalName().charAt(1) - '0';
            boolean skipped = previous > 0 && level > previous + 1;
            if (skipped) {
                skips++;
            }
            if (outline.size() < MAX_HEADINGS) {
                outline.add(row(heading, level, skipped ? previous : 0));
            }
            previous = level;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("h1Count", html.select("h1").size());
        out.put("headingSkips", skips);
        out.put("headingCount", headings.size());
        out.put("outline", outline);
        return out;
    }

    private static Map<String, Object> row(Element heading, int level, int skippedFrom) {
        String text = text(heading);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(LEVEL, level);
        row.put("text", text.length() > MAX_HEADING_CHARS ? text.substring(0, MAX_HEADING_CHARS) : text);
        row.put("selector", selector(heading));
        if (skippedFrom > 0) {
            row.put("skippedFrom", skippedFrom);
        }
        if (text.isEmpty()) {
            row.put("empty", true);
        }
        return row;
    }

    /** What a heading says: its text, else what stands for it, an aria-label or the alt of an image. */
    private static String text(Element heading) {
        String text = heading.text().trim();
        if (!text.isEmpty()) {
            return text;
        }
        String label = heading.attr("aria-label").trim();
        if (!label.isEmpty()) {
            return label;
        }
        Element image = heading.selectFirst("img[alt]");
        return image == null ? "" : image.attr("alt").trim();
    }

    /**
     * A selector of the element from the body of the page: the nearest id, then each tag, with its rank
     * among the siblings of the same tag when it has some. Example: {@code main > section:nth-of-type(2) > h2}.
     */
    static String selector(Element element) {
        List<String> parts = new ArrayList<>();
        Element body = element.ownerDocument() == null ? null : element.ownerDocument().body();
        Element node = element;
        while (node != null && node != body && !"html".equals(node.normalName())) {
            if (!node.id().isEmpty()) {
                parts.add(0, "#" + escape(node.id()));
                break;
            }
            String tag = node.normalName();
            Element parent = node.parent();
            String part = tag;
            if (parent != null) {
                List<Element> same = parent.children().stream().filter(child -> child.normalName().equals(tag)).toList();
                if (same.size() > 1) {
                    part += ":nth-of-type(" + (same.indexOf(node) + 1) + ")";
                }
            }
            parts.add(0, part);
            node = parent;
        }
        return String.join(" > ", parts);
    }

    /** An id as a CSS identifier, as {@code CSS.escape} writes it for the usual ids. */
    static String escape(String id) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isDigit(c) && (i == 0 || (i == 1 && id.charAt(0) == '-'))) {
                out.append('\\').append(Integer.toHexString(c)).append(' ');
            } else if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c >= 0x80) {
                out.append(c);
            } else {
                out.append('\\').append(c);
            }
        }
        return out.toString();
    }
}
