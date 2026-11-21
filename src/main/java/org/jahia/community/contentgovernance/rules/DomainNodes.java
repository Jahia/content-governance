package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes the {@link DomainValues} of a domain or a subdomain node. The session of the node has
 * the English locale, so that the internationalized label and help are their English values.
 */
final class DomainNodes {

    private DomainNodes() {
    }

    /** The level of a node, or null when it is neither a domain nor a subdomain. */
    static Level levelOf(JCRNodeWrapper node) throws RepositoryException {
        for (Level level : Level.values()) {
            if (node.isNodeType(level.type())) {
                return level;
            }
        }
        return null;
    }

    /** The values that the node holds, with its texts in the languages other than English. */
    static Map<String, Object> read(JCRNodeWrapper node, Level level) throws RepositoryException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : level.keys()) {
            String property = level.property(key);
            if (node.hasProperty(property)) {
                Object value = value(node, key, property);
                if (value != null && !"".equals(value)) {
                    values.put(key, value);
                }
            }
        }
        Map<String, Map<String, String>> translations = translations(node);
        if (!translations.isEmpty()) {
            values.put(DomainValues.TRANSLATIONS, translations);
        }
        return values;
    }

    private static Object value(JCRNodeWrapper node, String key, String property) throws RepositoryException {
        if (DomainValues.ORDER.equals(key)) {
            return node.getProperty(property).getLong();
        }
        if (DomainValues.SITES.equals(key)) {
            List<String> items = new ArrayList<>();
            for (Value item : node.getProperty(property).getValues()) {
                items.add(item.getString());
            }
            return items.isEmpty() ? null : List.copyOf(items);
        }
        return node.getProperty(property).getString();
    }

    /** The label and the help of the node in each language but English. */
    private static Map<String, Map<String, String>> translations(JCRNodeWrapper node) throws RepositoryException {
        Map<String, Map<String, String>> out = new TreeMap<>();
        Node real = node.getRealNode();
        NodeIterator found = real == null ? null : real.getNodes("j:translation_*");
        while (found != null && found.hasNext()) {
            Node translation = found.nextNode();
            String language = translation.hasProperty("jcr:language") ? translation.getProperty("jcr:language").getString() : null;
            Map<String, String> texts = language == null || "en".equals(language) ? Map.of() : texts(translation);
            if (!texts.isEmpty()) {
                out.put(language, texts);
            }
        }
        return out;
    }

    /** The label and the help that one translation node holds, by key. */
    private static Map<String, String> texts(Node translation) throws RepositoryException {
        Map<String, String> texts = new TreeMap<>();
        for (String key : DomainValues.TRANSLATABLE) {
            String text = translation.hasProperty(key) ? translation.getProperty(key).getString() : "";
            if (!text.isBlank()) {
                texts.put(key, text);
            }
        }
        return texts;
    }

    /**
     * Writes the values on the node. With {@code exact}, a property that the values do not have is
     * removed; without it, the node keeps it. The id of a node never changes: the seeder finds the node by it.
     */
    static void write(JCRNodeWrapper node, Level level, Map<String, Object> values, boolean exact) throws RepositoryException {
        for (String key : level.keys()) {
            String property = level.property(key);
            Object value = values.get(key);
            if (value != null) {
                set(node, property, value);
            } else if (exact && node.hasProperty(property) && !DomainValues.ID.equals(key)) {
                node.getProperty(property).remove();
            }
        }
    }

    /** Writes the values that the node lacks, keeps every value it has, and returns true when it wrote one. */
    static boolean fillMissing(JCRNodeWrapper node, Level level, Map<String, Object> values) throws RepositoryException {
        Map<String, Object> present = read(node, level);
        boolean wrote = false;
        for (String key : level.keys()) {
            if (values.get(key) != null && !present.containsKey(key)) {
                set(node, level.property(key), values.get(key));
                wrote = true;
            }
        }
        return wrote;
    }

    private static void set(JCRNodeWrapper node, String property, Object value) throws RepositoryException {
        if (value instanceof Long number) {
            node.setProperty(property, number);
        } else if (value instanceof List<?> list) {
            node.setProperty(property, list.stream().map(String::valueOf).toArray(String[]::new));
        } else {
            node.setProperty(property, value.toString());
        }
    }

    static String text(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }
}
