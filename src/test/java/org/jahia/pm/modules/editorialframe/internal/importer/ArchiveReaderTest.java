/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveReaderTest {

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void eachEntryLeftOutWithAReasonIsListedInTheOrderOfTheArchive() throws IOException {
        byte[] large = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(large, (byte) 'a');
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("repo/", new byte[0]);
        entries.put("repo/.github/notes.md", "hidden".getBytes(StandardCharsets.UTF_8));
        entries.put("repo/logo.png", new byte[]{1, 2, 3});
        entries.put("repo/bad.md", new byte[]{(byte) 0xC3, (byte) 0x28});
        entries.put("repo/big.md", large);
        entries.put("repo/skills/x/SKILL.md", "text".getBytes(StandardCharsets.UTF_8));
        entries.put("/abs.md", "text".getBytes(StandardCharsets.UTF_8));

        ArchiveReader archive = ArchiveReader.read(new ByteArrayInputStream(zip(entries)));

        assertEquals(Map.of("skills/x/SKILL.md", "text"), archive.files);
        assertEquals(List.of("repo/bad.md: not UTF-8 text", "repo/big.md: larger than 2097152 bytes",
                "/abs.md: unsafe path"), archive.skipped);
    }

    @Test
    void anArchiveWithTooManyEntriesIsRefused() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i <= ArchiveReader.MAX_ENTRIES; i++) {
            entries.put("folder-" + i + "/", new byte[0]);
        }
        byte[] zip = zip(entries);

        IOException refused = assertThrows(IOException.class, () -> ArchiveReader.read(new ByteArrayInputStream(zip)));
        assertEquals("The archive has more than 5000 entries", refused.getMessage());
    }
}
