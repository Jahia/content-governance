import {describe, expect, it} from 'vitest';
import {defaultOpen, isSectionOpen, withSections} from '../../javascript/common/openSections.js';

const SECTIONS = [
    {id: 'accessibility', count: 3, serious: true, incomplete: false},
    {id: 'seo', count: 2, serious: false, incomplete: false},
    {id: 'vitals', count: 0, serious: false, incomplete: false},
    {id: 'links', count: null, serious: false, incomplete: true},
    {id: 'score', count: 0, serious: true, incomplete: false}
];

/** Every section shows the results of the same audit. */
const audit = key => Object.fromEntries(SECTIONS.map(section => [section.id, key]));

describe('defaultOpen', () => {
    it('opens a section with a critical or serious defect, or whose check did not finish', () => {
        expect(defaultOpen(SECTIONS)).toEqual({accessibility: true, seo: false, vitals: false, links: true, score: false});
    });
});

describe('isSectionOpen', () => {
    const defaults = defaultOpen(SECTIONS);
    const none = {};

    it('follows the defaults until the editor opens or closes a section', () => {
        expect(isSectionOpen(none, audit('audit-1'), defaults, 'accessibility')).toBe(true);
        expect(isSectionOpen(none, audit('audit-1'), defaults, 'seo')).toBe(false);
    });

    it('keeps the editor\'s choice for the same results', () => {
        const choice = withSections(withSections(none, audit('audit-1'), ['accessibility'], false), audit('audit-1'), ['seo'], true);
        expect(isSectionOpen(choice, audit('audit-1'), defaults, 'accessibility')).toBe(false);
        expect(isSectionOpen(choice, audit('audit-1'), defaults, 'seo')).toBe(true);
        expect(isSectionOpen(choice, audit('audit-1'), defaults, 'links')).toBe(true);
    });

    it('applies the defaults again to new results', () => {
        const choice = withSections(none, audit('audit-1'), ['accessibility', 'seo'], false);
        expect(isSectionOpen(choice, audit('audit-2'), defaults, 'accessibility')).toBe(true);
        expect(withSections(choice, audit('audit-2'), ['vitals'], true).vitals).toEqual({key: 'audit-2', open: true});
    });

    it('keeps the choice on a section whose own check gave no new results', () => {
        const before = {...audit('audit-1'), score: null};
        const choice = withSections(none, before, ['seo', 'score'], true);
        const after = {...before, score: 'geo-1'};
        expect(isSectionOpen(choice, after, defaults, 'seo')).toBe(true);
        expect(isSectionOpen(choice, after, defaults, 'score')).toBe(false);
    });

    it('expands or collapses every section at once', () => {
        const ids = SECTIONS.map(section => section.id);
        const all = withSections(none, audit('audit-1'), ids, true);
        expect(ids.every(id => isSectionOpen(all, audit('audit-1'), defaults, id))).toBe(true);
        expect(ids.some(id => isSectionOpen(withSections(all, audit('audit-1'), ids, false), audit('audit-1'), defaults, id))).toBe(false);
    });
});
