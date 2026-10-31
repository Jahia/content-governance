/**
 * Language-aware readability analysis of the preview frame's main content.
 *
 * The score is a Flesch Reading Ease adapted to the page language, each adaptation on Flesch's
 * 0-100 scale (higher reads easier), so the bands and advice are shared. ASL is words per
 * sentence, ASW syllables per word:
 * - en Flesch (1948): 206.835 - 1.015 ASL - 84.6 ASW, plus the Flesch-Kincaid grade level
 * - fr Kandel and Moles (1958): 207 - 1.015 ASL - 73.6 ASW
 * - de Amstad (1978): 180 - ASL - 58.5 ASW
 * - es Fernandez Huerta (1959), with the sentence term as corrected by Law (2011): 206.84 - 1.02 ASL - 60 ASW
 * - it Franchina and Vacca (1986): 206 - ASL - 65 ASW
 * - nl Douma (1960): 206.835 - 0.93 ASL - 77 ASW
 * - pt Martins et al. (1996): 248.835 - 1.015 ASL - 84.6 ASW
 *
 * Another language gets no score rather than the English one: its text statistics and structure
 * are still measured. Languages written without spaces between words (Japanese, Chinese, Thai...)
 * have no word count either: characters and sentences are counted instead.
 */

const FORMULAS = {
    en: {name: 'Flesch', score: (asl, asw) => 206.835 - (1.015 * asl) - (84.6 * asw)},
    fr: {name: 'Kandel-Moles', score: (asl, asw) => 207 - (1.015 * asl) - (73.6 * asw)},
    de: {name: 'Amstad', score: (asl, asw) => 180 - asl - (58.5 * asw)},
    es: {name: 'Fernández Huerta', score: (asl, asw) => 206.84 - (1.02 * asl) - (60 * asw)},
    it: {name: 'Franchina-Vacca', score: (asl, asw) => 206 - asl - (65 * asw)},
    nl: {name: 'Douma', score: (asl, asw) => 206.835 - (0.93 * asl) - (77 * asw)},
    pt: {name: 'Martins', score: (asl, asw) => 248.835 - (1.015 * asl) - (84.6 * asw)}
};

// Vowel letters per language: a run of them is one syllable nucleus (diphthongs count once).
const VOWELS = {
    en: /[aeiouy]+/g,
    fr: /[aeiouyàâäéèêëîïôöùûüœæ]+/g,
    de: /[aeiouyäöü]+/g,
    es: /[aeiouáéíóúü]+/g,
    it: /[aeiouàèéìíòóù]+/g,
    nl: /[aeiouyáéèëíïóöú]+/g,
    pt: /[aeiouáàâãéêíóôõú]+/g
};

// A diaeresis after another vowel opens a new syllable in French and Dutch (Noël, naïf, geëerd).
const HIATUS = {fr: /[aeiouy][ëïü]/g, nl: /[aeiouy][ëïü]/g};

// Written without spaces between words: a word count means nothing there.
const UNSPACED = new Set(['ja', 'zh', 'th', 'lo', 'km', 'my']);

const LONG_SENTENCE_WORDS = 25;
const MIN_WORDS = 30;
const READING_WPM = 200;

/**
 * Syllables of one word by vowel groups, plus a hiatus marked by a diaeresis, minus the silent
 * final e of English and French.
 *
 * @param {string} word a word as split from the text
 * @param {string} lang a language with a formula (`FORMULAS` key)
 * @returns {number} 0 for a word without letters, else at least 1
 */
export function countSyllables(word, lang) {
    const w = word.toLowerCase().replaceAll(/[^\p{L}]/gu, '');
    if (w.length === 0) {
        return 0;
    }

    let count = (w.match(VOWELS[lang] || VOWELS.en) || []).length;
    if (HIATUS[lang]) {
        count += (w.match(HIATUS[lang]) || []).length;
    }

    if (count > 1 && (lang === 'en' || lang === 'fr')
        && (w.endsWith('e') || w.endsWith('es') || (lang === 'en' && w.endsWith('ed')))) {
        count--;
    }

    return Math.max(1, count);
}

function scoreBand(score) {
    if (score >= 90) {
        return 'veryEasy';
    }

    if (score >= 70) {
        return 'easy';
    }

    if (score >= 50) {
        return 'standard';
    }

    if (score >= 30) {
        return 'difficult';
    }

    return 'veryDifficult';
}

/**
 * Text statistics and score of a plain text.
 *
 * @param {string} text the text, whitespace already collapsed
 * @param {string} lang two-letter language code, lower case
 * @returns {object} `{lang, spaced, empty, scored, formula, score, band, gradeLevel, words,
 *     characters, sentences, avgSentenceLength, avgSyllablesPerWord, longSentences, readingMinutes}`;
 *     the fields a language cannot measure are `null`
 */
export function analyzeText(text, lang) {
    if (UNSPACED.has(lang)) {
        const sentences = text.split(/[。．！？!?]+/).map(s => s.trim()).filter(Boolean);
        const characters = (text.match(/[\p{L}\p{N}]/gu) || []).length;
        return {
            lang, spaced: false, empty: characters === 0, scored: false,
            formula: null, score: null, band: null, gradeLevel: null,
            words: null, characters, sentences: sentences.length,
            avgSentenceLength: null, avgSyllablesPerWord: null, longSentences: null, readingMinutes: null
        };
    }

    // A sentence ends at the spaces after a full stop, ! ? or an ellipsis (a lookbehind: no backtracking)
    const sentences = text.split(/(?<=[.!?…])\s+/)
        .map(s => s.trim())
        .filter(s => s.split(/\s+/).length > 1);
    const words = text.split(/\s+/)
        .map(w => w.replaceAll(/[^\p{L}\p{N}'’-]/gu, ''))
        .filter(w => w.length > 0);

    if (words.length < MIN_WORDS) {
        return {lang, spaced: true, empty: true, scored: false, words: words.length};
    }

    const asl = words.length / Math.max(1, sentences.length);
    const formula = FORMULAS[lang];
    let score = null;
    let asw = null;
    if (formula) {
        asw = words.reduce((sum, w) => sum + countSyllables(w, lang), 0) / words.length;
        score = Math.round(Math.min(100, Math.max(0, formula.score(asl, asw))));
    }

    return {
        lang,
        spaced: true,
        empty: false,
        scored: Boolean(formula),
        formula: formula ? formula.name : null,
        score,
        band: formula ? scoreBand(score) : null,
        gradeLevel: lang === 'en' ? Math.round(((0.39 * asl) + (11.8 * asw) - 15.59) * 10) / 10 : null,
        words: words.length,
        characters: null,
        sentences: sentences.length,
        avgSentenceLength: Math.round(asl * 10) / 10,
        avgSyllablesPerWord: formula ? Math.round(asw * 100) / 100 : null,
        longSentences: sentences.filter(s => s.split(/\s+/).length > LONG_SENTENCE_WORDS).length,
        readingMinutes: Math.max(1, Math.round(words.length / READING_WPM))
    };
}

export function runReadability(frame, language) {
    const doc = frame.contentDocument;
    if (!doc || !doc.body) {
        throw new Error('Preview frame is not accessible');
    }

    const lang = (language || doc.documentElement.lang || 'en').slice(0, 2).toLowerCase();
    const rootEl = doc.querySelector('main') || doc.body;

    const clone = rootEl.cloneNode(true);
    clone.querySelectorAll('script,style,noscript,svg,nav,header,footer,[aria-hidden="true"]')
        .forEach(node => node.remove());
    // Join text nodes with spaces - textContent merges adjacent block
    // elements into single "words", skewing word and sentence counts
    const parts = [];
    const walker = doc.createTreeWalker(clone, 4 /* NodeFilter.SHOW_TEXT */);
    let textNode = walker.nextNode();
    while (textNode) {
        const t = textNode.textContent.trim();
        if (t) {
            parts.push(t);
        }

        textNode = walker.nextNode();
    }

    const text = parts.join(' ').replace(/\s+/g, ' ').trim();
    const stats = analyzeText(text, lang);
    if (stats.empty) {
        return {...stats, recommendations: []};
    }

    // Heading structure (whole page for h1, main content for hierarchy)
    const levels = Array.from(rootEl.querySelectorAll('h1,h2,h3,h4,h5,h6'))
        .map(h => Number(h.tagName[1]));
    let headingSkips = 0;
    let prev = 0;
    levels.forEach(level => {
        if (prev > 0 && level > prev + 1) {
            headingSkips++;
        }

        prev = level;
    });

    const paragraphs = Array.from(rootEl.querySelectorAll('p'))
        .filter(p => (p.textContent || '').trim().length > 0);
    const paragraphWords = paragraphs.reduce(
        (sum, p) => sum + (p.textContent || '').trim().split(/\s+/).length, 0
    );

    const result = {
        ...stats,
        h1Count: doc.querySelectorAll('h1').length,
        headingSkips,
        paragraphs: paragraphs.length,
        avgWordsPerParagraph: stats.spaced && paragraphs.length > 0 ? Math.round(paragraphWords / paragraphs.length) : null
    };

    result.recommendations = buildReadabilityRecommendations(result);
    return result;
}

/**
 * Actionable advice derived from the scores. Keys resolve to
 * readability.recs.<key>.title / .detail in the locale bundles. A measure the
 * language cannot take (no formula, no word count) gives no advice.
 *
 * @param {object} r the readability result
 * @returns {Array<{key: string, severity: string, params: object}>}
 */
export function buildReadabilityRecommendations(r) {
    const recs = [];
    const push = (key, severity, params) => recs.push({key, severity, params: params || {}});

    if (r.scored && r.score < 30) {
        push('veryHardText', 'serious', {score: r.score});
    } else if (r.scored && r.score < 50) {
        push('hardText', 'moderate', {score: r.score});
    }

    if (r.longSentences > 0) {
        push('longSentences', r.longSentences / r.sentences > 0.2 ? 'moderate' : 'minor',
            {count: r.longSentences});
    }

    if (r.avgWordsPerParagraph > 80) {
        push('longParagraphs', 'moderate', {count: r.avgWordsPerParagraph});
    }

    if (r.h1Count !== 1) {
        push('h1Count', 'serious', {count: r.h1Count});
    }

    if (r.headingSkips > 0) {
        push('headingSkips', 'moderate', {count: r.headingSkips});
    }

    if (r.spaced && r.words < 300) {
        push('thinContent', 'minor', {count: r.words});
    }

    return recs;
}
