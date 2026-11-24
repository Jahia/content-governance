package org.jahia.community.contentgovernance.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DrawerDisplayTest {

    @Test
    void hidesEveryElementWithNoCfg() {
        assertThat(new DrawerDisplay().shown()).isEqualTo(new DrawerDisplay.Shown(false, false, false));
    }

    @Test
    void showsEachElementWhoseKeyIsTrue() {
        DrawerDisplay display = new DrawerDisplay();

        display.activate(Map.of("SHOW_FIX_FIRST", "true"));
        assertThat(display.shown()).isEqualTo(new DrawerDisplay.Shown(true, false, false));

        display.activate(Map.of("SHOW_FIX_FIRST", "false", "SHOW_PREVIEW_BUTTON", " TRUE "));
        assertThat(display.shown()).isEqualTo(new DrawerDisplay.Shown(false, true, false));

        display.activate(Map.of("SHOW_PAGE_SUMMARY", "true"));
        assertThat(display.shown()).isEqualTo(new DrawerDisplay.Shown(false, false, true));
    }

    @Test
    void hidesAnElementWhoseValueIsNotTrue() {
        DrawerDisplay display = new DrawerDisplay();

        display.activate(Map.of("SHOW_FIX_FIRST", "yes", "SHOW_PREVIEW_BUTTON", "", "SHOW_PAGE_SUMMARY", "1"));

        assertThat(display.shown()).isEqualTo(new DrawerDisplay.Shown(false, false, false));
    }
}
