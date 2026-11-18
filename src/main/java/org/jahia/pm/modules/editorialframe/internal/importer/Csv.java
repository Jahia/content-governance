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

    private Csv() {
    }

    /** The rows of the text. A blank line gives no row. */
    static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        String source = text.startsWith("﻿") ? text.substring(1) : text;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                    i++;
                }
                end(rows, row, field);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        end(rows, row, field);
        return rows;
    }

    private static void end(List<List<String>> rows, List<String> row, StringBuilder field) {
        row.add(field.toString());
        field.setLength(0);
        if (row.size() > 1 || !row.get(0).isBlank()) {
            rows.add(row);
        }
    }
}
