package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.compliance.model.GlossaryTerm;
import org.jahia.community.contentgovernance.compliance.model.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermMatcherTest {

    private static GlossaryTerm term(String text, MatchMode mode) {
        return new GlossaryTerm(text, mode, null, null, "/t/" + text, "/g");
    }

    private static List<String> found(String text, GlossaryTerm... terms) {
        return new TermMatcher(List.of(terms)).find(text).stream()
                .map(m -> text.substring(m.start(), m.end()))
                .toList();
    }

    @Test
    void wholeWordIgnoresCaseAndAccents() {
        assertThat(found("Livraison GRATUITE et gratuité garanties.", term("gratuité", MatchMode.WORD)))
                .containsExactly("GRATUITE", "gratuité");
        assertThat(found("Livraison GRATUITÉ.", term("gratuite", MatchMode.WORD)))
                .containsExactly("GRATUITÉ");
    }

    @Test
    void wholeWordDoesNotMatchInsideOrAtTheStartOfAnotherWord() {
        assertThat(found("garantie, garantis, sans garanti.", term("garanti", MatchMode.WORD)))
                .containsExactly("garanti");
    }

    @Test
    void prefixFindsTheWholeWord() {
        assertThat(found("Guaranteed results, a guarantee, unguarded.", term("guarant", MatchMode.PREFIX)))
                .containsExactly("Guaranteed", "guarantee");
    }

    @Test
    void anywhereFindsInsideWords() {
        assertThat(found("superfree and free", term("free", MatchMode.ANYWHERE)))
                .containsExactly("free", "free");
    }

    @Test
    void phraseMatchesAcrossAnyWhiteSpace() {
        assertThat(found("Satisfait ou\n  remboursé !", term("satisfait ou remboursé", MatchMode.WORD)))
                .containsExactly("Satisfait ou\n  remboursé");
    }

    @Test
    void typographicApostrophesAndLigaturesFold() {
        assertThat(found("C’est l’ŒUVRE", term("c'est l'oeuvre", MatchMode.WORD)))
                .containsExactly("C’est l’ŒUVRE");
    }

    @Test
    void offsetsPointAtTheOriginalTextAfterDecomposedCharacters() {
        // "é" written as e + combining acute: two chars in the original, one in the folded text.
        String text = "Café risqué ici";
        assertThat(found(text, term("risque", MatchMode.WORD))).containsExactly("risqué");
    }

    @Test
    void regexCharactersInTermsAreLiteral() {
        assertThat(found("Taux 100% (garanti) ou 100x", term("100%", MatchMode.WORD), term("(garanti)", MatchMode.WORD)))
                .containsExactly("100%", "(garanti)");
    }

    @Test
    void duplicateTermsAtTheSamePlaceAreReportedOnce() {
        assertThat(found("gratuit", term("Gratuit", MatchMode.WORD), term("gratuit", MatchMode.WORD)))
                .containsExactly("gratuit");
    }

    @Test
    void theSameTermInTwoGlossariesIsReportedForEach() {
        GlossaryTerm legal = new GlossaryTerm("autonomous", MatchMode.PREFIX, "Outcome promise", null, "/t/a", "/g/legal");
        GlossaryTerm brand = new GlossaryTerm("Autonomous", MatchMode.WORD, "Brand voice", null, "/t/b", "/g/brand");
        assertThat(new TermMatcher(List.of(legal, brand)).find("fully autonomous teams"))
                .extracting(m -> m.term().glossaryPath())
                .containsExactlyInAnyOrder("/g/legal", "/g/brand");
    }

    @Test
    void wholeWordMatchesInsideJapaneseAndChineseSentences() {
        assertThat(found("耐久性のある端子と安全なロック機能を備えており", term("安全", MatchMode.WORD)))
                .containsExactly("安全");
        assertThat(found("为领先的医疗保健和生命科学企业提供支持", term("领先", MatchMode.WORD)))
                .containsExactly("领先");
        assertThat(found("モレックスでは、業界をリードするブランド群", term("業界をリード", MatchMode.WORD)))
                .containsExactly("業界をリード");
    }

    @Test
    void prefixStopsAtTheEndOfTheTermInScriptsWithoutSpaces() {
        assertThat(found("製品保証を維持するため", term("保証", MatchMode.PREFIX))).containsExactly("保証");
    }

    @Test
    void latinWordsNextToCjkTextKeepTheirBoundaries() {
        assertThat(found("当社のbestsellerとbest製品", term("best", MatchMode.WORD))).containsExactly("best");
    }

    @Test
    void kanaKeepTheirVoicedMarks() {
        assertThat(found("ガイドを読む", term("カイト", MatchMode.WORD))).isEmpty();
        assertThat(found("ガイドを読む", term("ガイド", MatchMode.WORD))).containsExactly("ガイド");
    }

    @Test
    void boundariesApplyOnlyWhereTheTermHasAWordCharacter() {
        assertThat(found("ユーザー情報を保護します", term("情報", MatchMode.WORD))).containsExactly("情報");
        assertThat(found("ユーザー情報を保護します", term("情報", MatchMode.PREFIX))).containsExactly("情報");
        assertThat(found("Jahia安全", term("安全", MatchMode.WORD))).containsExactly("安全");
        assertThat(found("ユーザー情報とユーザーID", term("ユーザー", MatchMode.WORD))).containsExactly("ユーザー");
    }

    @Test
    void decomposedKanaMatchTheirComposedForm() {
        String decomposed = "\u30AB\u3099\u30A4\u30C8\u3099を読む";   // ガイド written as カ+゛ イ ト+゛
        assertThat(found(decomposed, term("ガイド", MatchMode.WORD))).containsExactly("\u30AB\u3099\u30A4\u30C8\u3099");
        assertThat(found(decomposed, term("カイト", MatchMode.WORD))).isEmpty();
    }

    @Test
    void compatibilityIdeographsMatchTheirStandardForm() {
        assertThat(found("\uF900", term("\u8C48", MatchMode.WORD))).containsExactly("\uF900");
    }

    @Test
    void fullwidthLatinIsFolded() {
        assertThat(found("送料ＦＲＥＥ！", term("free", MatchMode.WORD))).containsExactly("ＦＲＥＥ");
    }

    @Test
    void blankTermsAreIgnored() {
        TermMatcher matcher = new TermMatcher(List.of(term("  ", MatchMode.WORD)));
        assertThat(matcher.isEmpty()).isTrue();
        assertThat(matcher.find("anything")).isEmpty();
    }
}
