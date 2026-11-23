package org.jahia.community.contentgovernance.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueRuleIdsTest {

    @Test
    @DisplayName("the id of a shipped rule names its module, and an id of the site names none")
    void shippedBy_namesTheModuleOfAShippedRule() {
        assertThat(UniqueRuleIds.shippedBy("seo.title")).isEqualTo("content-governance");
        assertThat(UniqueRuleIds.shippedBy("acme.tone")).isNull();
    }
}
