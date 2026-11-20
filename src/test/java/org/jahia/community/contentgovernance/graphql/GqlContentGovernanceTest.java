package org.jahia.community.contentgovernance.graphql;

import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GqlContentGovernanceTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "/sites/digitall, digitall",
        "/sites/digitall/home, digitall",
        "/sites/digitall/home/main/intro, digitall"
    })
    @DisplayName("a path under a site names that site")
    void siteOf_readsTheSiteOfThePath(String path, String siteKey) {
        assertThat(GqlContentGovernance.siteOf(path)).isEqualTo(siteKey);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/sites/a/../b/home", "/sites/a/home/../../b/home", "/sites/a/..", "/sites/..", "/sites/",
        "/sites", "/", "", "/content/home", "sites/a/home"})
    @DisplayName("a path that climbs with .., or that is not under a site, is refused before the gate of a site")
    void siteOf_refusesAPathOutOfItsSite(String path) {
        assertThatThrownBy(() -> GqlContentGovernance.siteOf(path))
                .isInstanceOf(DataFetchingException.class)
                .hasMessage(GqlContentGovernance.INVALID_PATH);
    }
}
