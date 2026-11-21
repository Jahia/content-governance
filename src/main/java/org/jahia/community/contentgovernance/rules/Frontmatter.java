package org.jahia.community.contentgovernance.rules;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * A Markdown file that a module ships: a YAML frontmatter between two {@code ---} lines, then a body.
 * A rule, a domain and a subdomain are each one such file.
 *
 * @param keys the keys of the frontmatter, as YAML reads them
 * @param body the text after the frontmatter, stripped; empty when there is none
 */
record Frontmatter(Map<?, ?> keys, String body) {

    /** A language of a text, such as {@code fr} or {@code pt_BR}. */
    static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(_[A-Z]{2})?");

    /**
     * @param text     the text of the file
     * @param fileName the name of the file, for the messages
     * @throws IllegalArgumentException when the file has no frontmatter, or one that is not a YAML map
     */
    static Frontmatter parse(String text, String fileName) {
        // A file may start with a byte order mark, U+FEFF.
        boolean mark = !text.isEmpty() && text.charAt(0) == 0xFEFF;
        String source = (mark ? text.substring(1) : text).replace("\r\n", "\n");
        if (!source.startsWith("---\n")) {
            throw new IllegalArgumentException(fileName + " has no YAML frontmatter");
        }
        int end = source.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " has no closing ---");
        }
        int bodyStart = source.indexOf('\n', end + 4);
        String body = bodyStart < 0 ? "" : source.substring(bodyStart + 1).strip();
        return new Frontmatter(yaml(source.substring(4, end), fileName), body);
    }

    private static Map<?, ?> yaml(String front, String fileName) {
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(10);
            options.setCodePointLimit(64 * 1024);
            loaded = new Yaml(new SafeConstructor(options)).load(front);
        } catch (YAMLException e) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " is not valid YAML");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("The frontmatter of " + fileName + " is not a map");
        }
        return map;
    }
}
