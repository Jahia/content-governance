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

/** Small text functions of the frame: plain text from HTML, digests. */
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
}
