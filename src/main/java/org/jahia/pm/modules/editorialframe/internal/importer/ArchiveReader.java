/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import org.jahia.pm.modules.editorialframe.internal.skills.SkillPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the text files of a zip archive, such as the archive that GitHub gives for a repository.
 * The archive is data: no entry is written to a disk, and no entry runs.
 */
public final class ArchiveReader {

    static final int MAX_ENTRIES = 5000;
    static final long MAX_TOTAL_CHARS = 60L * 1024 * 1024;
    /** Every entry is inflated, the skipped ones too: this caps a zip bomb. */
    static final long MAX_INFLATED_BYTES = 300L * 1024 * 1024;

    /** The text files by path, after the folder that wraps every entry is removed. */
    public final Map<String, String> files;
    /** Each entry left out, with the reason. */
    public final List<String> skipped;

    private ArchiveReader(Map<String, String> files, List<String> skipped) {
        this.files = files;
        this.skipped = skipped;
    }

    public static ArchiveReader read(InputStream zip) throws IOException {
        Map<String, String> raw = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        long total = 0;
        long[] inflated = {0};
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(zip, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("The archive has more than " + MAX_ENTRIES + " entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!SkillPaths.isSafeRelative(name)) {
                    skipped.add(name + ": unsafe path");
                    continue;
                }
                if (isHidden(name) || !isText(name)) {
                    drain(in, inflated);
                    continue;
                }
                byte[] bytes = readLimited(in, SkillPaths.MAX_FILE_CHARS, inflated);
                if (bytes == null) {
                    skipped.add(name + ": larger than " + SkillPaths.MAX_FILE_CHARS + " bytes");
                    continue;
                }
                String text = utf8(bytes);
                if (text == null) {
                    skipped.add(name + ": not UTF-8 text");
                    continue;
                }
                total += text.length();
                if (total > MAX_TOTAL_CHARS) {
                    throw new IOException("The archive holds more than " + MAX_TOTAL_CHARS + " characters of text");
                }
                raw.put(name, text);
            }
        }
        return new ArchiveReader(stripWrapper(raw), skipped);
    }

    /** Removes the first folder when every path starts with it, as in an archive of GitHub. */
    static Map<String, String> stripWrapper(Map<String, String> raw) {
        String prefix = null;
        for (String path : raw.keySet()) {
            int slash = path.indexOf('/');
            String first = slash < 0 ? null : path.substring(0, slash + 1);
            if (first == null || (prefix != null && !prefix.equals(first))) {
                prefix = null;
                break;
            }
            prefix = first;
        }
        Map<String, String> files = new TreeMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            files.put(prefix == null ? entry.getKey() : entry.getKey().substring(prefix.length()), entry.getValue());
        }
        return files;
    }

    private static boolean isHidden(String path) {
        for (String part : path.split("/")) {
            if (part.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isText(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return SkillPaths.TEXT_TYPES.contains(extension);
    }

    private static byte[] readLimited(InputStream in, int limit, long[] inflated) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            count(inflated, read);
            out.write(buffer, 0, read);
            if (out.size() > limit) {
                drain(in, inflated);
                return null;
            }
        }
        return out.toByteArray();
    }

    /** Reads the rest of an entry, so that the next one can be read, and counts its bytes. */
    private static void drain(InputStream in, long[] inflated) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            count(inflated, read);
        }
    }

    private static void count(long[] inflated, int read) throws IOException {
        inflated[0] += read;
        if (inflated[0] > MAX_INFLATED_BYTES) {
            throw new IOException("The archive inflates to more than " + MAX_INFLATED_BYTES + " bytes");
        }
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
