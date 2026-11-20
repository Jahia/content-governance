/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorialFrameServiceImplTest {

    @Test
    void aFindingOfARegisteredCheck_isAViolationWithTheRuleOfTheSite() {
        ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.BLOCKING, true, RuleValues.ADVICE, "Insert the product."));

        RuleViolation violation = EditorialFrameServiceImpl.violation(rule, "12.50 EUR", "costs 12.50 EUR today");

        assertEquals(new RuleViolation("acme.erpPrice", WritingRule.Kind.FORBIDDEN, WritingRule.Severity.BLOCKING, null,
                "12.50 EUR", null, "Insert the product.", "costs 12.50 EUR today"), violation);
    }
}
