/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small text functions of the frame: plain text from HTML, token estimates, digests. */
public final class Texts {

    private static final Pattern TAG = Pattern.compile("<[^>]{0,2000}>");
    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|div|li|h[1-6]|tr|td|th|blockquote)>|<br\\s*/?>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
            Map.entry("apos", "'"), Map.entry("nbsp", "\u00A0"), Map.entry("laquo", "«"),
            Map.entry("raquo", "»"), Map.entry("rsquo", "’"), Map.entry("lsquo", "‘"),
            Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("hellip", "…"),
            Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("eacute", "é"),
            Map.entry("egrave", "è"), Map.entry("agrave", "à"), Map.entry("ccedil", "ç"));

    private Texts() {
    }

    /** The text of an HTML value, with one space for each tag and decoded entities. */
    public static String plain(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = BLOCK_END.matcher(value).replaceAll("\n");
        text = TAG.matcher(text).replaceAll(" ");
        Matcher entity = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (entity.find()) {
            entity.appendReplacement(out, Matcher.quoteReplacement(decode(entity.group(1), entity.group())));
        }
        entity.appendTail(out);
        return out.toString().replace('\u00A0', ' ');
    }

    private static String decode(String name, String whole) {
        if (name.startsWith("#")) {
            try {
                int code = name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                return Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : whole;
            } catch (NumberFormatException e) {
                return whole;
            }
        }
        return NAMED.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), whole);
    }

    /** An estimate of the tokens of a text: four characters a token. */
    public static int tokens(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }

    /** The first 12 hexadecimal characters of the SHA-256 of a text. */
    public static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }

    /** The first line of a Markdown text, without its heading marks, at most 160 characters. */
    public static String firstLine(String markdown) {
        if (markdown == null) {
            return "";
        }
        for (String line : markdown.split("\n")) {
            String trimmed = line.replaceFirst("^[#>*\\-\\s]+", "").trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 160 ? trimmed.substring(0, 157) + "..." : trimmed;
            }
        }
        return "";
    }

    /** A text without the character {@code edge} at its start and at its end, however often it repeats there. */
    public static String trim(String text, char edge) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == edge) {
            start++;
        }
        while (end > start && text.charAt(end - 1) == edge) {
            end--;
        }
        return text.substring(start, end);
    }

    /** True when a text holds a character that hides text from a reader: zero width or bidirectional. */
    public static boolean hasInvisibleCharacters(String text) {
        if (text == null) {
            return false;
        }
        return text.codePoints().anyMatch(c -> (c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069) || c == 0xFEFF || (c >= 0xE0000 && c <= 0xE007F));
    }
}
