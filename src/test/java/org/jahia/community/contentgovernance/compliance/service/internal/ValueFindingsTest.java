package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.check.PatternCheck;
import org.jahia.community.contentgovernance.check.Registries;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.internal.EditorialFrameServiceImpl;
import org.jahia.services.content.JCRTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * The Compliance scan on one value, through the check registry, and {@code frame.check} on the same text,
 * with the same rules and checks: both give the same findings.
 */
class ValueFindingsTest {

    private static final String SITE = "acme";
    private static final String INTRO = "/sites/acme/home/main/intro";
    private static final String LEGAL = "/sites/acme/contents/compliance-glossaries/legal";
    private static final String ACME = "/sites/acme/contents/content-insights/acme/rules";
    private static final String RICH = "<p>Click <b>here</b> to meet Sackville-Baggins and Frodon, for 12.50 EUR.</p>";
    private static final String PLAIN = "Under 20.00 EUR & < 3 days: click here, Frodon";

    /** The check of another module, as the test module registers it: a price typed in a text, with no place. */
    private static final ContentCheck PRICE = new ContentCheck() {
        private final Pattern price = Pattern.compile("\\d+[.,]\\d{2}\\s?EUR");

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.VALUE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            List<Finding> found = new ArrayList<>();
            Matcher matcher = price.matcher(input.text());
            while (matcher.find()) {
                found.add(Finding.of(rule, matcher.group()));
            }
            return found;
        }
    };

    private final ContentRules site = siteRules();
    private CheckRegistry checks;
    private MockedStatic<JCRTemplate> templates;

    private static ContentRule rule(String folder, Map<String, Object> values) {
        return RuleValues.toRule(values).inFolder(folder);
    }

    /** The rules of the site: the terms of the legal team, two glossary rules, and a rule of another module. */
    private static ContentRules siteRules() {
        return ContentRules.base()
                .with(rule(LEGAL, Map.of(RuleValues.RULE_ID, "compliance.click-here", RuleValues.KIND, "replace",
                        RuleValues.PATTERN, "click here", RuleValues.REPLACEMENT, "select", RuleValues.ADVICE, "Name the action.")))
                .with(rule(LEGAL, Map.of(RuleValues.RULE_ID, "compliance.baggins", RuleValues.PATTERN, "Baggins")))
                .with(rule(LEGAL, Map.of(RuleValues.RULE_ID, "compliance.sackville", RuleValues.PATTERN, "Sackville-Baggins")))
                .with(rule(LEGAL, Map.of(RuleValues.RULE_ID, "glossary.frodo-fr", RuleValues.KIND, "replace",
                        RuleValues.MATCH_MODE, "term", RuleValues.PATTERN, "Frodo", RuleValues.PATTERN_LANGUAGE, "en",
                        RuleValues.REPLACEMENT, "Frodon", RuleValues.RULE_LANGUAGE, "fr")))
                .with(rule(LEGAL, Map.of(RuleValues.RULE_ID, "glossary.frodo-es", RuleValues.KIND, "replace",
                        RuleValues.MATCH_MODE, "term", RuleValues.PATTERN, "Frodo", RuleValues.PATTERN_LANGUAGE, "en",
                        RuleValues.REPLACEMENT, "Frodo", RuleValues.RULE_LANGUAGE, "es")))
                .with(rule(ACME, Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                        RuleValues.SUBDOMAINS, List.of("writing"), RuleValues.SEVERITY, "serious", RuleValues.LABEL, "Prices from the ERP")));
    }

    @BeforeEach
    void bindChecksAndRules() throws RepositoryException {
        checks = new CheckRegistry();
        Registries.bind(checks, new PatternCheck(), ContentRule.PATTERN);
        Registries.bind(checks, PRICE, "acme.erpPrice");
        // frame.check reads the rules of the site with a system session
        JCRTemplate template = mock(JCRTemplate.class);
        templates = mockStatic(JCRTemplate.class);
        templates.when(JCRTemplate::getInstance).thenReturn(template);
        doReturn(site).when(template).doExecuteWithSystemSessionAsUser(any(), eq("default"), any(), any());
    }

    @AfterEach
    void closeTemplates() {
        templates.close();
    }

    private ValueFindings scan(String language, String value, boolean rich) {
        ComplianceRules rules = ComplianceRules.of(site, rule -> checks.runs(rule, ContentCheck.Scope.VALUE), language,
                LocalDate.now(), path -> new Glossary(path, null, "Title", 0));
        return ValueFindings.of(checks, rules, INTRO, language, value, rich);
    }

    @Test
    @DisplayName("the scan gives each finding of the registry with its term, its replacement and its place, in the order of the text")
    void of_placesTheFindingsOfTheRegistry() {
        ValueFindings found = scan("es", RICH, true);

        assertThat(found.text()).isEqualTo("Click here to meet Sackville-Baggins and Frodon, for 12.50 EUR.");
        assertThat(found.capped()).isFalse();
        assertThat(found.findings()).extracting(placed -> placed.rule().id(), placed -> placed.excerpt(found.text()).match(),
                placed -> placed.term("en"), PlacedFinding::replacement, placed -> placed.rule().folder()).containsExactly(
                tuple("compliance.click-here", "Click here", "click here", "select", LEGAL),
                tuple("compliance.sackville", "Sackville-Baggins", "Sackville-Baggins", null, LEGAL),
                // A form of the French glossary rule in a Spanish text, with the Spanish form to write
                tuple("glossary.frodo-fr", "Frodon", "Frodon", "Frodo", LEGAL),
                // The check of another module gives no place: the scan marks the first place of its text, and its
                // term is the label of its rule
                tuple("acme.erpPrice", "12.50 EUR", "Prices from the ERP", null, ACME));
        Excerpt price = found.findings().get(3).excerpt(found.text());
        assertThat(price.before()).isEqualTo("Click here to meet Sackville-Baggins and Frodon, for ");
        assertThat(price.after()).isEqualTo(".");
    }

    @Test
    @DisplayName("a plain value keeps its < and its &, and its findings keep their place")
    void of_readsAPlainValueAsItIsWritten() {
        ValueFindings found = scan("en", PLAIN, false);

        assertThat(found.text()).isEqualTo(PLAIN);
        assertThat(found.findings()).extracting(placed -> placed.rule().id(), placed -> placed.excerpt(found.text()).match())
                .containsExactly(tuple("acme.erpPrice", "20.00 EUR"), tuple("compliance.click-here", "click here"),
                        tuple("glossary.frodo-fr", "Frodon"));
    }

    @Test
    @DisplayName("a rule that the value breaks in more places than the registry keeps marks the value as cut")
    void of_saysWhenARuleGaveAllTheFindingsThatTheRegistryKeeps() {
        assertThat(scan("en", "click here ".repeat(CheckRegistry.MAX_FINDINGS + 5), false).capped()).isTrue();
        assertThat(scan("en", "click here ".repeat(CheckRegistry.MAX_FINDINGS - 1), false).capped()).isFalse();
    }

    @Test
    @DisplayName("frame.check and the Compliance scan give the same findings for the same text")
    void of_findsWhatFrameCheckFinds() {
        for (String language : List.of("es", "en", "fr")) {
            for (String text : List.of(RICH, PLAIN)) {
                ValueFindings found = scan(language, text, text.equals(RICH));
                List<RuleViolation> violations = new EditorialFrameServiceImpl(checks).check(SITE, language, text, false);

                assertThat(violations).as(language + ": " + text).isNotEmpty();
                assertThat(found.findings()).as(language + ": " + text)
                        .extracting(placed -> placed.rule().id(), placed -> placed.excerpt(found.text()).match(),
                                PlacedFinding::replacement)
                        .containsExactlyInAnyOrderElementsOf(violations.stream()
                                .map(violation -> tuple(violation.ruleId(), violation.match(), violation.replacement())).toList());
            }
        }
    }
}
