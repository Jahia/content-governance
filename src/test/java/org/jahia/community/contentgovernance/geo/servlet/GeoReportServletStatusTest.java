package org.jahia.community.contentgovernance.geo.servlet;

import org.jahia.community.contentgovernance.geo.spi.GeoReportWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the GEO dashboard reads to offer its tab Report, with a writer and without one. */
class GeoReportServletStatusTest {

    @Test
    void withNoWriterTheReportIsAbsent() {
        var status = GeoReportServlet.statusJson(GeoReportServlet.statusOf(null, "acme"));

        assertThat(status.getBoolean("enabled")).isFalse();
        assertThat(status.getString("reason")).isEqualTo("absent");
        assertThat(status.getString("model")).isEmpty();
    }

    @Test
    void aReadyWriterNamesItsProviderAndItsModel() {
        GeoReportWriter writer = mock(GeoReportWriter.class);
        when(writer.status("acme")).thenReturn(new GeoReportWriter.Status(true, "", "mistral", "mistral-medium"));

        var status = GeoReportServlet.statusJson(GeoReportServlet.statusOf(writer, "acme"));

        assertThat(status.getBoolean("enabled")).isTrue();
        assertThat(status.getString("reason")).isEmpty();
        assertThat(status.getString("model")).isEqualTo("mistral-medium");
    }

    @Test
    void aWriterThatRefusesTheUserNamesNoModel() {
        GeoReportWriter writer = mock(GeoReportWriter.class);
        when(writer.status("acme")).thenReturn(new GeoReportWriter.Status(false, "noAccess", "mistral", "large"));

        var status = GeoReportServlet.statusJson(GeoReportServlet.statusOf(writer, "acme"));

        assertThat(status.getBoolean("enabled")).isFalse();
        assertThat(status.getString("reason")).isEqualTo("noAccess");
        assertThat(status.getString("provider")).isEmpty();
        assertThat(status.getString("model")).isEmpty();
    }
}
