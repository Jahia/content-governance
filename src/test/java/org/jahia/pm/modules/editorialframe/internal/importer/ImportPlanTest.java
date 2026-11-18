/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPlanTest {

    private static final String TONE = "---\nname: brand-tone\ndescription: The voice.\ntype: context\n---\n# Brand tone\nClear and human.\n";
    private static final String AUDIENCE = "---\nname: audience\ndescription: The readers.\n---\n# Audience\n"
            + "## Global\nIgnored.\n## Persona Messaging Framework\n### CIO / CTO\n- Cares about governance.\n"
            + "### CMO\n- Cares about growth.\n## Other\nIgnored.\n";
    private static final String RULES = "forbidden_terms:\n  - seamless\n  - game changer\n"
            + "forbidden_terms_fr:\n  - term: \"en nuage\"\n    replacement: \"cloud\"\nrisk_levels:\n  safe: {}\n";
    private static final String REVIEW = "---\nname: review-blog-article\ndescription: Review a blog draft.\n"
            + "depends_on:\n  - context/brand-tone\n  - _references/quality.md\n---\n# Review\nCheck it.\n";

    private static Map<String, String> files() {
        Map<String, String> files = new TreeMap<>();
        files.put("context/brand-tone/SKILL.md", TONE);
        files.put("context/audience/SKILL.md", AUDIENCE);
        files.put("_rules/claims.yml", RULES);
        files.put("_references/quality.md", "# Quality bar");
        files.put("skills/content/review-blog-article/SKILL.md", REVIEW);
        files.put("skills/content/review-blog-article/_sub-agents/checker/SKILL.md",
                "---\nname: checker\ndescription: A sub-agent.\n---\nCheck.");
        files.put("skills/content/review-blog-article/scripts/run.md", "never kept");
        return files;
    }

    private static ImportPlan.Options options() {
        ImportPlan.Options options = new ImportPlan.Options();
        options.language = "en";
        options.voice = "context/brand-tone";
        options.personas = "context/audience";
        options.rules = List.of("_rules/claims.yml");
        options.rulesSeverity = "blocking";
        return options;
    }

    @Test
    void aCsvFileGivesOneGlossaryTermPerLanguagePair() {
        Map<String, String> files = new TreeMap<>();
        files.put("_glossary/terms.csv", "en,fr,de,Note,comment\r\n"
                + "access token,jeton d'accès,Zugriffstoken,\"never \"\"jeton d'authentification\"\"\",x\r\n"
                + "Jahia,Jahia,,a brand name\r\n"
                + "\r\n"
                + ",orphan,,\r\n");
        ImportPlan.Options options = new ImportPlan.Options();
        options.glossary = List.of("_glossary/terms.csv", "_glossary/missing.csv");

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(3, plan.glossary.size());
        GlossaryTerm token = plan.glossary.get(0).term;
        assertEquals("access token", token.term());
        assertEquals("en", token.termLanguage());
        assertEquals("jeton d'accès", token.translation());
        assertEquals("fr", token.translationLanguage());
        assertEquals("never \"jeton d'authentification\"", token.note());
        assertEquals("de", plan.glossary.get(1).term.translationLanguage());
        assertTrue(plan.glossary.get(2).term.kept());
        assertEquals("_glossary/terms.csv", plan.glossary.get(2).source);
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("'comment'")), plan.warnings.toString());
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("missing.csv")), plan.warnings.toString());
    }

    @Test
    void aCsvFileGivesTheFormsTheDefinitionAndTheStatusOfATerm() {
        Map<String, String> files = new TreeMap<>();
        files.put("terms.csv", "en,fr,Definition,Status,note\n"
                + "access token|access tokens,jeton d'accès| jetons d'accès |,A secret.,,\n"
                + "access token,jeton d'authentification,,Forbidden,another concept\n"
                + "API key,clé d'API,,obsolete,\n");
        ImportPlan.Options options = new ImportPlan.Options();
        options.glossary = List.of("terms.csv");

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(2, plan.glossary.size(), plan.warnings.toString());
        GlossaryTerm token = plan.glossary.get(0).term;
        assertEquals("access token", token.term());
        assertEquals(List.of("access tokens"), token.termForms());
        assertEquals("jeton d'accès", token.translation());
        assertEquals(List.of("jetons d'accès"), token.translationForms());
        assertEquals("A secret.", token.definition());
        assertEquals(GlossaryTerm.Status.APPROVED, token.status());
        GlossaryTerm forbidden = plan.glossary.get(1).term;
        assertEquals(GlossaryTerm.Status.FORBIDDEN, forbidden.status());
        assertEquals("another concept", forbidden.note());
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("'obsolete'")), plan.warnings.toString());
        assertTrue(plan.warnings.stream().noneMatch(w -> w.contains("column")), plan.warnings.toString());
    }

    @Test
    void theOptionsMapAVoicePersonasAndRules() {
        ImportPlan plan = ImportPlan.of(files(), options());
        assertEquals("brand-tone", plan.voice.name);
        assertEquals("Brand tone", plan.voice.title);
        assertEquals(2, plan.personas.size());
        assertEquals("cio-cto", plan.personas.get(0).name);
        assertTrue(plan.personas.get(0).isDefault);
        assertTrue(plan.personas.get(1).body.contains("growth"));
        assertEquals(3, plan.rules.size());
        assertEquals("seamless*", plan.rules.get(0).rule.pattern());
        assertEquals("game changer", plan.rules.get(1).rule.pattern());
        WritingRule replace = plan.rules.get(2).rule;
        assertEquals(WritingRule.Kind.REPLACE, replace.kind());
        assertEquals("fr", replace.language());
        assertEquals(WritingRule.Severity.BLOCKING, replace.severity());
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("risk_levels")));
    }

    @Test
    void anExampleOfAFileGoesToEachRuleOfThatFileThatItsWrongTextBreaks() {
        String claims = "forbidden_terms:\n  - seamless\n  - empower\n"
                + "unsafe_patterns:\n  - regex: \"(the\\\\s+)?leading\\\\s+(DXP|CMS)\"\n    risk: unsafe\n"
                + "rewrite_examples:\n"
                + "  - weak: \"Jahia is the leading DXP.\"\n    safer: \"Jahia is a composable DXP.\"\n    category: \"Superiority\"\n"
                + "  - weak: \"Jahia guarantees GDPR compliance.\"\n    safer: \"Jahia supports governance.\"\n"
                + "counter_examples:\n"
                + "  - category: \"Forbidden terms\"\n    bad:\n      text: \"A seamless platform that empowers teams.\"\n"
                + "      reason: \"Two forbidden terms.\"\n    good:\n      text: \"A platform that helps teams.\"\n";
        Map<String, String> files = new TreeMap<>(Map.of("_rules/claims.yml", claims, "_rules/other.yml",
                "forbidden_terms:\n  - leading\n"));
        ImportPlan.Options options = new ImportPlan.Options();
        options.rules = List.of("_rules/claims.yml", "_rules/other.yml");

        ImportPlan plan = ImportPlan.of(files, options);

        WritingRule seamless = plan.rules.get(0).rule;
        assertEquals(List.of("A seamless platform that empowers teams."), seamless.wrongExamples());
        assertEquals(List.of("A platform that helps teams."), seamless.rightExamples());
        assertEquals(seamless.wrongExamples(), plan.rules.get(1).rule.wrongExamples(), "empower* is broken too");
        WritingRule leading = plan.rules.get(2).rule;
        assertEquals(List.of("Jahia is the leading DXP."), leading.wrongExamples());
        assertEquals(List.of("Jahia is a composable DXP."), leading.rightExamples());
        assertTrue(plan.rules.get(3).rule.wrongExamples().isEmpty(), "An example never goes to a rule of another file");
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("\"Jahia guarantees GDPR compliance.\" breaks no rule")),
                plan.warnings.toString());
        assertFalse(plan.warnings.stream().anyMatch(w -> w.contains("rewrite_examples") || w.contains("counter_examples")),
                plan.warnings.toString());
    }

    @Test
    void aFullRuleCarriesItsExamplesItsOwnerAndItsDates() {
        String rules = "rules:\n"
                + "  - kind: forbidden\n    pattern: Acme\n    owner: Brand team\n    decided_on: 2026-09-05\n"
                + "    valid_from: '2026-09-05'\n    valid_until: 2026-12-31\n"
                + "    wrong_examples: [\"Better than Acme.\"]\n    right_examples: \"A fit for portal teams.\"\n"
                + "  - kind: forbidden\n    pattern: Globex\n    valid_until: 31/12/2026\n";
        ImportPlan.Options options = new ImportPlan.Options();
        options.rules = List.of("_rules/moratorium.yml");

        ImportPlan plan = ImportPlan.of(Map.of("_rules/moratorium.yml", rules), options);

        assertEquals(1, plan.rules.size());
        WritingRule rule = plan.rules.get(0).rule;
        assertEquals("Brand team", rule.owner());
        assertEquals(LocalDate.of(2026, 9, 5), rule.decidedOn());
        assertEquals(LocalDate.of(2026, 9, 5), rule.validFrom());
        assertEquals(LocalDate.of(2026, 12, 31), rule.validUntil());
        assertEquals(List.of("Better than Acme."), rule.wrongExamples());
        assertEquals(List.of("A fit for portal teams."), rule.rightExamples());
        assertTrue(plan.warnings.stream().anyMatch(w -> w.contains("not YYYY-MM-DD") && w.contains("Globex")),
                plan.warnings.toString());
    }

    @Test
    void aSkillKeepsItsSubAgentsAndItsDependenciesButNoScript() {
        ImportPlan plan = ImportPlan.of(files(), options());
        assertEquals(1, plan.skills.size());
        ImportPlan.SkillItem skill = plan.skills.get(0);
        assertEquals("review-blog-article", skill.document.name);
        assertTrue(skill.files.containsKey("_sub-agents/checker/SKILL.md"));
        assertTrue(skill.files.containsKey("deps/_references/quality.md"));
        assertFalse(skill.files.containsKey("deps/context/brand-tone/SKILL.md"), "the frame already carries the voice");
        assertFalse(skill.files.containsKey("scripts/run.md"));
        assertEquals("context/brand-tone,_references/quality.md", skill.document.metadata.get("depends_on"));
    }

    @Test
    void invisibleCharactersRefuseASkill() {
        Map<String, String> files = new TreeMap<>();
        files.put("skills/x/SKILL.md", "---\nname: x\ndescription: X.\n---\nDo this​ and that.");
        ImportPlan plan = ImportPlan.of(files, new ImportPlan.Options());
        assertTrue(plan.skills.isEmpty());
        assertTrue(plan.skipped.get(0).contains("invisible"));
    }

    @Test
    void aBadNameIsFixedWithAWarning() {
        SkillDocument document = SkillDocument.parse("---\nname: My Skill\ndescription: D.\n---\nBody", "my-skill");
        assertEquals("my-skill", document.name);
        assertFalse(document.warnings.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SkillDocument.parse("---\nname: x\n---\nBody", "x"));
    }

    @Test
    void theArchiveDropsTheWrapperFolderAndUnsafePaths() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("repo-abc/skills/x/SKILL.md", "repo-abc/../evil.md", "repo-abc/image.png")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("text".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        ArchiveReader archive = ArchiveReader.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(List.of("skills/x/SKILL.md"), List.copyOf(archive.files.keySet()));
        assertTrue(archive.skipped.get(0).contains("unsafe"));
    }
}
