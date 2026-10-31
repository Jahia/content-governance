package org.jahia.community.contentgovernance.audit;

import org.jahia.community.contentgovernance.audit.model.AuditSummary;
import org.jahia.community.contentgovernance.audit.service.internal.AuditResults;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditResultsTest {

    private static final String RESULTS = "{"
            + "\"page\": {\"title\": \"Home\"},"
            + "\"a11y\": {\"violations\": [{\"id\": \"image-alt\", \"impact\": \"critical\"}, {\"id\": \"link-name\", \"impact\": \"minor\"}], \"passes\": 41},"
            + "\"seo\": {\"recommendations\": [{\"key\": \"shortTitle\"}]},"
            + "\"readability\": {\"recommendations\": []},"
            + "\"incomplete\": {\"links\": {\"incomplete\": \"timedOut\"}},"
            + "\"script\": \"<script>alert(1)</script>\""
            + "}";

    @Test
    void keepsTheTabResultsAndThePageFactsOnly() {
        JSONObject kept = AuditResults.parse(RESULTS);
        assertThat(kept.keySet()).containsExactlyInAnyOrder("page", "a11y", "seo", "readability");
        assertThat(kept.getJSONObject("a11y").getInt("passes")).isEqualTo(41);
    }

    @Test
    void refusesWhatIsNotAnAuditOrTooLarge() {
        assertThatThrownBy(() -> AuditResults.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditResults.parse("[1, 2]")).hasMessage("The results are not a JSON object");
        assertThatThrownBy(() -> AuditResults.parse("{\"page\": {}}")).hasMessage("The results hold no tab");
        assertThatThrownBy(() -> AuditResults.parse("{\"seo\": \"" + "x".repeat(AuditResults.MAX_CHARS) + "\"}"))
                .hasMessage("The results are too large");
    }

    @Test
    void countsViolationsForAccessibilityAndRecommendationsElsewhere() {
        AuditSummary summary = AuditSummary.of(AuditResults.parse(RESULTS));
        assertThat(summary.counts()).containsExactly(
                java.util.Map.entry("accessibility", 2), java.util.Map.entry("seo", 1), java.util.Map.entry("readability", 0));
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.seriousAccessibility()).isEqualTo(1);
    }

    @Test
    void readsBackWhatItStores() {
        AuditSummary summary = AuditSummary.of(AuditResults.parse(RESULTS));
        assertThat(AuditSummary.fromJson(summary.toJson())).isEqualTo(summary);
        assertThat(AuditSummary.fromJson(null).total()).isZero();
        assertThat(AuditSummary.fromJson("{\"counts\": {}, \"total\": 2}").seriousAccessibility()).isEqualTo(-1);
        assertThat(AuditSummary.fromJson("{\"counts\": {\"seo\": 4, \"unknown\": 9}, \"total\": 4}").counts())
                .containsOnlyKeys("seo");
    }
}
