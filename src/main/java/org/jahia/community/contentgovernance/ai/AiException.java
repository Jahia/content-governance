package org.jahia.community.contentgovernance.ai;

/**
 * A call that gave no answer. The message is for the server log: it can quote the provider, and the
 * endpoints answer the browser from {@link #reason()} alone.
 */
public class AiException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Why the call gave no answer. */
    public enum Reason {
        /** The current user does not hold {@code genaiAccess} on the site. Nothing was sent. */
        NO_ACCESS,
        /** The rate limit of genai-connector refused the call. Nothing was sent. */
        QUOTA,
        /** genai-connector is absent, or serves no model on the tier. Nothing was sent. */
        NOT_CONFIGURED,
        /** The answer reached its output budget before it was one complete JSON object. */
        OUTPUT_LIMIT,
        /** The answer was not one JSON object, after the retries of genai-connector. */
        INVALID_OUTPUT,
        /** The provider failed, or genai-connector refused the request (a picture it cannot read). */
        PROVIDER
    }

    private final Reason reason;

    public AiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
