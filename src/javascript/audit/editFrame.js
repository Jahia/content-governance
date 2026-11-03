// Page Builder's page under edit, beside the docked drawer: where "Highlight on the page" marks an
// element, rather than in the section's own preview. The audit itself always runs in the preview,
// a plain render of the page; the edit frame adds jContent's markup around every content.

const SKIPPED = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE']);
// jContent's own layer in the edit frame: boxes, insertion points, overlays
const EDIT_CHROME = '#jahia-portal-root';
// How far up the page a finding may climb when its own element is not in the edit frame
const MAX_ANCESTORS = 4;

/**
 * The document of Page Builder's active frame, when it shows this page in this language.
 *
 * @param {string} path the audited page
 * @param {string} language the audited language
 * @returns {Document|null} null outside Page Builder, or on another page or language
 */
export function findEditFrameDocument(path, language) {
    const frame = document.querySelector('iframe[data-sel-role="page-builder-frame-active"]');
    let doc = null;
    try {
        doc = frame && frame.contentDocument;
    } catch {
        return null;
    }
    if (!doc || !doc.body) {
        return null;
    }
    let pathname = '';
    try {
        pathname = decodeURIComponent(doc.location.pathname);
    } catch {
        return null;
    }
    return pathname.endsWith(`/${language}${path}.html`) ? doc : null;
}

export const normalize = s => (s || '')
    .replace(/[’‘]/g, '\'')
    .replace(/[“”«»]/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();

const isChrome = el => Boolean(el.closest(EDIT_CHROME));

// What an element is, whichever document renders it: its tag, what it points to, its words
const keyOf = el => [
    el.tagName,
    el.getAttribute('src') || el.getAttribute('href') || el.getAttribute('alt') || '',
    normalize(el.textContent).slice(0, 300)
].join('|');

function sameElement(el, doc) {
    if (el.id) {
        const byId = doc.getElementById(el.id);
        if (byId && byId.tagName === el.tagName && !isChrome(byId)) {
            return byId;
        }
    }
    const key = keyOf(el);
    const candidates = root => [...root.querySelectorAll(el.tagName)].filter(c => keyOf(c) === key);
    const inPreview = candidates(el.ownerDocument.body);
    const inFrame = candidates(doc.body).filter(c => !isChrome(c));
    if (inFrame.length === 0) {
        return null;
    }
    if (inFrame.length === 1) {
        return inFrame[0];
    }
    // The same content repeated: the one at the same rank
    return inFrame[inPreview.indexOf(el)] || null;
}

/**
 * The element of the edit frame that renders an element of the preview: the same id, else the same
 * tag with the same target and words at the same rank. Edit mode adds markup inside some
 * containers, so a container that differs gives way to its nearest ancestor that matches and holds
 * an element of its tag. An element with no children (an image, an empty link) is matched itself
 * or not at all: its container would point the editor at the wrong place, the preview shows it.
 *
 * @param {Element} el an element of the preview document
 * @param {Document} doc the edit frame's document
 * @returns {Element|null} null when nothing matches
 */
export function locateInFrame(el, doc) {
    const own = sameElement(el, doc);
    if (own || el.children.length === 0) {
        return own;
    }
    let node = el.parentElement;
    for (let i = 1; node && node !== el.ownerDocument.body && i <= MAX_ANCESTORS; i++) {
        const found = sameElement(node, doc);
        if (found && found.querySelector(el.tagName)) {
            return found;
        }
        node = node.parentElement;
    }
    return null;
}

/**
 * The tightest element of a document whose text holds a quote. Quotes from a model are approximate:
 * whitespace, apostrophes and quotes are normalized, case is ignored, and the search runs at element
 * level so wording spanning several inline text nodes still matches; failing the whole quote, its
 * first eight then four words.
 *
 * @param {Document} doc the document to search
 * @param {string} sample the quoted text
 * @returns {Element|null}
 */
export function findByText(doc, sample) {
    if (!doc || !doc.body || !sample) {
        return null;
    }
    const fullNeedle = normalize(sample);
    const words = fullNeedle.split(' ');
    const needles = [...new Set([
        fullNeedle,
        words.slice(0, 8).join(' '),
        words.slice(0, 4).join(' ')
    ])].filter(n => n.length >= 3);

    const elements = [...doc.body.querySelectorAll('*')]
        .filter(el => !SKIPPED.has(el.tagName) && !isChrome(el));

    for (const needle of needles) {
        const matches = elements.filter(el => normalize(el.textContent).includes(needle));
        if (matches.length > 0) {
            // Tightest container = the match with the least text
            return matches.reduce((a, b) => ((a.textContent || '').length <= (b.textContent || '').length ? a : b));
        }
    }
    return null;
}
