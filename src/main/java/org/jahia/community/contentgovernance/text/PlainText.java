package org.jahia.community.contentgovernance.text;

import org.apache.commons.text.StringEscapeUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns property values into the text a visitor reads: tags removed from rich text, entities
 * decoded, white space collapsed. The Compliance scan and the check {@code pattern} read a text this way,
 * so a match has the same place in both.
 *
 * <p>The tag stripper is one forward pass: every search starts where the previous one ended. An unclosed
 * comment or script drops the rest of the value, and a {@code <} that no {@code >} follows is text, with
 * the rest of the value: {@code Prices < 10 EUR} stays as it is. A regular expression per construct
 * restarts at each unclosed {@code <}, which took 23 s on a 160 KB value of {@code <} characters.</p>
 */
public final class PlainText {

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
    public static String fromHtml(String html) {
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
            i = skipMarkup(html, lower, i, out);
        }
        return collapse(StringEscapeUtils.unescapeHtml4(out.toString()));
    }

    /**
     * Skips the markup that starts at {@code from}, a {@code <}, and appends what it stands for in the text: a
     * space for a comment, a script, a style or a block tag, and the rest of the value when no {@code >} follows.
     *
     * @return the index after the markup, or the length of the value when the markup takes the rest of it
     */
    private static int skipMarkup(String html, String lower, int from, StringBuilder out) {
        int n = html.length();
        if (html.startsWith("<!--", from)) {
            int end = html.indexOf("-->", from + 4);
            if (end < 0) {
                return n;
            }
            out.append(' ');
            return end + 3;
        }
        int close = html.indexOf('>', from + 1);
        if (close < 0) {
            // No > follows, so no tag starts here or after
            out.append(html, from, n);
            return n;
        }
        String name = tagName(lower, from + 1, close);
        if (("script".equals(name) || "style".equals(name)) && lower.charAt(from + 1) != '/') {
            int end = lower.indexOf("</" + name, close + 1);
            if (end < 0) {
                return n;
            }
            int endClose = html.indexOf('>', end);
            if (endClose < 0) {
                return n;
            }
            out.append(' ');
            return endClose + 1;
        }
        if (BLOCK_TAGS.contains(name)) {
            out.append(' ');
        }
        return close + 1;
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
    public static String collapse(String text) {
        return SPACES.matcher(text).replaceAll(" ").trim();
    }

    /**
     * @param text a plain text value (small text, text area), such as {@code Prices < 10 EUR}
     * @return the value as HTML, which {@link #fromHtml} reads as {@link #collapse} reads the value:
     * {@code Prices &lt; 10 EUR}
     */
    public static String toHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
