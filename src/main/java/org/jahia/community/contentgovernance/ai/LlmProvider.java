package org.jahia.community.contentgovernance.ai;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * One chat-completion backend, shared by every feature of the module that calls a model: the page
 * review and its assists, and the GEO site report.
 *
 * Implementations are stateless and safe for concurrent use: everything that
 * varies per call, credentials included, arrives in {@link LlmSettings}. The
 * shape follows the provider layer of automatic-content-tags, so a reader of
 * one module recognises the other.
 */
public interface LlmProvider {

    /** The configuration value that selects this provider, lowercase. */
    String name();

    /** The API base used when the configuration names none. */
    String defaultBaseUrl();

    /** Whether {@link #complete(String, String, List, LlmSettings)} sends the images or ignores them. */
    default boolean supportsImages() {
        return false;
    }

    /**
     * Sends one system prompt and one user message and returns the answer.
     *
     * @throws IOException          on transport failure or a non-2xx status; the message carries the
     *                              status and a clipped response body, never the request
     * @throws InterruptedException when the calling thread is interrupted while waiting
     */
    default Completion complete(String system, String user, LlmSettings settings) throws IOException, InterruptedException {
        return complete(system, user, Collections.emptyList(), settings);
    }

    /**
     * Sends one system prompt and one user message followed by pictures, each introduced by an
     * {@code IMAGE #id:} line. A provider that does not read images sends the text alone.
     *
     * @throws IOException          on transport failure or a non-2xx status
     * @throws InterruptedException when the calling thread is interrupted while waiting
     */
    Completion complete(String system, String user, List<LlmImage> images, LlmSettings settings)
            throws IOException, InterruptedException;
}
