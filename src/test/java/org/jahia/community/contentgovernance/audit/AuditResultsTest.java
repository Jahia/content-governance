package org.jahia.community.contentgovernance.audit;

import java.util.Collections;
import java.util.Map;
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
                Map.entry("accessibility", 2), Map.entry("seo", 1), Map.entry("readability", 0));
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.seriousAccessibility()).isEqualTo(1);
    }

    @Test
    void readsBackWhatItStores() {
        AuditSummary summary = AuditSummary.of(AuditResults.parse(RESULTS));
        assertThat(AuditSummary.fromJson(summary.toJson())).isEqualTo(summary);
        assertThat(AuditSummary.fromJson(null).total()).isZero();
        assertThat(AuditSummary.fromJson("{\"counts\": {}, \"total\": 2}").seriousAccessibility()).isEqualTo(-1);
        assertThat(AuditSummary.fromJson("{\"counts\": {\"zTab\": 2, \"seo\": 4, \"aTab\": 9}, \"total\": 15}").counts())
                .containsExactly(Map.entry("seo", 4), Map.entry("aTab", 9), Map.entry("zTab", 2));
    }

    @Test
    void countsTheFindingsOfAnotherTabUnderItsId() {
        JSONObject stored = AuditResults.parseFindings("[{\"key\": \"t.one\", \"severity\": \"serious\", \"title\": \"One\"},"
                + "{\"key\": \"t.two\", \"severity\": \"minor\", \"title\": \"Two\"}]");
        AuditSummary summary = AuditSummary.ofFindings("myTab", stored.getJSONArray("findings"));
        assertThat(summary.counts()).containsExactly(Map.entry("myTab", 2));
        assertThat(summary.total()).isEqualTo(2);
        assertThat(AuditSummary.fromJson(summary.toJson())).isEqualTo(summary);
    }

    @Test
    void keepsTheFieldsOfAFindingOnly() {
        JSONObject finding = AuditResults.parseFindings("[{\"key\": \"t.one\", \"rule\": \"r1\", \"severity\": \"urgent\","
                + " \"title\": \"One\", \"detail\": \"Do this\", \"count\": 3, \"html\": \"<b>x</b>\", \"tab\": {\"a\": 1}}]")
                .getJSONArray("findings").getJSONObject(0);
        assertThat(finding.keySet()).containsExactlyInAnyOrder("key", "rule", "severity", "title", "detail", "count");
        assertThat(finding.getString("severity")).isEqualTo("minor");
        assertThat(AuditResults.parseFindings("[{\"key\": \"k\", \"title\": \"" + "x".repeat(5000) + "\"}]")
                .getJSONArray("findings").getJSONObject(0).getString("title")).hasSize(AuditResults.MAX_FIELD_CHARS);
    }

    @Test
    void refusesWhatIsNotAListOfFindings() {
        assertThatThrownBy(() -> AuditResults.parseFindings(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditResults.parseFindings("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditResults.parseFindings("[1]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditResults.parseFindings("[{\"title\": \"no key\"}]")).isInstanceOf(IllegalArgumentException.class);
        String many = "[" + String.join(",", Collections.nCopies(AuditResults.MAX_FINDINGS + 1, "{\"key\": \"k\"}")) + "]";
        assertThatThrownBy(() -> AuditResults.parseFindings(many)).isInstanceOf(IllegalArgumentException.class);
        assertThat(AuditResults.parseFindings("[]").getJSONArray("findings")).isEmpty();
    }
}
