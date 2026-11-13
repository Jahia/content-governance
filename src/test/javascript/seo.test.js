import {describe, expect, it} from 'vitest';
import {normalizeLabel} from '../../javascript/audit/analyzers/seo.js';

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
