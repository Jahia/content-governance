/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.frame;

import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.RuleViolation;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlossaryCheckerTest {

    private static final GlossaryTerm TOKEN = new GlossaryTerm("t1", "access token", "en", "jeton d'accès", "fr",
            "never jeton d'authentification", "site");
    private static final GlossaryTerm JAHIA = new GlossaryTerm("t2", "Jahia", "en", "Jahia", "fr", null, "group");
    private static final GlossaryTerm TOKEN_DE = new GlossaryTerm("t3", "access token", "en", "Zugriffstoken", "de", null, "site");

    @Test
    void aTranslationThatLacksTheRenderingOfATermOfTheSourceBreaksTheGlossary() {
        List<RuleViolation> found = GlossaryChecker.check(List.of(TOKEN, JAHIA, TOKEN_DE), "en",
                "<p>Create an Access Token in Jahia.</p>", "fr", "<p>Créez un jeton d'authentification dans Jahia.</p>");

        assertEquals(1, found.size());
        RuleViolation violation = found.get(0);
        assertEquals(WritingRule.Kind.GLOSSARY, violation.kind());
        assertEquals("access token", violation.pattern());
        assertEquals("jeton d'accès", violation.replacement());
        assertEquals("never jeton d'authentification", violation.justification());
        assertTrue(violation.excerpt().contains("Access Token"));
    }

    @Test
    void aTranslationWithTheRenderingPassesAndATermAbsentFromTheSourceIsNotChecked() {
        assertTrue(GlossaryChecker.check(List.of(TOKEN), "en", "Create an access token.", "fr_FR",
                "Créez un Jeton d'accès.").isEmpty());
        assertTrue(GlossaryChecker.check(List.of(TOKEN), "en", "Create a password.", "fr", "Créez un mot de passe.").isEmpty());
        assertTrue(GlossaryChecker.check(List.of(TOKEN), "de", "Ein access token.", "fr", "Un jeton.").isEmpty(),
                "Another pair is not checked");
    }

    private static final GlossaryTerm TOKENS = new GlossaryTerm("t4", "access token", "en", List.of("access tokens"),
            "jeton d'accès", "fr", List.of("jetons d'accès"), "A secret that lets a program call an API.", null,
            GlossaryTerm.Status.APPROVED, "site");
    private static final GlossaryTerm AUTH_TOKEN = new GlossaryTerm("t5", "access token", "en", List.of(),
            "jeton d'authentification", "fr", List.of("jetons d'authentification"), null, "another concept",
            GlossaryTerm.Status.FORBIDDEN, "site");
    private static final GlossaryTerm DEVELOPER = new GlossaryTerm("t6", "developer", "en", List.of("developers"),
            "développeur", "fr", List.of("développeuse", "développeurs", "développeuses"), null, null,
            GlossaryTerm.Status.APPROVED, "site");

    @Test
    void aPluralOfTheTermAndOfItsRenderingPasses() {
        assertTrue(GlossaryChecker.check(List.of(TOKENS), "en", "Create access tokens.", "fr",
                "Créez des jetons d'accès.").isEmpty());
        assertTrue(GlossaryChecker.check(List.of(TOKENS), "en", "Create access tokens.", "fr",
                "Créez des jetons d’accès.").isEmpty(), "A curly apostrophe is an apostrophe");

        List<RuleViolation> found = GlossaryChecker.check(List.of(TOKENS), "en", "Create access tokens.", "fr",
                "Créez des clés.");
        assertEquals(1, found.size(), "The plural of the source finds the term");
        assertEquals("jeton d'accès", found.get(0).replacement());
    }

    @Test
    void aFeminineFormOfTheRenderingPasses() {
        assertTrue(GlossaryChecker.check(List.of(DEVELOPER), "en", "Ask a developer.", "fr",
                "Demandez à une développeuse.").isEmpty());
        GlossaryTerm noForms = new GlossaryTerm("t7", "developer", "en", "développeur", "fr", null, "site");
        assertEquals(1, GlossaryChecker.check(List.of(noForms), "en", "Ask a developer.", "fr",
                "Demandez à une développeuse.").size(), "Without its forms, the term matches its exact form only");
    }

    @Test
    void aForbiddenTranslationOfATermOfTheSourceIsFlaggedWithTheRenderingToWrite() {
        List<RuleViolation> found = GlossaryChecker.check(List.of(TOKENS, AUTH_TOKEN), "en",
                "<p>Create access tokens in Jahia.</p>", "fr", "<p>Créez des jetons d'authentification dans Jahia.</p>");

        assertEquals(1, found.size(), "The forbidden translation, and no missing rendering too: " + found);
        RuleViolation violation = found.get(0);
        assertEquals("t5", violation.ruleId());
        assertEquals(WritingRule.Kind.GLOSSARY, violation.kind());
        assertEquals("access token", violation.pattern());
        assertEquals("jetons d'authentification", violation.match());
        assertEquals("jeton d'accès", violation.replacement());
        assertEquals("another concept", violation.justification());
        assertTrue(violation.excerpt().contains("jetons d'authentification"), violation.excerpt());
    }

    @Test
    void aForbiddenTranslationIsNotFlaggedWhenTheSourceLacksTheTerm() {
        assertTrue(GlossaryChecker.check(List.of(TOKENS, AUTH_TOKEN), "en", "Sign in with a password.", "fr",
                "Un jeton d'authentification.").isEmpty());
        List<RuleViolation> found = GlossaryChecker.check(List.of(AUTH_TOKEN), "en", "Create an access token.", "fr",
                "Créez un jeton d'authentification.");
        assertEquals(1, found.size());
        assertEquals(null, found.get(0).replacement(), "No approved rendering to name");
    }
}
