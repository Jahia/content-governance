package org.jahia.community.contentgovernance.ai;

/**
 * Whether a feature can call its model for the current user on a site, and which model answers.
 *
 * @param availability whether it can, or why not
 * @param provider     the provider of the model, empty when not ready
 * @param model        the model, empty when not ready
 * @param vision       whether the tier that reads pictures is served (alternative text)
 */
public record AiStatus(Availability availability, String provider, String model, boolean vision) {

    /** Whether a feature can call its model, or the first reason it cannot. */
    public enum Availability {
        /** The model answers. */
        READY,
        /** genai-connector is not installed or not started. */
        ABSENT,
        /** genai-connector serves no model on the tier of the feature. */
        NOT_CONFIGURED,
        /** The current user does not hold {@code genaiAccess} on the site. */
        NO_ACCESS
    }

    /** A status with no model, for every reason but {@link Availability#READY}. */
    public static AiStatus unavailable(Availability why) {
        return new AiStatus(why, "", "", false);
    }

    public boolean ready() {
        return availability == Availability.READY;
    }

    /** Why the feature is unavailable, as the drawer reads it: absent, notConfigured, noAccess; empty when ready. */
    public String reason() {
        switch (availability) {
            case ABSENT:
                return "absent";
            case NOT_CONFIGURED:
                return "notConfigured";
            case NO_ACCESS:
                return "noAccess";
            default:
                return "";
        }
    }
}
