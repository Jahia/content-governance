package org.jahia.community.contentgovernance.geo.botrank;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A BotRank connection from a file under the name that GEO Readiness used,
 * {@code karaf/etc/org.jahia.se.modules.georeadiness.botrank-<id>.cfg}.
 *
 * <p>It works as a {@link BotRankConnection} of the current name, so a server that replaced GEO
 * Readiness keeps its connections, and each activation asks in the log to rename the file to
 * {@code org.jahia.community.contentgovernance.geo.botrank-<id>.cfg}. A file under each name with
 * the same id makes both unusable, as any two files with one id do ({@link BotRankConnections}).</p>
 *
 * <p>Thread safety: as {@link BotRankConnection}.</p>
 */
@Component(
        service = BotRankConnection.class,
        configurationPid = BotRankConnection.LEGACY_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                "service.description=GEO readiness - one BotRank organisation, from a file of the legacy name",
                "service.vendor=Jahia Solutions Group SA"
        })
public class LegacyBotRankConnection extends BotRankConnection {

    private static final Logger logger = LoggerFactory.getLogger(LegacyBotRankConnection.class);

    /**
     * Reads the file as a connection of the current name, then asks to rename it.
     *
     * @param config the configuration of the file, as Felix FileInstall reads it
     */
    @Activate
    @Modified
    @Override
    protected void apply(Map<String, Object> config) {
        super.apply(config);
        String id = id();
        logger.warn("BotRank connection {} comes from a file named {}-{}.cfg, the name of GEO Readiness: "
                + "rename it to {}-{}.cfg", id, LEGACY_PID, id, PID, id);
    }
}
