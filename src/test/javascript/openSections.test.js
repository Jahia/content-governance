import {describe, expect, it} from 'vitest';
import {defaultOpen, isSectionOpen, withSections} from '../../javascript/common/openSections.js';

const SECTIONS = [
    {id: 'accessibility', count: 3, serious: true, incomplete: false},
    {id: 'seo', count: 2, serious: false, incomplete: false},
    {id: 'vitals', count: 0, serious: false, incomplete: false},
    {id: 'links', count: null, serious: false, incomplete: true},
    {id: 'score', count: 0, serious: true, incomplete: false}
];

describe('defaultOpen', () => {
    it('opens a section with a critical or serious defect, or whose check did not finish', () => {
        expect(defaultOpen(SECTIONS)).toEqual({accessibility: true, seo: false, vitals: false, links: true, score: false});
    });
});

describe('isSectionOpen', () => {
    const defaults = defaultOpen(SECTIONS);
    const none = {key: 'audit-1', open: {}};

    it('follows the defaults until the editor opens or closes a section', () => {
        expect(isSectionOpen(none, 'audit-1', defaults, 'accessibility')).toBe(true);
        expect(isSectionOpen(none, 'audit-1', defaults, 'seo')).toBe(false);
    });

    it('keeps the editor\'s choice for the same results', () => {
        const choice = withSections(withSections(none, 'audit-1', ['accessibility'], false), 'audit-1', ['seo'], true);
        expect(isSectionOpen(choice, 'audit-1', defaults, 'accessibility')).toBe(false);
        expect(isSectionOpen(choice, 'audit-1', defaults, 'seo')).toBe(true);
        expect(isSectionOpen(choice, 'audit-1', defaults, 'links')).toBe(true);
    });

    it('applies the defaults again to new results', () => {
        const choice = withSections(none, 'audit-1', ['accessibility', 'seo'], false);
        expect(isSectionOpen(choice, 'audit-2', defaults, 'accessibility')).toBe(true);
        expect(withSections(choice, 'audit-2', ['vitals'], true)).toEqual({key: 'audit-2', open: {vitals: true}});
    });

    it('expands or collapses every section at once', () => {
        const ids = SECTIONS.map(section => section.id);
        const all = withSections(none, 'audit-1', ids, true);
        expect(ids.every(id => isSectionOpen(all, 'audit-1', defaults, id))).toBe(true);
        expect(ids.some(id => isSectionOpen(withSections(all, 'audit-1', ids, false), 'audit-1', defaults, id))).toBe(false);
    });
});
