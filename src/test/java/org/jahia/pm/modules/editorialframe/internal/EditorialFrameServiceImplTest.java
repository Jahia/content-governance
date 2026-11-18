/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal;

import org.jahia.pm.modules.editorialframe.api.Frame;
import org.jahia.pm.modules.editorialframe.api.FrameText;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
