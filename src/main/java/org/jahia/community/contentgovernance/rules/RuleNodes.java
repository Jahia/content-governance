package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
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
import java.util.TreeMap;

/**
 * Reads and writes the {@link RuleValues} of a {@value ContentRules#TYPE} node. The session of the node
 * has the English locale, so that the internationalized advice is its English value. A key of a
 * {@link CheckType} is on the mixin of that kind, so a write puts the mixin of the check type of the values
 * on the node first. A rule node under a subdomain node of the tree takes its subdomain from that parent, so the
 * read gives the subdomain of the parent and the write leaves the property out.
 */
final class RuleNodes {

    /** The fingerprint of the values that the seeder last wrote on the node. */
    static final String SEED_DIGEST = "seedDigest";
    /** Set on a rule that a site changed and that its module no longer ships. */
    static final String RETIRED = "retired";

    private RuleNodes() {
    }

    /** The values that the node holds, with the subdomain of its parent node in the tree. */
    static Map<String, Object> read(JCRNodeWrapper node) throws RepositoryException {
        Map<String, Object> values = new LinkedHashMap<>();
        String parentSubdomain = parentSubdomain(node);
        for (String key : RuleValues.KEYS) {
            if (RuleValues.SUBDOMAIN.equals(key) && parentSubdomain != null) {
                values.put(key, parentSubdomain);
                continue;
            }
            if (!node.hasProperty(key)) {
                continue;
            }
            Object value = value(node, key);
            if (value != null && !"".equals(value)) {
                values.put(key, value);
            }
        }
        if (!values.containsKey(RuleValues.ADVICE)) {
            String advice = adviceInAnyLanguage(node);
            if (advice != null) {
                values.put(RuleValues.ADVICE, advice);
            }
        }
        Map<String, Map<String, String>> translations = translations(node);
        if (!translations.isEmpty()) {
            values.put(RuleValues.TRANSLATIONS, translations);
        }
        return values;
    }

    /** The id of the subdomain node that holds the rule node, or null for a rule node outside the tree. */
    static String parentSubdomain(JCRNodeWrapper node) throws RepositoryException {
        JCRNodeWrapper parent = node.getParent();
        String property = DomainValues.Level.SUBDOMAIN.idProperty();
        return parent.isNodeType(ContentDomains.SUBDOMAIN_TYPE) && parent.hasProperty(property)
                ? parent.getProperty(property).getString() : null;
    }

    /**
     * The title, the label and the advice of the node in each language but English. Example: the French
     * advice that a site wrote on a shipped rule.
     */
    private static Map<String, Map<String, String>> translations(JCRNodeWrapper node) throws RepositoryException {
        Map<String, Map<String, String>> out = new TreeMap<>();
        Node real = node.getRealNode();
        if (real == null) {
            return out;
        }
        NodeIterator found = real.getNodes("j:translation_*");
        while (found != null && found.hasNext()) {
            Node translation = found.nextNode();
            String language = translation.hasProperty("jcr:language") ? translation.getProperty("jcr:language").getString() : null;
            if (language != null && !"en".equals(language)) {
                Map<String, String> texts = texts(translation);
                if (!texts.isEmpty()) {
                    out.put(language, texts);
                }
            }
        }
        return out;
    }

    /** The title, the label and the advice that one translation node holds, by key. */
    private static Map<String, String> texts(Node translation) throws RepositoryException {
        Map<String, String> out = new TreeMap<>();
        for (String key : RuleValues.TRANSLATABLE) {
            String text = translation.hasProperty(key) ? translation.getProperty(key).getString() : "";
            if (!text.isBlank()) {
                out.put(key, text);
            }
        }
        return out;
    }

    /**
     * The advice of a node that has none in English, in the first language that has one. Example: the
     * example rule of a French site, whose advice the seeder wrote in French.
     */
    private static String adviceInAnyLanguage(JCRNodeWrapper node) throws RepositoryException {
        NodeIterator translations = node.getRealNode().getNodes("j:translation_*");
        while (translations.hasNext()) {
            Node translation = translations.nextNode();
            if (translation.hasProperty(RuleValues.ADVICE)) {
                String advice = translation.getProperty(RuleValues.ADVICE).getString();
                if (!advice.isBlank()) {
                    return advice;
                }
            }
        }
        return null;
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
     * removed, so that the node holds exactly the values; without it, the node keeps it. The check type of the
     * values goes first, with its mixin, which declares the keys of its kind.
     */
    static void write(JCRNodeWrapper node, Map<String, Object> values, boolean exact) throws RepositoryException {
        CheckType type = CheckType.of(values.get(RuleValues.CHECK_TYPE));
        if (type != null) {
            mixIn(node, type);
        }
        boolean inTree = parentSubdomain(node) != null;
        for (String key : RuleValues.KEYS) {
            if (inTree && RuleValues.SUBDOMAIN.equals(key)) {
                continue;
            }
            Object value = values.get(key);
            if (value != null) {
                set(node, key, value);
            } else if (exact && node.hasProperty(key) && !RuleValues.RULE_ID.equals(key)) {
                node.getProperty(key).remove();
            }
        }
    }

    /**
     * Writes the values that the node lacks, keeps every value it has, and returns true when it wrote one. A node
     * with no check type takes the one of the values, with its mixin. A key of a kind that the check type of the
     * node does not hold stays out. Example: a site turned a shipped rule of the check type CODE into a rule of the
     * check type AI, and the node gets no unit.
     */
    static boolean fillMissing(JCRNodeWrapper node, Map<String, Object> values) throws RepositoryException {
        Map<String, Object> present = read(node);
        boolean wrote = false;
        CheckType missing = present.containsKey(RuleValues.CHECK_TYPE) ? null : CheckType.of(values.get(RuleValues.CHECK_TYPE));
        if (missing != null) {
            mixIn(node, missing);
            set(node, RuleValues.CHECK_TYPE, missing.name());
            present.put(RuleValues.CHECK_TYPE, missing.name());
            wrote = true;
        }
        CheckType type = CheckType.of(present.get(RuleValues.CHECK_TYPE));
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            // The translations of a shipped rule stay in its file: the rule reads them from there.
            if (RuleValues.KEYS.contains(key) && !present.containsKey(key) && CheckType.holds(type, key)
                    && !RuleValues.SUBDOMAIN.equals(key)) {
                set(node, key, entry.getValue());
                wrote = true;
            }
        }
        return wrote;
    }

    /**
     * Puts the mixin of a check type on the node, and takes the mixin of each other check type off first: AI and
     * CODE both declare {@code minimum}, and the repository refuses a node that holds two declarations of one name.
     * The mixin that goes off takes its values with it.
     *
     * @return true when the mixins of the node changed
     */
    static boolean mixIn(JCRNodeWrapper node, CheckType type) throws RepositoryException {
        boolean changed = false;
        for (CheckType other : CheckType.values()) {
            if (other != type && node.isNodeType(other.mixin())) {
                node.removeMixin(other.mixin());
                changed = true;
            }
        }
        if (!node.isNodeType(type.mixin())) {
            node.addMixin(type.mixin());
            changed = true;
        }
        return changed;
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
