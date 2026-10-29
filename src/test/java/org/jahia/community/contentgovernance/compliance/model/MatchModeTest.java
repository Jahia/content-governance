package org.jahia.community.contentgovernance.compliance.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchModeTest {

    @Test
    void readsTheChoicelistValuesAndDefaultsToWholeWord() {
        assertThat(MatchMode.fromValue("word")).isEqualTo(MatchMode.WORD);
        assertThat(MatchMode.fromValue("prefix")).isEqualTo(MatchMode.PREFIX);
        assertThat(MatchMode.fromValue(" anywhere ")).isEqualTo(MatchMode.ANYWHERE);
        assertThat(MatchMode.fromValue(null)).isEqualTo(MatchMode.WORD);
        assertThat(MatchMode.fromValue("regex")).isEqualTo(MatchMode.WORD);
    }
}
