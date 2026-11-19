/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

/**
 * A message that a skill covers or does not cover, as the input guard of the assistant reads it.
 * The number of attached images and the note are optional: the note says why the message goes to
 * this skill, such as "a typo inside a text".
 *
 * @param note why the message goes to this skill, or {@code null}
 */
public record SkillExample(String message, int attachedImages, String note) {
}
