import {cssPath} from './cssPath';

/** Headings listed in the outline: a longer page keeps its counts, the list stops here. */
export const MAX_OUTLINE_HEADINGS = 200;
/** Characters of a heading kept in the outline. */
export const MAX_HEADING_CHARS = 160;

/**
 * The outline of a page from its headings in document order: each with the level it skips from,
 * when it sits more than one level under the heading before it (H2 then H4), and whether it has no
 * text. The count of skips is the one the Readability tab has always shown.
 *
 * @param {Array<{level: number, text: string, selector: string}>} headings in document order
 * @returns {{outline: Array<{level: number, text: string, selector: string, skippedFrom: (number|undefined), empty: (boolean|undefined)}>, skips: number, total: number}}
 *     `outline` holds the first `MAX_OUTLINE_HEADINGS` headings, `skips` and `total` count them all
 */
export function buildOutline(headings) {
    let skips = 0;
    let prev = 0;
    const outline = [];
    headings.forEach(({level, text, selector}) => {
        const skipped = prev > 0 && level > prev + 1;
        if (skipped) {
            skips++;
        }
        if (outline.length < MAX_OUTLINE_HEADINGS) {
            const trimmed = (text || '').replaceAll(/\s+/g, ' ').trim();
            const entry = {level, text: trimmed.slice(0, MAX_HEADING_CHARS), selector};
            if (skipped) {
                entry.skippedFrom = prev;
            }
            if (!trimmed) {
                entry.empty = true;
            }
            outline.push(entry);
        }
        prev = level;
    });
    return {outline, skips, total: headings.length};
}

// What a heading says: its text, else what stands for it (an image's alt, an aria-label)
function headingText(heading) {
    const text = (heading.textContent || '').trim();
    if (text) {
        return text;
    }
    return heading.getAttribute('aria-label') || heading.querySelector('img[alt]')?.getAttribute('alt') || '';
}

/**
 * The headings of an element, in document order, as `buildOutline` takes them.
 *
 * @param {Element} root the element to read (the page's main content)
 * @returns {Array<{level: number, text: string, selector: string}>}
 */
export function collectHeadings(root) {
    return Array.from(root.querySelectorAll('h1,h2,h3,h4,h5,h6')).map(heading => ({
        level: Number(heading.tagName[1]),
        text: headingText(heading),
        selector: cssPath(heading)
    }));
}
