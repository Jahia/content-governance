package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoChecksTest {

    @Test
    @DisplayName("a GEO check gives a finding when GEO readiness fails its rule on the report of a page")
    void check_failsWithTheReport() {
        JSONObject report = new JSONObject().put("unreachableCount", 1).put("unreachableError", "timeout");
        CheckInput input = CheckInput.page("/home", "en", "").withFact(GeoChecks.REPORT, report);

        assertThat(new GeoChecks.Check("geo.fetched").check(input, ContentRules.base().get("geo.fetched")))
                .containsExactly(new Finding("geo.fetched", ContentRules.base().get("geo.fetched").severity(), "timeout", null));
        assertThat(new GeoChecks.Check("geo.fetched").check(CheckInput.page("/home", "en", ""),
                ContentRules.base().get("geo.fetched"))).as("no report, no finding").isEmpty();
    }
}
