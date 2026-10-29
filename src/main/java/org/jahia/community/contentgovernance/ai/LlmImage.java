package org.jahia.community.contentgovernance.ai;

/**
 * One picture sent with a prompt, for a provider that reads images.
 *
 * @param id        the number the prompt refers to it by ({@code IMAGE #id})
 * @param mediaType its MIME type, for example {@code image/jpeg}
 * @param base64    its bytes, base64-encoded, without a {@code data:} prefix
 */
public record LlmImage(int id, String mediaType, String base64) {
}
