package org.jahia.community.contentgovernance.check.spi;

import org.jahia.community.contentgovernance.check.spi.ContentCheck.Scope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What a check reads: a value, the HTML of a page, or a site.
 *
 * <p>A fact is a value that the caller measured. The checks {@code threshold} and {@code presence} read
 * the fact whose key is the id of their rule, such as the length of a title under {@code seo.title}.
 * Other facts have a key with a colon, which no rule id holds.</p>
 *
 * <p>Thread safety: the facts are immutable, and the shared results are a concurrent map.</p>
 */
public final class CheckInput {

    private static final String SITES = "/sites/";

    private final Scope scope;
    private final String path;
    private final String language;
    private final String text;
    private final Map<String, Object> facts;
    private final Map<String, Object> shared = new ConcurrentHashMap<>();

    private CheckInput(Scope scope, String path, String language, String text, Map<String, Object> facts) {
        this.scope = scope;
        this.path = path;
        this.language = language;
        this.text = text;
        this.facts = facts;
    }

    /**
     * @param path     the path of the node that holds the value, such as {@code /sites/digitall/home/main/intro}
     * @param language the language of the value, such as {@code en}, or null
     * @param text     the value, which may hold HTML
     * @return the input of the scope {@link Scope#VALUE}
     */
    public static CheckInput value(String path, String language, String text) {
        return new CheckInput(Scope.VALUE, path, language, text, Map.of());
    }

    /**
     * @param path     the path of the page or of the content item that was rendered
     * @param language the language of the render, such as {@code en}, or null
     * @param html     the rendered HTML
     * @return the input of the scope {@link Scope#PAGE}
     */
    public static CheckInput page(String path, String language, String html) {
        return new CheckInput(Scope.PAGE, path, language, html, Map.of());
    }

    /**
     * @param siteKey the key of the site, such as {@code digitall}
     * @return the input of the scope {@link Scope#SITE}, with no text
     */
    public static CheckInput site(String siteKey) {
        return new CheckInput(Scope.SITE, SITES + siteKey, null, null, Map.of());
    }

    /**
     * @param key   the id of the rule whose value was measured, or a key with a colon
     * @param value the fact, never null
     * @return a copy of this input with the fact
     */
    public CheckInput withFact(String key, Object value) {
        Map<String, Object> out = new HashMap<>(facts);
        out.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return new CheckInput(scope, path, language, text, Collections.unmodifiableMap(out));
    }

    public Scope scope() {
        return scope;
    }

    public String path() {
        return path;
    }

    /**
     * @return the key of the site of the path, or null when the path is not under {@code /sites/}
     */
    public String siteKey() {
        if (path == null || !path.startsWith(SITES) || path.length() == SITES.length()) {
            return null;
        }
        int end = path.indexOf('/', SITES.length());
        return end < 0 ? path.substring(SITES.length()) : path.substring(SITES.length(), end);
    }

    public String language() {
        return language;
    }

    /**
     * @return the value or the HTML, or null for a site
     */
    public String text() {
        return text;
    }

    /**
     * @param key the id of a rule, or a key with a colon
     * @return the fact, or null when the caller did not measure it
     */
    public Object fact(String key) {
        return facts.get(key);
    }

    /**
     * A result that several checks read, computed once per input, such as the parsed HTML.
     *
     * @param key     the name of the result, with the id of the module as a prefix
     * @param type    the type of the result
     * @param compute computes the result the first time
     * @return the result
     */
    public <T> T shared(String key, Class<T> type, Supplier<? extends T> compute) {
        return type.cast(shared.computeIfAbsent(key, ignored -> compute.get()));
    }
}
