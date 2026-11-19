/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import org.jahia.pm.modules.editorialframe.api.WritingRule.Kind;
import org.jahia.pm.modules.editorialframe.api.WritingRule.MatchMode;
import org.jahia.pm.modules.editorialframe.api.WritingRule.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingRuleTest {

    private static final WritingRule BAN = new WritingRule("ban", Kind.FORBIDDEN, "Acme", MatchMode.TERM, null, null,
            Severity.BLOCKING, "No competitor name in a LinkedIn post.", "site", List.of(), List.of(), null, null, null,
            null);

    @Test
    void aRuleAppliesFromItsFirstDayToItsLastDayBothIncluded() {
        WritingRule moratorium = BAN.withValidity(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 12, 31));
        assertFalse(moratorium.inForceOn(LocalDate.of(2026, 9, 4)));
        assertTrue(moratorium.inForceOn(LocalDate.of(2026, 9, 5)));
        assertTrue(moratorium.inForceOn(LocalDate.of(2026, 12, 31)));
        assertFalse(moratorium.inForceOn(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void aRuleWithNoDateAppliesEveryDay() {
        assertTrue(BAN.inForceOn(LocalDate.of(1990, 1, 1)));
        assertTrue(BAN.withValidity(null, LocalDate.of(2026, 12, 31)).inForceOn(LocalDate.of(1990, 1, 1)));
        assertTrue(BAN.withValidity(LocalDate.of(2026, 9, 5), null).inForceOn(LocalDate.of(2099, 1, 1)));
    }

    @Test
    void eachCopyKeepsTheOtherFields() {
        WritingRule rule = BAN.withExamples(List.of("Better than Acme."), List.of("A fit for teams that need a portal."))
                .withDecision("Brand team", LocalDate.of(2026, 9, 5))
                .withValidity(null, LocalDate.of(2026, 12, 31));
        assertEquals("Acme", rule.pattern());
        assertEquals(List.of("Better than Acme."), rule.wrongExamples());
        assertEquals(List.of("A fit for teams that need a portal."), rule.rightExamples());
        assertEquals("Brand team", rule.owner());
        assertEquals(LocalDate.of(2026, 9, 5), rule.decidedOn());
        assertEquals(LocalDate.of(2026, 12, 31), rule.validUntil());
        assertTrue(BAN.wrongExamples().isEmpty(), "A copy leaves the rule it comes from unchanged");
    }
}
