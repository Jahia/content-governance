/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;


import java.util.List;


/**
 * A skill as a catalogue lists it. The scope is site, group (systemsite), module or generated. The
 * activation is auto, manual or pinned-only. The action is the action of the assistant that runs
 * the skill. The overrides are the scopes of the skills of the same name that this one hides.
 *
 * <p>The label is the name that a user interface shows. The tools are the Jahia tools that the
 * skill runs with, such as {@code content.get}: empty when the skill runs with the tools of its
 * action. The coverage is what the input guard of the assistant reads.</p>
 *
 * @param label the name that a user interface shows: the name of the skill when it states no label
 * @param tools the Jahia tools of the skill, from {@code allowed-tools}. Empty: the tools of its action
 * @param coverage the work that the skill covers, for the input guard of the assistant. Never null:
 *                 the description of the skill, with no example, when none is given
 */
public record SkillSummary(String name, String description, String scope, String origin, String activation,
                           List<String> consumers, String action, String source, List<String> overrides, String label,
                           List<String> tools, SkillCoverage coverage) {

    public SkillSummary {
        consumers = consumers == null ? List.of() : List.copyOf(consumers);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
        label = label == null || label.isBlank() ? name : label;
        tools = tools == null ? List.of() : List.copyOf(tools);
        coverage = coverage == null ? new SkillCoverage(description, List.of(), List.of()) : coverage;
    }

    public SkillSummary withOverrides(List<String> hidden) {
        return new SkillSummary(name, description, scope, origin, activation, consumers, action, source, hidden, label, tools, coverage);
    }
}
