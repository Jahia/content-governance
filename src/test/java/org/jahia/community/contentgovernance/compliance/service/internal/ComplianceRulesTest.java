package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ComplianceRulesTest {

    private static final String LEGAL = "/sites/acme/contents/compliance-glossaries/legal";
    private static final String BRAND = "/sites/acme/contents/compliance-glossaries/brand";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private static ContentRule rule(String id, String folder, Map<String, Object> more) {
        Map<String, Object> values = new HashMap<>(Map.of(RuleValues.RULE_ID, id, RuleValues.KIND, "forbidden",
                RuleValues.PATTERN, id.substring(id.indexOf('.') + 1)));
        values.putAll(more);
        return RuleValues.toRule(values).inFolder(folder);
    }

    private static ComplianceRules of(String language, ContentRule... rules) {
        return ComplianceRules.of(List.of(rules), language, DAY,
                path -> new Glossary(path, "uuid-" + path.substring(path.lastIndexOf('/') + 1), "Title", 0));
    }

    @Test
    void keepsThePatternRulesThatOneTextCanBreak() {
        ComplianceRules applied = of("en",
                rule("compliance.free", LEGAL, Map.of(RuleValues.RULE_LANGUAGE, "en")),
                rule("brand.seamless", BRAND, Map.of()),
                rule("glossary.jeton", BRAND, Map.of(RuleValues.RULE_LANGUAGE, "fr")),
                rule("legal.risk", LEGAL, Map.of(RuleValues.KIND, "mandatory")),
                rule("seo.title", null, Map.of(RuleValues.EVALUATOR, "threshold")),
                rule("brand.off", BRAND, Map.of(RuleValues.ENABLED, false)),
                rule("brand.later", BRAND, Map.of(RuleValues.VALID_FROM, DAY.plusDays(1))));

        // A rule of another language stays: a glossary rule reports a form of its term in any language.
        assertThat(applied.rules()).containsOnlyKeys("compliance.free", "brand.seamless", "glossary.jeton");
        assertThat(applied.writing()).extracting(w -> w.folder()).containsExactly(LEGAL, BRAND, BRAND);
        assertThat(applied.folders()).extracting(Glossary::path, Glossary::termCount)
                .containsExactly(tuple(BRAND, 1), tuple(LEGAL, 1));
    }

    @Test
    void namesEachFolderOnceWithItsRuleCount() {
        ComplianceRules applied = of("en",
                rule("compliance.free", LEGAL, Map.of()),
                rule("compliance.guaranteed", LEGAL, Map.of()),
                rule("brand.seamless", BRAND, Map.of()));

        assertThat(applied.folders()).extracting(Glossary::path, Glossary::uuid, Glossary::termCount)
                .containsExactly(
                        tuple(BRAND, "uuid-brand", 1),
                        tuple(LEGAL, "uuid-legal", 2));
    }

    @Test
    void namesNoFolderWhenNoRuleOfTheLanguageFits() {
        ComplianceRules applied = of("de", rule("compliance.free", LEGAL, Map.of(RuleValues.RULE_LANGUAGE, "en")));

        assertThat(applied.folders()).isEmpty();
    }
}
