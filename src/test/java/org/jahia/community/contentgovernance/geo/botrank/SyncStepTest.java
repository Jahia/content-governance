package org.jahia.community.contentgovernance.geo.botrank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncStepTest {

    @Test
    void aSwitchedOffFeaturePausesTheSyncInsteadOfTurningItOff() {
        // Up to 1.4.0 a run with AI citations off disabled the sync for good: it stayed off once the
        // switch came back, until an administrator saved the settings again.
        assertThat(SyncStep.first(true, false)).isEqualTo(SyncStep.SKIP);
    }

    @Test
    void runsWhenTheSyncAndTheFeatureAreOn() {
        assertThat(SyncStep.first(true, true)).isEqualTo(SyncStep.RUN);
    }

    @Test
    void dropsTheTriggerOfASyncSwitchedOffInItsSettings() {
        assertThat(SyncStep.first(false, true)).isEqualTo(SyncStep.UNSCHEDULE);
        assertThat(SyncStep.first(false, false)).isEqualTo(SyncStep.UNSCHEDULE);
    }
}
