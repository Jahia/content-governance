package org.jahia.test.contentgovernance;

import org.jahia.community.contentgovernance.geo.spi.GeoReportWriter;
import org.osgi.service.component.annotations.Component;

/**
 * A writer of the GEO report that another module registers, as ai-assistant does, with no model. It
 * answers a fixed report on the sites whose key starts with {@value #SITE_PREFIX}, and refuses the
 * others as not configured, so that the other specs see no report.
 */
@Component(service = GeoReportWriter.class)
public class StubReportWriter implements GeoReportWriter {

    static final String SITE_PREFIX = "cg-geo-report";
    static final String MODEL = "stub-model";

    @Override
    public Status status(String siteKey) {
        return siteKey.startsWith(SITE_PREFIX) ? new Status(true, "", "stub", MODEL) : Status.refused("notConfigured");
    }

    @Override
    public Answer write(String siteKey, String digest) throws Failure {
        if (!siteKey.startsWith(SITE_PREFIX)) {
            throw new Failure(Failure.Reason.NOT_CONFIGURED, "The stub writes no report on " + siteKey, null);
        }
        String language = digest.lines().findFirst().orElse("").replace("REPORT LANGUAGE: ", "");
        String json = "{\"summary\": \"Stub report of " + siteKey + " in " + language + ".\", \"verdict\": \"partial\","
                + " \"compliance\": [{\"area\": \"links\", \"status\": \"met\", \"note\": \"Fine.\"}],"
                + " \"priorities\": [], \"quickWins\": [\"Publish the sitemap.\"],"
                + " \"roadmap\": {\"now\": [], \"next\": [], \"later\": []}}";
        return new Answer(json, false, "stub", MODEL, 10, 20, null);
    }
}
