import {describe, expect, it} from 'vitest';
import {buildSeoRecommendations, normalizeLabel} from '../../javascript/audit/analyzers/seo.js';
import {BASE_RULES, rulesWith} from './baseRules.js';

describe('normalizeLabel', () => {
    it('drops what decorates a label at either end, and keeps the inside', () => {
        expect(normalizeLabel('Read More +')).toBe('Read More');
        expect(normalizeLabel('  » En savoir plus »  ')).toBe('En savoir plus');
        expect(normalizeLabel('-> Learn\n   more_')).toBe('Learn more');
        expect(normalizeLabel('Lire la suite…')).toBe('Lire la suite');
    });

    it('gives an empty label for nothing, white space or decorations alone', () => {
        expect(normalizeLabel(undefined)).toBe('');
        expect(normalizeLabel('   ')).toBe('');
        expect(normalizeLabel(' >> ')).toBe('');
    });

    // The results of the expression this loop replaces (1.9.1), sample by sample
    it.each([
        ['Read more', 'Read more'], [' read  more ', 'read more'], ['Click here!', 'Click here'], ['[ OK ]', 'OK'],
        ['___', ''], ['', ''], ['a', 'a'], ['+', ''], ['découvrir', 'découvrir'], ['Découvrir →', 'Découvrir'],
        ['En savoir plus »', 'En savoir plus'], ['« ici »', 'ici'], ['more\t\n', 'more'], ['1. Next', '1. Next']
    ])('answers %j as before', (label, expected) => {
        expect(normalizeLabel(label)).toBe(expected);
    });

    // Known limit, kept as it was: a letter outside ASCII at an edge counts as a decoration
    it('still drops an accented letter at an edge', () => {
        expect(normalizeLabel('été')).toBe('t');
        expect(normalizeLabel('Élire')).toBe('lire');
    });

    it('stays fast on a long label', () => {
        expect(normalizeLabel('x'.repeat(5000) + ' !')).toHaveLength(5000);
        expect(normalizeLabel('?'.repeat(5000))).toBe('');
    });
});

describe('the SEO findings follow the rules of the site', () => {
    const facts = title => ({
        title: {text: title, length: title.length},
        description: {text: 'd'.repeat(80), length: 80},
        robots: {content: null, noindex: false, nofollow: false},
        canonical: 'http://localhost/',
        og: {title: 'og', image: 'og.png'},
        jsonLd: {count: 1, errors: 0},
        lang: {htmlLang: 'en', expected: 'en'},
        imagesWithoutAlt: 0,
        badAnchors: 0
    });
    const titleFinding = (title, rules) => buildSeoRecommendations(facts(title), rules).find(rec => rec.rule === 'seo.title');

    it('reports a title of 35 characters only once the site raises the minimum of seo.title to 40', () => {
        expect(titleFinding('t'.repeat(35), BASE_RULES)).toBeUndefined();
        expect(titleFinding('t'.repeat(35), rulesWith('seo.title', {minimum: 40}))).toEqual({
            key: 'titleLength', rule: 'seo.title', severity: 'serious', params: {length: 35, min: 40, max: 60}
        });
    });

    it('reports nothing for a rule the site turned off', () => {
        expect(titleFinding('', rulesWith('seo.title', {enabled: false}))).toBeUndefined();
        expect(titleFinding('', BASE_RULES)).toMatchObject({key: 'noTitle', rule: 'seo.title'});
    });

    it('takes the severity of the rule', () => {
        expect(titleFinding('', rulesWith('seo.title', {severity: 'critical'})).severity).toBe('critical');
    });

    it('gives every finding the id of a rule of the catalog', () => {
        const all = buildSeoRecommendations({...facts(''), description: {text: null, length: 0}, canonical: null,
            og: {}, jsonLd: {count: 0, errors: 0}, lang: {htmlLang: null}, imagesWithoutAlt: 1, badAnchors: 1}, BASE_RULES);
        expect(all.length).toBeGreaterThan(5);
        all.forEach(rec => expect(BASE_RULES.get(rec.rule).id).toBe(rec.rule));
    });
});
