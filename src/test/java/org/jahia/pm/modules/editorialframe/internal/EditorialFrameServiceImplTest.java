/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorialFrameServiceImplTest {

    @Test
    void aFindingOfARegisteredCheck_isAViolationWithTheRuleOfTheSite() {
        ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.BLOCKING, true, RuleValues.ADVICE, "Insert the product."));

        RuleViolation violation = EditorialFrameServiceImpl.violation(rule, Finding.of(rule, "12.50 EUR", "costs 12.50 EUR today"),
                "en");

        assertEquals(new RuleViolation("acme.erpPrice", WritingRule.Kind.FORBIDDEN, WritingRule.Severity.BLOCKING, null,
                "12.50 EUR", null, "Insert the product.", "costs 12.50 EUR today"), violation);
    }

    @Test
    void aTermFinding_givesTheTermAndTheReplacementThatTheCheckFound_andTheAdviceInTheLanguage() {
        ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "glossary.frodo", RuleValues.EVALUATOR, "pattern",
                RuleValues.KIND, "replace", RuleValues.PATTERN, "Frodo", RuleValues.REPLACEMENT, "Frodon",
                RuleValues.ADVICE, "Write the name of the site.",
                RuleValues.TRANSLATIONS, Map.of("es", Map.of(RuleValues.ADVICE, "Escriba el nombre del sitio."))));
        // The form of another language that a Spanish text holds, with the Spanish form to write
        Finding found = new Finding("glossary.frodo", "moderate", "Frodon", "Frodon llega", null,
                Map.of(Finding.START, 0, Finding.END, 6, Finding.TERM, "Frodon", Finding.REPLACEMENT, "Frodo"));

        RuleViolation violation = EditorialFrameServiceImpl.violation(rule, found, "es");

        assertEquals(new RuleViolation("glossary.frodo", WritingRule.Kind.REPLACE, WritingRule.Severity.WARNING, "Frodon",
                "Frodon", "Frodo", "Escriba el nombre del sitio.", "Frodon llega"), violation);
    }
}
