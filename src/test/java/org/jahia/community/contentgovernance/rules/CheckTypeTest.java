package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CheckTypeTest {

    private static final Pattern PROPERTY = Pattern.compile("^ - (\\w+) ", Pattern.MULTILINE);

    @Test
    @DisplayName("a value names its check type, and a missing or unknown value names none")
    void of_readsTheName() {
        assertThat(CheckType.of("CODE")).isEqualTo(CheckType.CODE);
        assertThat(CheckType.of("AI")).isEqualTo(CheckType.AI);
        assertThat(CheckType.of("code")).isNull();
        assertThat(CheckType.of(null)).isNull();
    }

    @Test
    @DisplayName("a rule of before the check types takes PATTERN for pattern or no check, AI for model, and CODE for any other check")
    void ofEvaluator_followsTheCheck() {
        assertThat(CheckType.ofEvaluator("pattern")).isEqualTo(CheckType.PATTERN);
        assertThat(CheckType.ofEvaluator(null)).isEqualTo(CheckType.PATTERN);
        assertThat(CheckType.ofEvaluator("")).isEqualTo(CheckType.PATTERN);
        assertThat(CheckType.ofEvaluator("model")).isEqualTo(CheckType.AI);
        assertThat(CheckType.ofEvaluator("threshold")).isEqualTo(CheckType.CODE);
        assertThat(CheckType.ofEvaluator("axe")).isEqualTo(CheckType.CODE);
    }

    @Test
    @DisplayName("a key of a check type is on that type only, minimum is on AI and CODE, and a common key is on every rule")
    void holds_followsTheKeysOfEachType() {
        assertThat(CheckType.holds(CheckType.PATTERN, RuleValues.PATTERN)).isTrue();
        assertThat(CheckType.holds(CheckType.CODE, RuleValues.PATTERN)).isFalse();
        assertThat(CheckType.holds(CheckType.AI, RuleValues.MINIMUM)).isTrue();
        assertThat(CheckType.holds(CheckType.CODE, RuleValues.MINIMUM)).isTrue();
        assertThat(CheckType.holds(CheckType.AI, RuleValues.UNIT)).isFalse();
        assertThat(CheckType.holds(null, RuleValues.UNIT)).isFalse();
        assertThat(CheckType.holds(null, RuleValues.SEVERITY)).isTrue();
        assertThat(CheckType.holds(CheckType.AI, RuleValues.CHECK_TYPE)).isTrue();
    }

    @Test
    @DisplayName("the mixin of each check type declares its keys, and the rule type declares none of them")
    void definitions_holdTheKeysOfEachTypeOnItsMixin() throws IOException {
        String cnd = definitions();
        for (CheckType type : CheckType.values()) {
            assertThat(propertiesOf(cnd, type.mixin())).as(type.mixin()).containsExactlyElementsOf(type.keys());
            assertThat(cnd).contains("[" + type.mixin() + "] > jmix:dynamicFieldset mixin\n extends = " + ContentRules.TYPE);
        }
        Set<String> rule = propertiesOf(cnd, ContentRules.TYPE);
        assertThat(rule).contains(RuleValues.CHECK_TYPE, RuleValues.EVALUATOR, RuleValues.ADVICE);
        for (CheckType type : CheckType.values()) {
            assertThat(rule).doesNotContainAnyElementsOf(type.keys());
        }
        assertThat(RuleValues.KEYS).containsAll(rule.stream().filter(key -> !List.of("source", "retired", "seedDigest").contains(key)).toList());
    }

    @Test
    @DisplayName("the choicelist gives each check type with the mixin that jContent adds")
    void initializer_namesTheMixinOfEachType() throws RepositoryException {
        List<ChoiceListValue> choices = new CheckTypesInitializer().getChoiceListValues(null, null, null, null, Map.of());

        List<String> values = new ArrayList<>();
        for (ChoiceListValue choice : choices) {
            values.add(choice.getValue().getString() + " " + choice.getProperties().get(CheckTypesInitializer.ADD_MIXIN));
        }
        assertThat(values).containsExactly("PATTERN ccsmix:patternCheck", "AI ccsmix:aiCheck", "CODE ccsmix:codeCheck");
    }

    private static String definitions() throws IOException {
        try (InputStream in = CheckTypeTest.class.getResourceAsStream("/META-INF/definitions.cnd")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The names of the properties that a type of the definitions declares, in order. */
    private static Set<String> propertiesOf(String cnd, String type) {
        int start = cnd.indexOf("[" + type + "]");
        assertThat(start).as(type).isNotNegative();
        int end = cnd.indexOf("\n\n", start);
        Matcher matcher = PROPERTY.matcher(cnd.substring(start, end < 0 ? cnd.length() : end));
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
