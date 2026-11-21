package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.check.spi.RuleSetCheck;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.community.contentgovernance.text.PlainText;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.osgi.service.component.annotations.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The evaluator {@code pattern}: the writing rule of each rule node, which {@link RuleChecker} finds in a
 * value with no model. The rules of one input apply together, as {@link RuleSetCheck} states: a term inside
 * a longer term of another rule gives no finding there, and a glossary rule of another language reports a
 * form of its term. A rule of the kind {@code mandatory} needs a whole document, so it gives no finding on
 * one value.
 *
 * <p>The check reads the value as {@link PlainText#fromHtml} reads it, as the Compliance scan does. A
 * finding holds the matched text, the text around it, and the values {@link Finding#START} and
 * {@link Finding#END}, the place of the match in that text, {@link Finding#TERM}, the term that the text
 * holds, and {@link Finding#REPLACEMENT}, what to write instead, when the rule names it. Example: with the
 * glossary rules that write "Frodo" as "Frodon" in French and as "Frodo" in Spanish, the Spanish text
 * "Frodon llega" gives the finding "Frodon" with {@code {start: 0, end: 6, term: "Frodon", replacement:
 * "Frodo"}}.</p>
 *
 * <p>Thread safety: the finders are immutable, and kept in a concurrent map.</p>
 */
@Component(service = ContentCheck.class, property = ContentCheck.ID + "=" + ContentRule.PATTERN)
public class PatternCheck implements RuleSetCheck {

    /** The most sets of rules whose finder is kept. */
    private static final int KEPT_FINDERS = 16;

    /** The finders of the last sets of rules: a scan of a site reads thousands of texts with one set. */
    private final Map<RuleSet, RuleChecker.Finder> finders = new ConcurrentHashMap<>();

    @Override
    public Set<Scope> scopes() {
        return Set.of(Scope.VALUE);
    }

    @Override
    public List<Finding> check(CheckInput input, List<? extends Rule> rules) {
        if (input.text() == null || rules.isEmpty()) {
            return List.of();
        }
        RuleChecker.Finder finder = finder(new RuleSet(List.copyOf(rules), input.language(), LocalDate.now()));
        if (finder.isEmpty()) {
            return List.of();
        }
        String plain = PlainText.fromHtml(input.text());
        Map<String, Rule> byId = new HashMap<>();
        rules.forEach(rule -> byId.putIfAbsent(rule.id(), rule));
        List<Finding> found = new ArrayList<>();
        for (RuleChecker.Match match : finder.find(plain)) {
            found.add(finding(byId.get(match.rule().id()), plain, match));
        }
        return found;
    }

    private RuleChecker.Finder finder(RuleSet set) {
        RuleChecker.Finder finder = finders.get(set);
        if (finder == null) {
            if (finders.size() >= KEPT_FINDERS) {
                // A set of rules that comes back is compiled again
                finders.clear();
            }
            List<WritingRule> writing = new ArrayList<>();
            for (Rule rule : set.rules()) {
                WritingRule one = writingRule(rule);
                if (one != null && one.inForceOn(set.day())) {
                    writing.add(one);
                }
            }
            finder = new RuleChecker.Finder(writing, set.language(), CheckRegistry.MAX_FINDINGS);
            finders.put(set, finder);
        }
        return finder;
    }

    private static Finding finding(Rule rule, String plain, RuleChecker.Match match) {
        WritingRule writing = match.rule();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(Finding.START, match.start());
        values.put(Finding.END, match.end());
        values.put(Finding.TERM, writing.pattern());
        if (writing.replacement() != null) {
            values.put(Finding.REPLACEMENT, writing.replacement());
        }
        return new Finding(rule.id(), rule.severity(), plain.substring(match.start(), match.end()),
                RuleChecker.excerpt(plain, match.start(), match.end()), null, values);
    }

    /**
     * The rules of one input, on a day, for a text of one language. Two sets are equal when they hold the same
     * rule objects in the same order: a scan reads the rules of its site once, so its texts share one finder.
     */
    private record RuleSet(List<? extends Rule> rules, String language, LocalDate day) {

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RuleSet set) || set.rules.size() != rules.size()
                    || !Objects.equals(set.language, language) || !set.day.equals(day)) {
                return false;
            }
            for (int i = 0; i < rules.size(); i++) {
                if (set.rules.get(i) != rules.get(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = Objects.hash(language, day);
            for (Rule rule : rules) {
                hash = 31 * hash + System.identityHashCode(rule);
            }
            return hash;
        }
    }

    /** The writing rule of the values of a rule, or null when the rule has no pattern or an unknown kind. */
    public static WritingRule writingRule(Rule rule) {
        String pattern = string(rule, RuleValues.PATTERN);
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        WritingRule.Kind kind;
        WritingRule.MatchMode matchMode;
        try {
            kind = WritingRule.Kind.parse(string(rule, RuleValues.KIND));
            matchMode = WritingRule.MatchMode.parse(string(rule, RuleValues.MATCH_MODE));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (kind == WritingRule.Kind.GLOSSARY) {
            return null;
        }
        return new WritingRule(rule.id(), kind, pattern, matchMode, string(rule, RuleValues.REPLACEMENT),
                string(rule, RuleValues.RULE_LANGUAGE),
                rule.blocking() ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                rule.advice(), null, List.of(), List.of(), string(rule, RuleValues.OWNER), null,
                day(rule, RuleValues.VALID_FROM), day(rule, RuleValues.VALID_UNTIL),
                strings(rule, RuleValues.PATTERN_FORMS), strings(rule, RuleValues.REPLACEMENT_FORMS),
                string(rule, RuleValues.PATTERN_LANGUAGE), rule instanceof ContentRule content ? content.folder() : null);
    }

    private static List<String> strings(Rule rule, String property) {
        return rule.value(property) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String string(Rule rule, String property) {
        Object value = rule.value(property);
        return value == null ? null : value.toString();
    }

    private static LocalDate day(Rule rule, String property) {
        return rule.value(property) instanceof LocalDate day ? day : null;
    }
}
