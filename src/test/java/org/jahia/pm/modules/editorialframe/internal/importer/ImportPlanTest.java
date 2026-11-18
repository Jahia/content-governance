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
        options.setLanguage("en");
        options.setVoice("context/brand-tone");
        options.setPersonas("context/audience");
        options.setRules(List.of("_rules/claims.yml"));
        options.setRulesSeverity("blocking");
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
        options.setGlossary(List.of("_glossary/terms.csv", "_glossary/missing.csv"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(3, plan.glossary().size());
        GlossaryTerm token = plan.glossary().get(0).term;
        assertEquals("access token", token.term());
        assertEquals("en", token.termLanguage());
        assertEquals("jeton d'accès", token.translation());
        assertEquals("fr", token.translationLanguage());
        assertEquals("never \"jeton d'authentification\"", token.note());
        assertEquals("de", plan.glossary().get(1).term.translationLanguage());
        assertTrue(plan.glossary().get(2).term.kept());
        assertEquals("_glossary/terms.csv", plan.glossary().get(2).source);
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("'comment'")), plan.warnings().toString());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("missing.csv")), plan.warnings().toString());
    }

    @Test
    void aCsvFileGivesTheFormsTheDefinitionAndTheStatusOfATerm() {
        Map<String, String> files = new TreeMap<>();
        files.put("terms.csv", "en,fr,Definition,Status,note\n"
                + "access token|access tokens,jeton d'accès| jetons d'accès |,A secret.,,\n"
                + "access token,jeton d'authentification,,Forbidden,another concept\n"
                + "API key,clé d'API,,obsolete,\n");
        ImportPlan.Options options = new ImportPlan.Options();
        options.setGlossary(List.of("terms.csv"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(2, plan.glossary().size(), plan.warnings().toString());
        GlossaryTerm token = plan.glossary().get(0).term;
        assertEquals("access token", token.term());
        assertEquals(List.of("access tokens"), token.termForms());
        assertEquals("jeton d'accès", token.translation());
        assertEquals(List.of("jetons d'accès"), token.translationForms());
        assertEquals("A secret.", token.definition());
        assertEquals(GlossaryTerm.Status.APPROVED, token.status());
        GlossaryTerm forbidden = plan.glossary().get(1).term;
        assertEquals(GlossaryTerm.Status.FORBIDDEN, forbidden.status());
        assertEquals("another concept", forbidden.note());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("'obsolete'")), plan.warnings().toString());
        assertTrue(plan.warnings().stream().noneMatch(w -> w.contains("column")), plan.warnings().toString());
    }

    @Test
    void theOptionsMapAVoicePersonasAndRules() {
        ImportPlan plan = ImportPlan.of(files(), options());
        assertEquals("brand-tone", plan.voice().name);
        assertEquals("Brand tone", plan.voice().title);
        assertEquals(2, plan.personas().size());
        assertEquals("cio-cto", plan.personas().get(0).name);
        assertTrue(plan.personas().get(0).isDefault);
        assertTrue(plan.personas().get(1).body.contains("growth"));
        assertEquals(3, plan.rules().size());
        assertEquals("seamless*", plan.rules().get(0).rule.pattern());
        assertEquals("game changer", plan.rules().get(1).rule.pattern());
        WritingRule replace = plan.rules().get(2).rule;
        assertEquals(WritingRule.Kind.REPLACE, replace.kind());
        assertEquals("fr", replace.language());
        assertEquals(WritingRule.Severity.BLOCKING, replace.severity());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("risk_levels")));
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
        options.setRules(List.of("_rules/claims.yml", "_rules/other.yml"));

        ImportPlan plan = ImportPlan.of(files, options);

        WritingRule seamless = plan.rules().get(0).rule;
        assertEquals(List.of("A seamless platform that empowers teams."), seamless.wrongExamples());
        assertEquals(List.of("A platform that helps teams."), seamless.rightExamples());
        assertEquals(seamless.wrongExamples(), plan.rules().get(1).rule.wrongExamples(), "empower* is broken too");
        WritingRule leading = plan.rules().get(2).rule;
        assertEquals(List.of("Jahia is the leading DXP."), leading.wrongExamples());
        assertEquals(List.of("Jahia is a composable DXP."), leading.rightExamples());
        assertTrue(plan.rules().get(3).rule.wrongExamples().isEmpty(), "An example never goes to a rule of another file");
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("\"Jahia guarantees GDPR compliance.\" breaks no rule")),
                plan.warnings().toString());
        assertFalse(plan.warnings().stream().anyMatch(w -> w.contains("rewrite_examples") || w.contains("counter_examples")),
                plan.warnings().toString());
    }

    @Test
    void aFullRuleCarriesItsExamplesItsOwnerAndItsDates() {
        String rules = "rules:\n"
                + "  - kind: forbidden\n    pattern: Acme\n    owner: Brand team\n    decided_on: 2026-09-05\n"
                + "    valid_from: '2026-09-05'\n    valid_until: 2026-12-31\n"
                + "    wrong_examples: [\"Better than Acme.\"]\n    right_examples: \"A fit for portal teams.\"\n"
                + "  - kind: forbidden\n    pattern: Globex\n    valid_until: 31/12/2026\n";
        ImportPlan.Options options = new ImportPlan.Options();
        options.setRules(List.of("_rules/moratorium.yml"));

        ImportPlan plan = ImportPlan.of(Map.of("_rules/moratorium.yml", rules), options);

        assertEquals(1, plan.rules().size());
        WritingRule rule = plan.rules().get(0).rule;
        assertEquals("Brand team", rule.owner());
        assertEquals(LocalDate.of(2026, 9, 5), rule.decidedOn());
        assertEquals(LocalDate.of(2026, 9, 5), rule.validFrom());
        assertEquals(LocalDate.of(2026, 12, 31), rule.validUntil());
        assertEquals(List.of("Better than Acme."), rule.wrongExamples());
        assertEquals(List.of("A fit for portal teams."), rule.rightExamples());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("not YYYY-MM-DD") && w.contains("Globex")),
                plan.warnings().toString());
    }

    @Test
    void aSkillKeepsItsSubAgentsAndItsDependenciesButNoScript() {
        ImportPlan plan = ImportPlan.of(files(), options());
        assertEquals(1, plan.skills().size());
        ImportPlan.SkillItem skill = plan.skills().get(0);
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
        assertTrue(plan.skills().isEmpty());
        assertTrue(plan.skipped().get(0).contains("invisible"));
    }

    @Test
    void aBadNameIsFixedWithAWarning() {
        SkillDocument document = SkillDocument.parse("---\nname: My Skill\ndescription: D.\n---\nBody", "my-skill");
        assertEquals("my-skill", document.name);
        assertFalse(document.warnings.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SkillDocument.parse("---\nname: x\n---\nBody", "x"));
    }

    @Test
    void aSlugIsLowerCaseAsciiWithADashBetweenWordsAndAtMost48Characters() {
        assertEquals("cio-cto", ImportPlan.slug("CIO / CTO"));
        assertEquals("elodie-dupont", ImportPlan.slug("  -Élodie Dupont-  "));
        assertEquals("a".repeat(48), ImportPlan.slug("a".repeat(50)));
        assertEquals("a".repeat(47), ImportPlan.slug("a".repeat(47) + " bcd"), "the cut leaves no dash at the end");
        assertEquals("persona", ImportPlan.slug("!!!"));
    }

    @Test
    void aDependencyIsCopiedOnceFromTheSkillOrTheArchive() {
        Map<String, String> files = files();
        files.put("skills/content/review-blog-article/SKILL.md", "---\nname: review-blog-article\ndescription: R.\n"
                + "depends_on: [_shared/, missing.md, notes.md, context/audience, ../evil.md, ref.md, tool.sh]\n---\nBody");
        files.put("skills/content/review-blog-article/notes.md", "Notes.");
        files.put("_shared/SKILL.md", "---\nname: shared\ndescription: S.\n---\nShared.");
        files.put("ref.md", "Ref.");
        files.put("tool.sh", "echo");
        ImportPlan.Options options = options();
        options.setSkillRoots(List.of("skills"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(1, plan.skills().size());
        assertEquals(List.of("_sub-agents/checker/SKILL.md", "notes.md", "deps/_shared/SKILL.md", "deps/ref.md"),
                List.copyOf(plan.skills().get(0).files.keySet()));
        assertEquals(List.of("skills/content/review-blog-article: the dependency missing.md is not in the archive"),
                plan.warnings().stream().filter(w -> w.contains("dependency")).toList());
        assertEquals(List.of("skills/content/review-blog-article/scripts/run.md: scripts and binary files are never imported"),
                plan.skipped());
    }

    @Test
    void theDependenciesStopAtTheirCharacterLimit() {
        Map<String, String> files = new TreeMap<>();
        files.put("skills/x/SKILL.md", "---\nname: x\ndescription: X.\ndepends_on: [big.md, small.md]\n---\nBody");
        files.put("big.md", "b".repeat(ImportPlan.MAX_DEPENDENCY_CHARS + 1));
        files.put("small.md", "Small.");

        ImportPlan plan = ImportPlan.of(files, new ImportPlan.Options());

        assertEquals(List.of(), List.copyOf(plan.skills().get(0).files.keySet()));
        assertEquals(List.of("skills/x: the dependencies pass 600000 characters, the rest is left out"), plan.warnings());
    }

    @Test
    void theSkillRootsKeepTheSkillsUnderThemAndTheFirstSkillOfANameWins() {
        Map<String, String> files = new TreeMap<>();
        files.put("skills/content/x/SKILL.md", "---\nname: x\ndescription: X.\n---\nFirst.");
        files.put("skills/content/x/sub/SKILL.md", "---\nname: sub\ndescription: Sub.\n---\nSub.");
        files.put("skills/other/x/SKILL.md", "---\nname: x\ndescription: X.\n---\nSecond.");
        files.put("tools/y/SKILL.md", "---\nname: y\ndescription: Y.\n---\nY.");
        ImportPlan.Options options = new ImportPlan.Options();
        options.setSkillRoots(List.of("skills/"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(1, plan.skills().size());
        assertEquals("skills/content/x", plan.skills().get(0).sourcePath);
        assertEquals(List.of("sub/SKILL.md"), List.copyOf(plan.skills().get(0).files.keySet()));
        assertEquals(List.of("skills/other/x: another skill of the archive has the name x"), plan.skipped());
    }

    @Test
    void aGlossaryWarnsOfAnUnknownColumnALongTermAndTheTermLimit() {
        StringBuilder csv = new StringBuilder("en,fr,note,note,status\n");
        csv.append("t".repeat(ImportPlan.MAX_TERM_CHARS + 1)).append(",long\n");
        for (int i = 0; i <= ImportPlan.MAX_TERMS; i++) {
            csv.append("term").append(i).append(",terme").append(i).append('\n');
        }
        Map<String, String> files = new TreeMap<>(Map.of("terms.csv", csv.toString(), "one.csv", "en,Note\nx,y\n",
                "empty.csv", "\n"));
        ImportPlan.Options options = new ImportPlan.Options();
        options.setGlossary(List.of("terms.csv", "one.csv", "empty.csv"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(ImportPlan.MAX_TERMS, plan.glossary().size());
        assertEquals("term4999", plan.glossary().get(ImportPlan.MAX_TERMS - 1).term.term());
        assertEquals(List.of(
                "terms.csv: the column 'note' is neither a language code nor note, definition or status, so it is left out",
                "terms.csv: the term '" + "t".repeat(40) + "' is longer than 200 characters, so it is left out",
                "terms.csv: more than 5000 glossary terms, the rest is left out",
                "one.csv: the first row needs two language codes at least, such as en,fr, so no glossary term",
                "empty.csv: an empty file, so no glossary term"), plan.warnings());
    }

    @Test
    void eachKeyOfARulesFileMapsToRulesOrToAWarning() {
        String rules = "forbidden_terms: none\n"
                + "sensitive_topics:\n  pricing_model:\n    avoid: [cheap, '']\n    prefer: [affordable, fair]\n"
                + "unsafe_patterns:\n  - regex: \"(unclosed\"\n  - regex: \"best\"\n    reason: \"No proof\"\n    risk: unsafe\n"
                + "rules:\n  - kind: glossary\n    pattern: API\n  - kind: unknown\n    pattern: Foo\n"
                + "tone: friendly\n";
        Map<String, String> files = new TreeMap<>(Map.of("rules.yml", rules, "list.yml", "- a\n", "bad.yml", "a: [b\n"));
        ImportPlan.Options options = new ImportPlan.Options();
        options.setRules(List.of("rules.yml", "missing.yml", "list.yml", "bad.yml"));

        ImportPlan plan = ImportPlan.of(files, options);

        assertEquals(2, plan.rules().size());
        WritingRule cheap = plan.rules().get(0).rule;
        assertEquals("cheap", cheap.pattern());
        assertEquals("Sensitive topic pricing model. Prefer: affordable, fair", cheap.justification());
        WritingRule best = plan.rules().get(1).rule;
        assertEquals("(?i)best", best.pattern());
        assertEquals(WritingRule.Severity.BLOCKING, best.severity());
        assertEquals("No proof (unsafe)", best.justification());
        assertEquals(List.of(
                "rules.yml: the pattern (unclosed does not compile in Java, so it is kept out",
                "rules.yml: a rule of kind glossary is kept out: a glossary comes from a CSV file (API)",
                "rules.yml: a rule with an unknown kind, match or severity is kept out (Foo)",
                "rules.yml: keys with no rule mapping, kept out: tone",
                "missing.yml: no such file in the archive, so no rule",
                "list.yml: not a YAML map, so no rule",
                "bad.yml: not valid YAML, so no rule"), plan.warnings());
    }

    @Test
    void theRulesOfAnImportStopAtTheirLimit() {
        StringBuilder rules = new StringBuilder("forbidden_terms:\n");
        for (int i = 0; i <= ImportPlan.MAX_RULES; i++) {
            rules.append("  - term").append(i).append('\n');
        }
        ImportPlan.Options options = new ImportPlan.Options();
        options.setRules(List.of("rules.yml"));

        ImportPlan plan = ImportPlan.of(Map.of("rules.yml", rules.toString()), options);

        assertEquals(ImportPlan.MAX_RULES, plan.rules().size());
        assertEquals(List.of("rules.yml: more than 2000 rules, the rest is left out"), plan.warnings());
    }

    @Test
    void thePersonasNeedTheirHeadingAndTheOptionsNameTheDefault() {
        ImportPlan.Options options = options();
        options.setDefaultPersona("cmo");

        ImportPlan plan = ImportPlan.of(files(), options);

        assertFalse(plan.personas().get(0).isDefault);
        assertTrue(plan.personas().get(1).isDefault);

        options.setPersonasHeading("Readers");
        ImportPlan none = ImportPlan.of(files(), options);

        assertTrue(none.personas().isEmpty());
        assertTrue(none.warnings().contains("context/audience: no section whose heading holds 'Readers', so no persona"),
                none.warnings().toString());
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
