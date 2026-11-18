/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void aQuotedFieldHoldsACommaALineBreakAndADoubledQuote() {
        assertEquals(List.of(List.of("a", "b,c", "say \"hi\"", "two\r\nlines")),
                Csv.read("a,\"b,c\",\"say \"\"hi\"\"\",\"two\r\nlines\""));
    }

    @Test
    void aLineEndsWithCrlfLfOrCrAndABlankLineGivesNoRow() {
        assertEquals(List.of(List.of("a", "b"), List.of("c"), List.of("d"), List.of("e", "")),
                Csv.read("a,b\r\nc\n  \nd\re,\r\n"));
    }

    @Test
    void aQuoteInsideAFieldOpensAQuotedPartAndAnUnclosedQuoteRunsToTheEnd() {
        assertEquals(List.of(List.of("abc,de")), Csv.read("ab\"c,d\"e"));
        assertEquals(List.of(List.of("a", "b\nc")), Csv.read("a,\"b\nc"));
        assertEquals(List.of(List.of("a\"")), Csv.read("\"a\"\"\""));
    }

    @Test
    void aByteOrderMarkIsDroppedAndAnEmptyTextGivesNoRow() {
        assertEquals(List.of(List.of("en", "fr")), Csv.read("﻿en,fr"));
        assertEquals(List.of(), Csv.read(""));
        assertEquals(List.of(List.of("", "")), Csv.read(","));
    }
}
