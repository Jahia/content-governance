package org.jahia.community.contentgovernance.geo.config;

import org.jahia.community.contentgovernance.geo.util.FetchGuard;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OSGi-managed configuration of the GEO readiness section: the crawler check and its fetches.
 * The model behind the written report is the module's shared one ({@code LlmConfigService}).
 *
 * Edit digital-factory-data/karaf/etc/org.jahia.community.contentgovernance.geo.cfg at
 * runtime. Changes are picked up immediately through @Modified, with no
 * redeployment.
 */
@Component(
        service = GeoReadinessConfigService.class,
        configurationPid = "org.jahia.community.contentgovernance.geo",
        property = {
                "service.description=Content Governance - GEO readiness configuration",
                "service.vendor=Jahia Solutions Group SA"
        },
        immediate = true)
public class GeoReadinessConfigService {

    /**
     * The whole configuration is swapped at once, never edited in place, so a
     * reader either sees the settings as they were or as they now are and never
     * a mixture of the two. Snapshot is immutable, which is what makes that
     * safe; the reference is held here rather than marked volatile so that the
     * swap is visibly the atomic operation it has to be.
     */
    private final AtomicReference<Snapshot> config = new AtomicReference<>(Snapshot.defaults());

    @Activate
    @Modified
    protected void activate(Map<String, Object> properties) {
        Snapshot snapshot = Snapshot.from(properties);
        this.config.set(snapshot);
        // The fetchers are static utilities, reached from a servlet thread and
        // from the scheduler alike, and they have to know which single address
        // an operator has explicitly pointed this module at: FetchGuard refuses
        // private addresses, and PUBLIC_BASE_URL is documented for exactly the
        // deployment where the site only answers on one.
        FetchGuard.trustBase(snapshot.publicBaseUrl);
    }

    public int getFetchTimeoutMs() {
        return config.get().fetchTimeoutMs;
    }

    public int getMaxBodyBytes() {
        return config.get().maxBodyBytes;
    }

    public int getRateMaxCalls() {
        return config.get().rateMaxCalls;
    }

    public long getRateWindowMs() {
        return config.get().rateWindowMs;
    }

    /** Blank means "use the built-in agent list", which is the normal case. */
    public String getCrawlerAgents() {
        return config.get().crawlerAgents;
    }

    /** Blank means "derive the base URL from the site's server name". */
    public String getPublicBaseUrl() {
        return config.get().publicBaseUrl;
    }


    // Immutable snapshot. One volatile read gives a fully consistent view.
    private static final class Snapshot {

        final int fetchTimeoutMs;
        final int maxBodyBytes;
        final int rateMaxCalls;
        final long rateWindowMs;
        final String crawlerAgents;
        final String publicBaseUrl;

        /**
         * Every field reads its own key, with the value it takes when the key is
         * absent. That is what makes {@link #defaults()} an empty map rather than
         * a second list of the same six numbers, kept in step by hand.
         */
        private Snapshot(Map<String, Object> p) {
            this.fetchTimeoutMs = intVal(p, "FETCH_TIMEOUT_MS", 8000);
            this.maxBodyBytes = intVal(p, "MAX_BODY_BYTES", 1_500_000);
            this.rateMaxCalls = intVal(p, "RATE_MAX_CALLS", 20);
            this.rateWindowMs = longVal(p, "RATE_WINDOW_MS", 600_000L);
            this.crawlerAgents = str(p, "CRAWLER_AGENTS", "");
            this.publicBaseUrl = str(p, "PUBLIC_BASE_URL", "");
        }

        static Snapshot defaults() {
            return new Snapshot(Collections.emptyMap());
        }

        static Snapshot from(Map<String, Object> p) {
            return new Snapshot(p);
        }

        private static String str(Map<String, Object> m, String key, String def) {
            Object v = m.get(key);
            if (v instanceof String) {
                String s = ((String) v).trim();
                return s.isEmpty() ? def : s;
            }
            return def;
        }

        private static int intVal(Map<String, Object> m, String key, int def) {
            try {
                return Integer.parseInt(String.valueOf(m.get(key)).trim());
            } catch (Exception e) {
                return def;
            }
        }

        private static long longVal(Map<String, Object> m, String key, long def) {
            try {
                return Long.parseLong(String.valueOf(m.get(key)).trim());
            } catch (Exception e) {
                return def;
            }
        }
    }
}
