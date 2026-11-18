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
 */
public final class SkillSummary {

    private final String name;
    private final String description;
    private final String scope;
    private final String origin;
    private final String activation;
    private final List<String> consumers;
    private final String action;
    private final String source;
    private final List<String> overrides;
    private final String label;
    private final List<String> tools;
    private final SkillCoverage coverage;

    public SkillSummary(String name, String description, String scope, String origin, String activation, List<String> consumers, String action, String source, List<String> overrides) {
        this(name, description, scope, origin, activation, consumers, action, source, overrides, null, null, null);
    }

    public SkillSummary(String name, String description, String scope, String origin, String activation, List<String> consumers, String action, String source, List<String> overrides,
                        String label, List<String> tools, SkillCoverage coverage) {
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.origin = origin;
        this.activation = activation;
        this.consumers = consumers == null ? List.of() : List.copyOf(consumers);
        this.action = action;
        this.source = source;
        this.overrides = overrides == null ? List.of() : List.copyOf(overrides);
        this.label = label == null || label.isBlank() ? name : label;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.coverage = coverage == null ? new SkillCoverage(description, List.of(), List.of()) : coverage;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String scope() {
        return scope;
    }

    public String origin() {
        return origin;
    }

    public String activation() {
        return activation;
    }

    public List<String> consumers() {
        return consumers;
    }

    public String action() {
        return action;
    }

    public String source() {
        return source;
    }

    public List<String> overrides() {
        return overrides;
    }

    /** The name that a user interface shows: the name of the skill when it states no label. */
    public String label() {
        return label;
    }

    /** The Jahia tools of the skill, from {@code allowed-tools}. Empty: the tools of its action. */
    public List<String> tools() {
        return tools;
    }

    /** The work that the skill covers, for the input guard of the assistant. Never null. */
    public SkillCoverage coverage() {
        return coverage;
    }

    public SkillSummary withOverrides(List<String> hidden) {
        return new SkillSummary(name, description, scope, origin, activation, consumers, action, source, hidden, label, tools, coverage);
    }

    @Override
    public String toString() {
        return "SkillSummary[" + "name=" + name + ", " + "description=" + description + ", " + "scope=" + scope + ", " + "origin=" + origin + ", " + "activation=" + activation + ", " + "consumers=" + consumers + ", " + "action=" + action + ", " + "source=" + source + ", " + "overrides=" + overrides + ", " + "label=" + label + ", " + "tools=" + tools + ", " + "coverage=" + coverage + "]";
    }
}
