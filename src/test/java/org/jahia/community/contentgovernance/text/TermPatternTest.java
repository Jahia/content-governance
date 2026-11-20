package org.jahia.community.contentgovernance.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermPatternTest {

    private static List<String> found(String text, String... forms) {
        FoldedText folded = FoldedText.of(text);
        return TermPattern.of(List.of(forms)).find(folded, Integer.MAX_VALUE).stream()
                .map(span -> text.substring(span.start(), span.end()))
                .toList();
    }

    @Test
    void wholeWordIgnoresCaseAndAccents() {
        assertThat(found("Livraison GRATUITE et gratuité garanties.", "gratuité"))
                .containsExactly("GRATUITE", "gratuité");
        assertThat(found("Livraison GRATUITÉ.", "gratuite")).containsExactly("GRATUITÉ");
    }

    @Test
    void wholeWordDoesNotMatchInsideOrAtTheStartOfAnotherWord() {
        assertThat(found("garantie, garantis, sans garanti.", "garanti")).containsExactly("garanti");
    }

    @Test
    void aStemFindsTheWholeWord() {
        assertThat(found("Guaranteed results, a guarantee, unguarded.", "guarant*"))
                .containsExactly("Guaranteed", "guarantee");
    }

    @Test
    void aWildcardOnBothSidesFindsInsideWords() {
        assertThat(found("superfree and free", "*free*")).containsExactly("free", "free");
        assertThat(found("superfree and free", "*free")).containsExactly("free", "free");
        assertThat(found("superfree and freedom", "free")).isEmpty();
    }

    @Test
    void aPhraseMatchesAcrossAnyWhiteSpace() {
        assertThat(found("Satisfait ou\n  remboursé !", "satisfait ou remboursé"))
                .containsExactly("Satisfait ou\n  remboursé");
        assertThat(found("Une offre en nuage.", "en nuage")).containsExactly("en nuage");
    }

    @Test
    void typographicApostrophesAndLigaturesFold() {
        assertThat(found("C’est l’ŒUVRE", "c'est l'oeuvre")).containsExactly("C’est l’ŒUVRE");
    }

    @Test
    void offsetsPointAtTheOriginalTextAfterDecomposedCharacters() {
        // "é" written as e + combining acute: two chars in the original, one in the folded text.
        String text = "Café risqué ici";
        assertThat(found(text, "risque")).containsExactly("risqué");
    }

    @Test
    void regexCharactersInTermsAreLiteral() {
        assertThat(found("Taux 100% (garanti) ou 100x", "100%", "(garanti)")).containsExactly("100%", "(garanti)");
    }

    @Test
    void theLongestFormMatchesFirst() {
        assertThat(found("Create access tokens, then an access token.", "access token", "access tokens"))
                .containsExactly("access tokens", "access token");
    }

    @Test
    void wholeWordMatchesInsideJapaneseAndChineseSentences() {
        assertThat(found("耐久性のある端子と安全なロック機能を備えており", "安全")).containsExactly("安全");
        assertThat(found("为领先的医疗保健和生命科学企业提供支持", "领先")).containsExactly("领先");
        assertThat(found("モレックスでは、業界をリードするブランド群", "業界をリード")).containsExactly("業界をリード");
    }

    @Test
    void aStemStopsAtTheEndOfTheTermInScriptsWithoutSpaces() {
        assertThat(found("製品保証を維持するため", "保証*")).containsExactly("保証");
    }

    @Test
    void latinWordsNextToCjkTextKeepTheirBoundaries() {
        assertThat(found("当社のbestsellerとbest製品", "best")).containsExactly("best");
    }

    @Test
    void kanaKeepTheirVoicedMarks() {
        assertThat(found("ガイドを読む", "カイト")).isEmpty();
        assertThat(found("ガイドを読む", "ガイド")).containsExactly("ガイド");
    }

    @Test
    void boundariesApplyOnlyWhereTheTermHasAWordCharacter() {
        assertThat(found("ユーザー情報を保護します", "情報")).containsExactly("情報");
        assertThat(found("ユーザー情報を保護します", "情報*")).containsExactly("情報");
        assertThat(found("Jahia安全", "安全")).containsExactly("安全");
        assertThat(found("ユーザー情報とユーザーID", "ユーザー")).containsExactly("ユーザー");
    }

    @Test
    void decomposedKanaMatchTheirComposedForm() {
        String decomposed = "ガイドを読む";   // ガイド written as カ+゛ イ ト+゛
        assertThat(found(decomposed, "ガイド")).containsExactly("ガイド");
        assertThat(found(decomposed, "カイト")).isEmpty();
    }

    @Test
    void compatibilityIdeographsMatchTheirStandardForm() {
        assertThat(found("豈", "豈")).containsExactly("豈");
    }

    @Test
    void fullwidthLatinIsFolded() {
        assertThat(found("送料ＦＲＥＥ！", "free")).containsExactly("ＦＲＥＥ");
    }

    @Test
    void blankFormsGiveNoPattern() {
        assertThat(TermPattern.of(List.of("  ", "**"))).isNull();
        assertThat(TermPattern.of(List.of())).isNull();
    }

    @Test
    void aWildcardOnBothSidesMatchesTheTermAloneInsideAWord() {
        assertThat(found("freedom and carefree", "*free*")).containsExactly("free", "free");
    }
}
