/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.api;

/**
 * A message that a skill covers or does not cover, as the input guard of the assistant reads it.
 * The number of attached images and the note are optional: the note says why the message goes to
 * this skill, such as "a typo inside a text".
 */
public final class SkillExample {

    private final String message;
    private final int attachedImages;
    private final String note;

    public SkillExample(String message, int attachedImages, String note) {
        this.message = message;
        this.attachedImages = attachedImages;
        this.note = note;
    }

    public String message() {
        return message;
    }

    public int attachedImages() {
        return attachedImages;
    }

    /** Why the message goes to this skill, or {@code null}. */
    public String note() {
        return note;
    }

    @Override
    public String toString() {
        return "SkillExample[message=" + message + ", attachedImages=" + attachedImages + ", note=" + note + "]";
    }
}
