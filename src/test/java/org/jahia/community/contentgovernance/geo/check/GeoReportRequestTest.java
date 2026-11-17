package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.ai.AiAnswer;
import org.jahia.community.contentgovernance.ai.AiFeature;
import org.jahia.community.contentgovernance.ai.AiRequest;
import org.jahia.community.contentgovernance.ai.AiService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.jcr.RepositoryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoReportRequestTest {

    private static final String ANSWER = "{\"summary\":\"Readable.\",\"verdict\":\"partial\",\"compliance\":[],"
            + "\"priorities\":[],\"quickWins\":[],\"roadmap\":{}}";

    @Test
    void theReportAsksForTheGeoReportOfItsOwnSite() throws Exception {
        AiService ai = mock(AiService.class);
        when(ai.complete(any())).thenReturn(new AiAnswer(ANSWER, true, 900, 300, 0.0042, "mistral", "mistral-small"));
        try (MockedStatic<ScanStore> store = mockStatic(ScanStore.class);
             MockedStatic<Languages> languages = mockStatic(Languages.class);
             MockedStatic<StructuredData> schema = mockStatic(StructuredData.class)) {
            store.when(() -> ScanStore.read("/sites/acme", "en")).thenReturn(new JSONObject());
            languages.when(() -> Languages.check("/sites/acme")).thenThrow(new RepositoryException("not here"));
            schema.when(() -> StructuredData.coverage(any(), any(), any(), any()))
                    .thenThrow(new RepositoryException("not here"));

            JSONObject report = GeoReport.generate("/sites/acme", "en", "fr", "https://acme.example", ai);

            ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
            verify(ai).complete(sent.capture());
            assertThat(sent.getValue().feature()).isEqualTo(AiFeature.GEO_REPORT);
            assertThat(sent.getValue().siteKey()).isEqualTo("acme");
            assertThat(sent.getValue().images()).isEmpty();
            assertThat(sent.getValue().maxOutputTokens()).isEqualTo(6000);
            assertThat(sent.getValue().input()).contains("SITE: /sites/acme", "REPORT LANGUAGE: French");
            assertThat(report.getString("provider")).isEqualTo("mistral");
            assertThat(report.getString("model")).isEqualTo("mistral-small");
            assertThat(report.getBoolean("truncated")).isTrue();
            assertThat(report.getJSONObject("usage").getDouble("cost")).isEqualTo(0.0042);
            store.verify(() -> ScanStore.saveReport(eq("/sites/acme"), eq("en"), any()));
        }
    }
}
