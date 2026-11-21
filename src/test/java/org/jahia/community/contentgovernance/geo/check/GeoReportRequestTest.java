package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.geo.spi.GeoReportWriter;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoReportRequestTest {

    private static final String ANSWER = "{\"summary\":\"Readable.\",\"verdict\":\"partial\",\"compliance\":[],"
            + "\"priorities\":[],\"quickWins\":[],\"roadmap\":{}}";

    @Test
    void theReportAsksTheWriterForItsOwnSiteAndStoresTheAnswer() throws Exception {
        GeoReportWriter writer = mock(GeoReportWriter.class);
        when(writer.write(anyString(), anyString()))
                .thenReturn(new GeoReportWriter.Answer(ANSWER, true, "mistral", "mistral-small", 900, 300, 0.0042));
        try (MockedStatic<ScanStore> store = mockStatic(ScanStore.class);
             MockedStatic<Languages> languages = mockStatic(Languages.class);
             MockedStatic<StructuredData> schema = mockStatic(StructuredData.class)) {
            stubMeasurements(store, languages, schema);

            JSONObject report = GeoReport.generate("/sites/acme", "en", "fr", "https://acme.example", writer);

            ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
            verify(writer).write(eq("acme"), digest.capture());
            assertThat(digest.getValue()).startsWith("REPORT LANGUAGE: French").contains("SITE: /sites/acme");
            assertThat(report.getString("summary")).isEqualTo("Readable.");
            assertThat(report.getString("provider")).isEqualTo("mistral");
            assertThat(report.getString("model")).isEqualTo("mistral-small");
            assertThat(report.getBoolean("truncated")).isTrue();
            assertThat(report.getJSONObject("usage").getDouble("cost")).isEqualTo(0.0042);
            store.verify(() -> ScanStore.saveReport(eq("/sites/acme"), eq("en"), any()));
        }
    }

    @Test
    void aWriterThatWritesNoReportStoresNothing() throws Exception {
        GeoReportWriter writer = mock(GeoReportWriter.class);
        when(writer.write(anyString(), anyString())).thenThrow(
                new GeoReportWriter.Failure(GeoReportWriter.Failure.Reason.QUOTA, "too many calls", null));
        try (MockedStatic<ScanStore> store = mockStatic(ScanStore.class);
             MockedStatic<Languages> languages = mockStatic(Languages.class);
             MockedStatic<StructuredData> schema = mockStatic(StructuredData.class)) {
            stubMeasurements(store, languages, schema);

            assertThatThrownBy(() -> GeoReport.generate("/sites/acme", "en", "en", "https://acme.example", writer))
                    .isInstanceOf(GeoReportWriter.Failure.class);
            store.verify(() -> ScanStore.saveReport(any(), any(), any()), never());
        }
    }

    private static void stubMeasurements(MockedStatic<ScanStore> store, MockedStatic<Languages> languages,
            MockedStatic<StructuredData> schema) {
        store.when(() -> ScanStore.read("/sites/acme", "en")).thenReturn(new JSONObject());
        languages.when(() -> Languages.check("/sites/acme")).thenThrow(new RepositoryException("not here"));
        schema.when(() -> StructuredData.coverage(any(), any(), any(), any()))
                .thenThrow(new RepositoryException("not here"));
    }
}
