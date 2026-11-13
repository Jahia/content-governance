import {describe, expect, it} from 'vitest';
import {MAX_HEADING_CHARS, MAX_OUTLINE_HEADINGS, buildOutline} from '../../javascript/audit/analyzers/headingOutline.js';

const h = (level, text = `Heading ${level}`) => ({level, text, selector: `h${level}`});

describe('buildOutline', () => {
    it('keeps the headings in order with their level, text and selector', () => {
        const {outline, skips, total} = buildOutline([h(1, 'Title'), h(2, 'Section'), h(3, 'Detail'), h(2, 'Other section')]);
        expect(outline).toEqual([
            {level: 1, text: 'Title', selector: 'h1'},
            {level: 2, text: 'Section', selector: 'h2'},
            {level: 3, text: 'Detail', selector: 'h3'},
            {level: 2, text: 'Other section', selector: 'h2'}
        ]);
        expect(skips).toBe(0);
        expect(total).toBe(4);
    });

    it('marks a heading more than one level under the one before it, and counts it', () => {
        const {outline, skips} = buildOutline([h(1), h(2), h(4), h(6), h(2), h(3)]);
        expect(outline.map(e => e.skippedFrom)).toEqual([undefined, undefined, 2, 4, undefined, undefined]);
        expect(skips).toBe(2);
    });

    it('does not count going back up, nor the first heading whatever its level', () => {
        const {outline, skips} = buildOutline([h(3), h(4), h(2)]);
        expect(outline.some(e => e.skippedFrom)).toBe(false);
        expect(skips).toBe(0);
    });

    it('marks a heading without text, and tidies the text of the others', () => {
        const {outline} = buildOutline([h(2, '  Our \n  approach  '), h(3, '   '), {level: 3, selector: 'h3'}]);
        expect(outline[0]).toEqual({level: 2, text: 'Our approach', selector: 'h2'});
        expect(outline[1].empty).toBe(true);
        expect(outline[2].empty).toBe(true);
        expect(outline[0].empty).toBeUndefined();
    });

    it('cuts a long heading and a long page, and still counts every heading and skip', () => {
        const long = buildOutline([h(2, 'x'.repeat(MAX_HEADING_CHARS + 40))]);
        expect(long.outline[0].text).toHaveLength(MAX_HEADING_CHARS);

        const many = [...Array.from({length: MAX_OUTLINE_HEADINGS}, () => h(2)), h(2), h(4)];
        const {outline, skips, total} = buildOutline(many);
        expect(outline).toHaveLength(MAX_OUTLINE_HEADINGS);
        expect(total).toBe(MAX_OUTLINE_HEADINGS + 2);
        expect(skips).toBe(1);
    });

    it('gives an empty outline for a page without headings', () => {
        expect(buildOutline([])).toEqual({outline: [], skips: 0, total: 0});
    });
});
