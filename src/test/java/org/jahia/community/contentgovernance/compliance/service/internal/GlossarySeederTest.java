package org.jahia.community.contentgovernance.compliance.service.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlossarySeederTest {

    @Test
    void sitePathOf_readsTheSiteOfItsInstalledModules() {
        assertThat(GlossarySeeder.sitePathOf("/sites/acme/j:installedModules")).isEqualTo("/sites/acme");
    }

    @Test
    void sitePathOf_ignoresOtherProperties_andDeeperNodes() {
        assertThat(GlossarySeeder.sitePathOf("/sites/acme/j:languages")).isNull();
        assertThat(GlossarySeeder.sitePathOf("/sites/acme/home/j:installedModules")).isNull();
        assertThat(GlossarySeeder.sitePathOf("/j:installedModules")).isNull();
        assertThat(GlossarySeeder.sitePathOf(null)).isNull();
    }

    @Test
    void seedText_isReadAsUtf8_inTheSiteLanguage() {
        assertThat(GlossarySeeder.seedText("fr").getString("seed.folder")).isEqualTo("Glossaires de conformit\u00e9");
        assertThat(GlossarySeeder.seedText("fr").getString("seed.term")).isEqualTo("cliquez ici");
    }

    @Test
    void seedText_fallsBackToEnglish_forALanguageWithoutTranslation() {
        assertThat(GlossarySeeder.seedText("de").getString("seed.term")).isEqualTo("click here");
        assertThat(GlossarySeeder.seedText("en").getString("seed.folder")).isEqualTo("Compliance glossaries");
    }
}
