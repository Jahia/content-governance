package org.jahia.community.contentgovernance.geo.servlet;

import org.jahia.community.contentgovernance.geo.spi.GeoReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the GEO dashboard reads to offer its tab Report, and how generate refuses, with a writer and without one. */
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

    @Test
    void generateRefusesAsNotConfiguredWithNoWriter() {
        assertThat(GeoReportServlet.refusalOf(GeoReportServlet.statusOf(null, "acme")))
                .isEqualTo(GeoReportWriter.Failure.Reason.NOT_CONFIGURED);
    }

    @Test
    void generateRefusesAUserTheWriterRefuses() {
        assertThat(GeoReportServlet.refusalOf(GeoReportWriter.Status.refused(GeoReportWriter.Status.NO_ACCESS)))
                .isEqualTo(GeoReportWriter.Failure.Reason.NO_ACCESS);
        assertThat(GeoReportServlet.refusalOf(GeoReportWriter.Status.refused(GeoReportWriter.Status.NOT_CONFIGURED)))
                .isEqualTo(GeoReportWriter.Failure.Reason.NOT_CONFIGURED);
        assertThat(GeoReportServlet.refusalOf(new GeoReportWriter.Status(true, "", "mistral", "medium"))).isNull();
    }

    @ParameterizedTest
    @CsvSource({"NO_ACCESS, 403", "QUOTA, 429", "NOT_CONFIGURED, 409", "OUTPUT_LIMIT, 502", "PROVIDER, 502"})
    void eachFailureOfTheWriterHasItsStatus(GeoReportWriter.Failure.Reason reason, int status) {
        assertThat(GeoReportServlet.httpStatusOf(reason)).isEqualTo(status);
    }
}
