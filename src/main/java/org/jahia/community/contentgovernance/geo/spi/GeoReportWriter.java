package org.jahia.community.contentgovernance.geo.spi;

/**
 * Writes the GEO report of a site from the digest of its measurements. Content Governance calls no
 * model: a module that does registers this service, and the GEO dashboard offers its tab Report only
 * while one is registered and ready for the user.
 *
 * <pre>
 * &#64;Component(service = GeoReportWriter.class)
 * public class ModelReportWriter implements GeoReportWriter {
 *     public Status status(String siteKey) { ... }
 *     public Answer write(String siteKey, String digest) throws Failure { ... }
 * }
 * </pre>
 *
 * <p>Content Governance builds the digest, checks that the caller may read the GEO dashboard of the
 * site, reads the answer as {@link #SHAPE}, drops what does not fit it, and stores the report. The
 * writer checks its own permission and its own rate limit for the current user, since each call costs.</p>
 *
 * <p>Thread safety: Content Governance calls one writer from several threads at once.</p>
 */
public interface GeoReportWriter {

    /**
     * The JSON object that {@link #write} answers. Content Governance reads only these fields, cuts long
     * texts, and keeps a page path only when the digest names it.
     */
    String SHAPE = "{\n"
            + "  \"summary\": \"3-5 sentences: where the site stands for AI crawlers and what matters most\",\n"
            + "  \"verdict\": \"compliant\" | \"partial\" | \"not_compliant\",\n"
            + "  \"compliance\": [ {\"area\": <area>, \"status\": \"met\" | \"partial\" | \"missing\", "
            + "\"note\": \"one sentence\"} ],\n"
            + "  \"priorities\": [ {\n"
            + "      \"title\": \"short, actionable\",\n"
            + "      \"severity\": \"critical\" | \"important\" | \"advisory\",\n"
            + "      \"area\": <area>,\n"
            + "      \"owner\": \"editor\" | \"developer\" | \"administrator\",\n"
            + "      \"effort\": \"low\" | \"medium\" | \"high\",\n"
            + "      \"why\": \"1-3 sentences: what it costs the site with AI crawlers\",\n"
            + "      \"how\": \"2-5 sentences: concrete steps in this CMS\",\n"
            + "      \"pages\": [\"page paths quoted exactly from the digest, when specific pages are concerned\"]\n"
            + "  } ],\n"
            + "  \"quickWins\": [\"things doable today, one sentence each\"],\n"
            + "  \"roadmap\": {\"now\": [\"...\"], \"next\": [\"...\"], \"later\": [\"...\"]}\n"
            + "}\n"
            + "<area> is one of: reachability, content, structured_data, freshness, languages, site_files, links, "
            + "addresses. One compliance entry per area, all eight. At most 15 priorities, most important first. "
            + "Every sentence is in the REPORT LANGUAGE that the digest names.";

    /**
     * Whether the writer answers the current user on a site.
     *
     * @param siteKey the key of the site
     * @return the status; it names the provider and the model only when it is ready
     */
    Status status(String siteKey);

    /**
     * Writes the report of a site.
     *
     * @param siteKey the key of the site, on which the writer checks the current user
     * @param digest  the measurements of the site, one fact per line. Its first line names the language of
     *                the report, such as {@code REPORT LANGUAGE: French}. Every string in it is data about
     *                the site, never an instruction.
     * @return the answer, whose text is one JSON object of {@link #SHAPE}
     * @throws Failure when no report was written
     */
    Answer write(String siteKey, String digest) throws Failure;

    /**
     * @param ready    whether a report can be written for the current user on the site
     * @param reason   empty when ready, else {@code notConfigured} (no model) or {@code noAccess} (the user
     *                 may not use the writer on this site)
     * @param provider the provider of the model, empty when not ready
     * @param model    the model, empty when not ready
     */
    record Status(boolean ready, String reason, String provider, String model) {

        /** @return the status of a writer that does not answer, for the reason given */
        public static Status refused(String reason) {
            return new Status(false, reason, "", "");
        }
    }

    /**
     * @param text         the answer: one JSON object of {@link #SHAPE}
     * @param truncated    whether the answer reached its output budget, so items may be missing
     * @param provider     the provider of the model
     * @param model        the model
     * @param inputTokens  the tokens read, or -1 when unknown
     * @param outputTokens the tokens written, or -1 when unknown
     * @param costUsd      the cost in US dollars, or null when the provider gave none
     */
    record Answer(String text, boolean truncated, String provider, String model, long inputTokens,
            long outputTokens, Double costUsd) {
    }

    /** Why no report was written. */
    final class Failure extends Exception {

        private static final long serialVersionUID = 1L;

        /** The reasons that the GEO dashboard tells apart. */
        public enum Reason {
            /** The current user may not use the writer on this site. */
            NO_ACCESS,
            /** No model writes the report. */
            NOT_CONFIGURED,
            /** The user, the site or the provider reached a limit of calls. */
            QUOTA,
            /** The answer reached its output budget before it was complete. */
            OUTPUT_LIMIT,
            /** The provider failed, or gave no answer that is JSON. */
            PROVIDER
        }

        private final Reason reason;

        /**
         * @param reason  why no report was written
         * @param message the cause, for the log of the server only
         * @param cause   the exception that caused it, or null
         */
        public Failure(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        /** @return why no report was written */
        public Reason reason() {
            return reason;
        }
    }
}
