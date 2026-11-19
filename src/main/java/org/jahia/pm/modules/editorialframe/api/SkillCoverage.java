/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

import java.util.List;

/**
 * The work that a skill covers, as the input guard of the assistant reads it: one sentence, the
 * messages that the skill covers, and messages that look close but that it does not cover.
 *
 * <p>For example, a skill of forms covers "create or change a form of the site.", with the message
 * "Add a contact form to the Contact page." in scope and "Write me a survey about pizza." out of
 * scope. A skill that states no sentence of its own takes its description.</p>
 *
 * @param sentence one sentence on the work that the skill covers
 * @param inScope messages that the skill covers
 * @param outOfScope messages that look close to the skill but that it does not cover
 */
public record SkillCoverage(String sentence, List<SkillExample> inScope, List<SkillExample> outOfScope) {

    public SkillCoverage {
        inScope = inScope == null ? List.of() : List.copyOf(inScope);
        outOfScope = outOfScope == null ? List.of() : List.copyOf(outOfScope);
    }
}
