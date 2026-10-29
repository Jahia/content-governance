package org.jahia.community.contentgovernance.compliance.service.internal;

import org.apache.commons.text.StringEscapeUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns property values into the text a visitor reads: tags removed from rich text, entities
 * decoded, white space collapsed.
 *
 * <p>The tag stripper is one forward pass: every search starts where the previous one ended, and an
 * unclosed tag, comment or script drops the rest of the value. A regular expression per construct
 * restarts at each unclosed {@code <}, which took 23 s on a 160 KB value of {@code <} characters.</p>
 */
final class PlainText {

    // Block-level tags separate words; inline tags (strong, em, span, a) do not: <b>gra</b>tuit is one word.
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "br", "hr", "li", "ul", "ol", "dl", "dt", "dd", "h1", "h2", "h3", "h4", "h5", "h6",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "section", "article", "header",
            "footer", "nav", "aside", "main", "blockquote", "figure", "figcaption", "pre", "address",
            "details", "summary");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private PlainText() {
    }

    /**
     * @param html a rich text value
     * @return the visible text, on one line
     */
    static String fromHtml(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        int n = html.length();
        while (i < n) {
            char c = html.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            if (html.startsWith("<!--", i)) {
                int end = html.indexOf("-->", i + 4);
                if (end < 0) {
                    break;
                }
                out.append(' ');
                i = end + 3;
                continue;
            }
            int close = html.indexOf('>', i + 1);
            if (close < 0) {
                break;
            }
            String name = tagName(lower, i + 1, close);
            if (("script".equals(name) || "style".equals(name)) && lower.charAt(i + 1) != '/') {
                int end = lower.indexOf("</" + name, close + 1);
                if (end < 0) {
                    break;
                }
                int endClose = html.indexOf('>', end);
                if (endClose < 0) {
                    break;
                }
                out.append(' ');
                i = endClose + 1;
                continue;
            }
            if (BLOCK_TAGS.contains(name)) {
                out.append(' ');
            }
            i = close + 1;
        }
        return collapse(StringEscapeUtils.unescapeHtml4(out.toString()));
    }

    /** The element name of the tag between {@code from} (after {@code <}) and {@code to} (the {@code >}). */
    private static String tagName(String lower, int from, int to) {
        int start = from < to && lower.charAt(from) == '/' ? from + 1 : from;
        int end = start;
        while (end < to && Character.isLetterOrDigit(lower.charAt(end))) {
            end++;
        }
        return lower.substring(start, end);
    }

    /**
     * @param text a plain text value (small text, text area)
     * @return the text on one line
     */
    static String collapse(String text) {
        return SPACES.matcher(text).replaceAll(" ").trim();
    }
}
