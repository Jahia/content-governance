package org.jahia.community.contentgovernance.geo.botrank;

/**
 * What a run of the daily BotRank sync ({@link CitationSyncJob}) does before anything else. Kept out
 * of the job, which extends Jahia's {@code BackgroundJob}, so the rule can be tested alone.
 */
enum SyncStep {
    /** The daily sync was switched off in its settings: the trigger goes. */
    UNSCHEDULE,
    /**
     * AI citations, the GEO section or the module is off on the site: this run is skipped, the
     * trigger and the settings stay, and the first run after it is back on syncs again.
     */
    SKIP,
    /** Go on with the owner's check and the sync. */
    RUN;

    /**
     * @param syncEnabled the daily sync is on in the site's BotRank settings
     * @param citationsOn the site enables the module and keeps AI citations on
     * @return the first step of a run; a switched-off feature pauses the sync, it never turns it off
     */
    static SyncStep first(boolean syncEnabled, boolean citationsOn) {
        if (!syncEnabled) {
            return UNSCHEDULE;
        }
        return citationsOn ? RUN : SKIP;
    }
}
