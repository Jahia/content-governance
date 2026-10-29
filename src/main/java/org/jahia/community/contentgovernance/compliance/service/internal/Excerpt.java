package org.jahia.community.contentgovernance.compliance.service.internal;

/**
 * The text around a match, shortened at word boundaries, for the report.
 *
 * @param before text before the match, starting with an ellipsis when shortened
 * @param match  the matched text as written
 * @param after  text after the match, ending with an ellipsis when shortened
 */
record Excerpt(String before, String match, String after) {

    private static final int RADIUS = 60;
    private static final String ELLIPSIS = "…";

    static Excerpt around(String text, int start, int end) {
        int from = Math.max(0, start - RADIUS);
        if (from > 0) {
            int space = text.indexOf(' ', from);
            from = space >= 0 && space < start ? space + 1 : from;
        }
        int to = Math.min(text.length(), end + RADIUS);
        if (to < text.length()) {
            int space = text.lastIndexOf(' ', to);
            to = space > end ? space : to;
        }
        String before = (from > 0 ? ELLIPSIS : "") + text.substring(from, start);
        String after = text.substring(end, to) + (to < text.length() ? ELLIPSIS : "");
        return new Excerpt(before, text.substring(start, end), after);
    }
}
