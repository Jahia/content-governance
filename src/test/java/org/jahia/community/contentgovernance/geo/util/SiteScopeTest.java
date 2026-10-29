package org.jahia.community.contentgovernance.geo.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterisation tests for {@link SiteScope#isLanguage(String)}, the only
 * pure method on {@link SiteScope}. Every other method needs a live JCR
 * session and is out of scope.
 *
 * This value ends up as a node name under a system session further down the
 * call chain, so a path-shaped or otherwise malformed value here is a
 * traversal risk - these tests pin that the guard actually rejects that
 * shape of input, not just "a language string".
 */
class SiteScopeTest {

    @Nested
    class AcceptedLanguageForms {

        @ParameterizedTest
        @ValueSource(strings = {
                "en",
                "fr",
                "EN",                 // letters may be any case
                "fr-BE",              // BCP-47 tag form
                "pt_BR",              // node-name form
                "zh-Hans-CN",         // language-script-region, 2 subtags
                "en-US-POSIX",        // 3 subtags
                "en-a-b-c-d",         // 4 subtags: the upper bound
                "ab",                 // shortest allowed main tag (2 letters)
                "abcdefgh",           // longest allowed main tag (8 letters)
        })
        void isLanguage_validLanguageForms_areAccepted(String language) {
            assertThat(SiteScope.isLanguage(language)).isTrue();
        }
    }

    @Nested
    class RejectedInvalidInput {

        @ParameterizedTest
        @NullAndEmptySource
        void isLanguage_nullOrEmpty_isRejected(String language) {
            assertThat(SiteScope.isLanguage(language)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/etc/passwd",
                "../etc",
                "../../etc/passwd",
                "en/../../etc",
                "sites/mysite",
                "en/fr",
        })
        void isLanguage_pathOrTraversalShapedInput_isRejected(String language) {
            // WHY: this becomes a JCR node name under a system session; a
            // path-shaped value here would be a traversal.
            assertThat(SiteScope.isLanguage(language)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "e",                   // main tag too short (1 letter)
                "abcdefghi",           // main tag too long (9 letters)
                "en-",                 // trailing separator with no subtag
                "en-a-b-c-d-e",        // 5 subtags: past the bound of 4
                "en--US",              // empty subtag between separators
        })
        void isLanguage_outOfShapeButLanguageLike_isRejected(String language) {
            assertThat(SiteScope.isLanguage(language)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"en\u0000", "en\n", "en\t", "en\u0001fr"})
        void isLanguage_controlCharacters_isRejected(String language) {
            assertThat(SiteScope.isLanguage(language)).isFalse();
        }

        @Test
        void isLanguage_absurdlyLongInput_isRejectedAndDoesNotHang() {
            // The subtag group is bounded ({0,4}) precisely so that a long
            // input like this cannot make the matcher recurse per repetition
            // and overflow the stack; it must simply fail to match, quickly.
            String tooLong = "a".repeat(10_000);

            boolean result = Assertions.assertTimeout(Duration.ofSeconds(2),
                    () -> SiteScope.isLanguage(tooLong));

            assertThat(result).isFalse();
        }

        @Test
        void isLanguage_whitespaceAroundOtherwiseValidValue_isRejected() {
            // Unlike FetchGuard's url parsing, isLanguage does no trimming of
            // its own: surrounding whitespace is simply not part of the
            // pattern's character classes, so it fails to match.
            assertThat(SiteScope.isLanguage(" en")).isFalse();
            assertThat(SiteScope.isLanguage("en ")).isFalse();
        }
    }
}
