package org.jahia.community.contentgovernance.page;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.html.rules.HtmlRuleChecks;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The checks of one domain of a page, one per check id, which share one reading of the input. A check of
 * a measure rule, such as {@code readability.statistics}, finds nothing and gives values only.
 *
 * <p>Thread safety: immutable. The shared reading lives in the input.</p>
 */
final class Family {

    private Family() {
    }

    /** A text that breaks a rule, the text around it or null, and a CSS selector of its element or null. */
    record Hit(String text, String context, String locator) {
    }

    /** What one reading of an input found and measured, by check id. */
    static final class Found {
        private final Map<String, List<Hit>> byCheck = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

        Found add(String checkId, String text, String context) {
            return add(checkId, text, context, null);
        }

        Found add(String checkId, String text, String context, String locator) {
            byCheck.computeIfAbsent(checkId, ignored -> new ArrayList<>()).add(new Hit(text, context, locator));
            return this;
        }

        /** The values of the result of a check, such as the link counts under {@code links.broken}. */
        Found values(String checkId, Map<String, Object> measured) {
            values.put(checkId, measured);
            return this;
        }

        List<Hit> of(String checkId) {
            return byCheck.getOrDefault(checkId, List.of());
        }

        Map<String, Object> valuesOf(String checkId) {
            return values.getOrDefault(checkId, Map.of());
        }
    }

    /**
     * @param ids    the ids of the checks of the domain
     * @param shared the key of the reading in the input, with the id of the module as a prefix
     * @param read   reads the input once for every check of the domain
     * @return the checks by id
     */
    static Map<String, ContentCheck> checks(List<String> ids, String shared, Function<CheckInput, Found> read) {
        Map<String, ContentCheck> out = new LinkedHashMap<>();
        ids.forEach(id -> out.put(id, new Check(id, shared, read)));
        return out;
    }

    /** The parsed HTML of the input, which the caller gives as a fact or which is parsed from its text. */
    static Document document(CheckInput input) {
        if (input.fact(HtmlRuleChecks.DOCUMENT) instanceof Document document) {
            return document;
        }
        return Jsoup.parse(input.text() == null ? "" : input.text());
    }

    /** True when the HTML is a whole page, and not one content item. Without the fact, a whole page. */
    static boolean wholePage(CheckInput input) {
        return input.fact(HtmlRuleChecks.HTML_SCOPE) != org.jahia.community.contentgovernance.html.rules.Scope.ITEM;
    }

    /** The opening tag of an element, without its content, cut at 160 characters. */
    static String tag(Element element) {
        return cut(element.clone().empty().outerHtml(), 160);
    }

    /** A CSS selector of the element, or null when jsoup cannot build one. */
    static String locator(Element element) {
        try {
            return element.cssSelector();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String cut(String text, int length) {
        return text.length() > length ? text.substring(0, length) + "…" : text;
    }

    private record Check(String id, String shared, Function<CheckInput, Found> read) implements ContentCheck {

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.PAGE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            return input.shared(shared, Found.class, () -> read.apply(input)).of(id).stream()
                    .map(hit -> Finding.at(rule, hit.text(), hit.context(), hit.locator()))
                    .toList();
        }

        @Override
        public CheckResult result(CheckInput input, Rule rule) {
            Found found = input.shared(shared, Found.class, () -> read.apply(input));
            return CheckResult.of(rule, found.valuesOf(id), check(input, rule));
        }
    }
}
