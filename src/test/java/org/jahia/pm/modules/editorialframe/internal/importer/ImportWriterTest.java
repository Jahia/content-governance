/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.pm.modules.editorialframe.api.SkillCoverage;
import org.jahia.pm.modules.editorialframe.internal.skills.JahiaSkillKeys;
import org.jahia.pm.modules.editorialframe.internal.skills.JcrSkills;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportWriterTest {

    @Test
    void anImportedSkillKeepsItsCoverageOnItsNode() {
        SkillDocument document = SkillDocument.parse("---\nname: teaser\ndescription: Refresh a teaser.\n"
                + "metadata:\n  jahia-activation: auto\n  jahia-scope: refresh the teaser of a news item.\n"
                + "  jahia-in-scope:\n    - message: Refresh the teaser of this news.\n      note: a teaser\n"
                + "  jahia-out-of-scope: [Write a new news item.]\n---\nBody", "teaser");

        List<String> values = ImportWriter.metadataValues(document, new ObjectMapper());
        List<String> warnings = new ArrayList<>();
        SkillCoverage coverage = JahiaSkillKeys.coverage(JcrSkills.metadata(values), document.description, warnings);

        assertTrue(values.contains("jahia-activation=auto"), values.toString());
        assertEquals("refresh the teaser of a news item.", coverage.sentence());
        assertEquals("a teaser", coverage.inScope().get(0).note());
        assertEquals("Write a new news item.", coverage.outOfScope().get(0).message());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
