package org.jahia.community.contentgovernance.compliance.service.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlossaryTypeOwnerTest {

    @Test
    void takesOverTypesLeftByTheRemovedModule() {
        assertThat(GlossaryTypeOwner.mustTakeOver("content-compliance-scan", false)).isTrue();
    }

    @Test
    void leavesTypesOfAnInstalledModule() {
        assertThat(GlossaryTypeOwner.mustTakeOver("content-compliance-scan", true)).isFalse();
    }

    @Test
    void leavesTypesItAlreadyOwnsOrThatAreMissing() {
        assertThat(GlossaryTypeOwner.mustTakeOver("content-governance", false)).isFalse();
        assertThat(GlossaryTypeOwner.mustTakeOver(null, false)).isFalse();
    }
}
