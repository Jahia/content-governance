/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV text as RFC 4180 states it: a comma between fields, a field in double quotes may hold
 * a comma, a line break or a doubled quote. A line break is CRLF or LF.
 */
final class Csv {

    private final String source;
    private final List<List<String>> rows = new ArrayList<>();
    private final StringBuilder field = new StringBuilder();
    private List<String> row = new ArrayList<>();
    private boolean quoted;

    private Csv(String text) {
        source = text.startsWith("﻿") ? text.substring(1) : text;
    }

    /** The rows of the text. A blank line gives no row. */
    static List<List<String>> read(String text) {
        Csv csv = new Csv(text);
        int index = 0;
        while (index < csv.source.length()) {
            index = csv.quoted ? csv.readQuoted(index) : csv.readPlain(index);
        }
        csv.endRow();
        return csv.rows;
    }

    /** Reads the character at an index inside quotes. Returns the index of the next character to read. */
    private int readQuoted(int index) {
        char c = source.charAt(index);
        if (c == '"' && next(index) == '"') {
            field.append('"');
            return index + 2;
        }
        if (c == '"') {
            quoted = false;
        } else {
            field.append(c);
        }
        return index + 1;
    }

    /** Reads the character at an index outside quotes. Returns the index of the next character to read. */
    private int readPlain(int index) {
        char c = source.charAt(index);
        if (c == '"') {
            quoted = true;
        } else if (c == ',') {
            row.add(field.toString());
            field.setLength(0);
        } else if (c == '\n' || c == '\r') {
            endRow();
            return c == '\r' && next(index) == '\n' ? index + 2 : index + 1;
        } else {
            field.append(c);
        }
        return index + 1;
    }

    /** The character after an index, or 0 at the end of the text. */
    private char next(int index) {
        return index + 1 < source.length() ? source.charAt(index + 1) : 0;
    }

    private void endRow() {
        row.add(field.toString());
        field.setLength(0);
        if (row.size() > 1 || !row.get(0).isBlank()) {
            rows.add(row);
        }
        row = new ArrayList<>();
    }
}
