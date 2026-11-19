package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Reads and writes the {@link RuleValues} of a {@value ContentRules#TYPE} node. The session of the node
 * has the English locale, so that the internationalized advice is its English value.
 */
final class RuleNodes {

    /** The fingerprint of the values that the seeder last wrote on the node. */
    static final String SEED_DIGEST = "seedDigest";
    /** Set on a rule that a site changed and that its module no longer ships. */
    static final String RETIRED = "retired";

    private RuleNodes() {
    }

    /** The values that the node holds. */
    static Map<String, Object> read(JCRNodeWrapper node) throws RepositoryException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : RuleValues.KEYS) {
            if (!node.hasProperty(key)) {
                continue;
            }
            Object value = value(node, key);
            if (value != null && !"".equals(value)) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static Object value(JCRNodeWrapper node, String key) throws RepositoryException {
        if (RuleValues.BOOLEANS.contains(key)) {
            return node.getProperty(key).getBoolean();
        }
        if (RuleValues.NUMBERS.contains(key)) {
            return node.getProperty(key).getDouble();
        }
        if (RuleValues.DAYS.contains(key)) {
            Calendar calendar = node.getProperty(key).getDate();
            return calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()).toLocalDate();
        }
        if (RuleValues.LISTS.contains(key)) {
            List<String> items = new ArrayList<>();
            for (Value item : node.getProperty(key).getValues()) {
                items.add(item.getString());
            }
            return items.isEmpty() ? null : List.copyOf(items);
        }
        return node.getProperty(key).getString();
    }

    /**
     * Writes the values on the node. With {@code exact}, a property that the values do not have is
     * removed, so that the node holds exactly the values; without it, the node keeps it.
     */
    static void write(JCRNodeWrapper node, Map<String, Object> values, boolean exact) throws RepositoryException {
        for (String key : RuleValues.KEYS) {
            Object value = values.get(key);
            if (value != null) {
                set(node, key, value);
            } else if (exact && node.hasProperty(key) && !RuleValues.RULE_ID.equals(key)) {
                node.getProperty(key).remove();
            }
        }
    }

    /** Writes the values that the node lacks, keeps every value it has, and returns true when it wrote one. */
    static boolean fillMissing(JCRNodeWrapper node, Map<String, Object> values) throws RepositoryException {
        Map<String, Object> present = read(node);
        boolean wrote = false;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!present.containsKey(entry.getKey())) {
                set(node, entry.getKey(), entry.getValue());
                wrote = true;
            }
        }
        return wrote;
    }

    private static void set(JCRNodeWrapper node, String key, Object value) throws RepositoryException {
        if (value instanceof Boolean flag) {
            node.setProperty(key, flag);
        } else if (value instanceof Double number) {
            node.setProperty(key, number);
        } else if (value instanceof LocalDate day) {
            Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
            calendar.clear();
            calendar.set(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
            node.setProperty(key, calendar);
        } else if (value instanceof List<?> list) {
            node.setProperty(key, list.stream().map(String::valueOf).toArray(String[]::new));
        } else if (!RuleValues.RULE_ID.equals(key) || !node.hasProperty(key)) {
            // The seeder never changes the id of a node: it finds the node by its id.
            node.setProperty(key, value.toString());
        }
    }

    static String text(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }
}
