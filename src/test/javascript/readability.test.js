import {describe, expect, it} from 'vitest';
import {analyzeText, buildReadabilityRecommendations, countSyllables} from '../../javascript/audit/analyzers/readability.js';

// Three sentences of ten words, every word of two syllables: ASL 10, ASW 2.
const textOf = word => new Array(3).fill(`${new Array(10).fill(word).join(' ')}.`).join(' ');

describe('readability score per language', () => {
    // Expected values computed by hand from each published formula with ASL 10 and ASW 2.
    it.each([
        ['en', 'pilot', 'Flesch', 27], // 206.835 - 10.15 - 169.2 = 27.49
        ['fr', 'salon', 'Kandel-Moles', 50], // 207 - 10.15 - 147.2 = 49.65
        ['de', 'Auto', 'Amstad', 53], // 180 - 10 - 117 = 53
        ['es', 'casa', 'Fernández Huerta', 77], // 206.84 - 10.2 - 120 = 76.64
        ['it', 'casa', 'Franchina-Vacca', 66], // 206 - 10 - 130 = 66
        ['nl', 'tafel', 'Douma', 44], // 206.835 - 9.3 - 154 = 43.54
        ['pt', 'casa', 'Martins', 69] // 248.835 - 10.15 - 169.2 = 69.49
    ])('%s uses %s', (lang, word, formula, score) => {
        const r = analyzeText(textOf(word), lang);
        expect(r).toMatchObject({lang, spaced: true, empty: false, scored: true, formula, score, words: 30, sentences: 3, avgSentenceLength: 10, avgSyllablesPerWord: 2});
    });

    it('gives the Flesch-Kincaid grade in English only', () => {
        expect(analyzeText(textOf('pilot'), 'en').gradeLevel).toBe(11.9); // 3.9 + 23.6 - 15.59
        expect(analyzeText(textOf('salon'), 'fr').gradeLevel).toBeNull();
    });

    it('keeps the score between 0 and 100', () => {
        expect(analyzeText(textOf('internationalisation'), 'en').score).toBe(0);
        expect(analyzeText(new Array(30).fill('a').join(' '), 'pt').score).toBe(100);
    });
});

describe('syllables', () => {
    it.each([
        ['named', 'en', 1], // silent -ed
        ['school', 'en', 1],
        ['école', 'fr', 2], // e muet
        ['Noël', 'fr', 2], // diaeresis
        ['naïve', 'fr', 2],
        ['schöne', 'de', 2], // a German final e is spoken
        ['canción', 'es', 2],
        ['città', 'it', 2],
        ['geëerd', 'nl', 2],
        ['ruïne', 'nl', 3],
        ['coração', 'pt', 3],
        ['2026', 'en', 0]
    ])('%s (%s) has %i', (word, lang, count) => {
        expect(countSyllables(word, lang)).toBe(count);
    });
});

describe('languages without a formula', () => {
    it('measures a spaced language without scoring it', () => {
        const r = analyzeText(textOf('текст'), 'ru');
        expect(r).toMatchObject({spaced: true, empty: false, scored: false, formula: null, score: null, band: null, words: 30, sentences: 3, longSentences: 0});
    });

    it('counts characters and sentences where words are not spaced', () => {
        const r = analyzeText('これは文です。これも文です。', 'ja');
        expect(r).toMatchObject({spaced: false, empty: false, scored: false, score: null, words: null, characters: 12, sentences: 2, longSentences: null, readingMinutes: null});
        expect(analyzeText('', 'zh').empty).toBe(true);
    });

    it('reports too little text below 30 words', () => {
        expect(analyzeText('Five words are not enough.', 'en')).toMatchObject({empty: true, words: 5});
    });
});

describe('recommendations', () => {
    const base = {spaced: true, scored: true, score: 60, words: 400, sentences: 10, longSentences: 0, avgWordsPerParagraph: 40, h1Count: 1, headingSkips: 0};
    const keys = r => buildReadabilityRecommendations(r).map(rec => rec.key);

    it('advises on a hard text only when there is a score', () => {
        expect(keys({...base, score: 20})).toEqual(['veryHardText']);
        expect(keys({...base, score: 40})).toEqual(['hardText']);
        expect(keys({...base, scored: false, score: null})).toEqual([]);
    });

    it('gives no word-based advice where words are not spaced', () => {
        expect(keys({...base, spaced: false, scored: false, score: null, words: null, longSentences: null, avgWordsPerParagraph: null})).toEqual([]);
        expect(keys({...base, words: 100})).toEqual(['thinContent']);
    });
});
