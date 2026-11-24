import {describe, expect, it} from 'vitest';
import {withSections} from '../../javascript/common/openSections.js';

const IDS = ['accessibility', 'seo', 'health'];

describe('withSections', () => {
    it('opens and closes the sections the editor names, and keeps the others', () => {
        const open = withSections(new Set(), ['accessibility', 'health'], true);
        expect([...withSections(open, ['accessibility'], false)]).toEqual(['health']);
    });

    it('expands or collapses every section at once', () => {
        const all = withSections(new Set(), IDS, true);
        expect(IDS.every(id => all.has(id))).toBe(true);
        expect(withSections(all, IDS, false).size).toBe(0);
    });

    it('leaves the sections it was given unchanged', () => {
        const open = new Set(['seo']);
        withSections(open, ['seo'], false);
        expect(open.has('seo')).toBe(true);
    });
});
