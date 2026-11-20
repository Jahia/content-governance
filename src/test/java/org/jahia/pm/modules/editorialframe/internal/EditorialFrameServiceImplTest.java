/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorialFrameServiceImplTest {

    @Test
    void aVoiceAllowsTheVoiceOfAnEditorAtItsPathsAndUnderThem() {
        Frame frame = new Frame("digitall", "en",
                List.of(new FrameText("voice", "Voice", "site", "Text.", List.of(" ", "/sites/digitall/home/news/")),
                        new FrameText("group", "Group", "group", "Text.", List.of(" /sites/digitall/blog "))),
                List.of(), List.of(), "abc123def456");

        assertTrue(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/home/news/item"));
        assertFalse(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/home/news"),
                "a path with a final slash allows the paths under it only");
        assertTrue(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/blog"));
        assertTrue(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/blog/post"));
        assertFalse(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/blogger"));
        assertFalse(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/home"));
    }

    @Test
    void aFrameWithNoPathAllowsNoVoiceOfAnEditor() {
        Frame frame = new Frame("digitall", "en", List.of(new FrameText("voice", "Voice", "site", "Text.", List.of())),
                List.of(), List.of(), "abc123def456");

        assertFalse(EditorialFrameServiceImpl.allowsAuthorVoice(frame, "/sites/digitall/home"));
    }

    @Test
    void aFindingOfARegisteredCheck_isAViolationWithTheRuleOfTheSite() {
        ContentRule rule = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice", RuleValues.EVALUATOR, "acme.erpPrice",
                RuleValues.BLOCKING, true, RuleValues.ADVICE, "Insert the product."));

        RuleViolation violation = EditorialFrameServiceImpl.violation(rule, "12.50 EUR", "costs 12.50 EUR today");

        assertEquals(new RuleViolation("acme.erpPrice", WritingRule.Kind.FORBIDDEN, WritingRule.Severity.BLOCKING, null,
                "12.50 EUR", null, "Insert the product.", "costs 12.50 EUR today"), violation);
    }
}
