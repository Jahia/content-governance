import {CHECKS_MARK} from '../drawer/entries';
import {cssPath} from './cssPath';

/**
 * What the preview of a page loaded and shows, measured once per document for the web vitals and the
 * eco-design checks. A resource that a check added after the drawer marked the start of the checks, such
 * as the script of axe-core, is left out.
 */

const LEGACY_IMAGE = /\.(jpe?g|png|gif|bmp|tiff?)($|\?)/i;
const FONT_FILE = /\.(woff2?|ttf|otf|eot)($|\?)/i;
/** A legacy image under this weight is not worth converting. */
const LEGACY_IMAGE_BYTES = 30_000;

const measured = new WeakMap();

/** transferSize is 0 for a resource from the HTTP cache: its encoded size keeps the weight meaningful. */
export const sizeOf = entry => entry.transferSize || entry.encodedBodySize || 0;

/** The last segment of an address, such as `app.js` for `https://example.com/js/app.js?v=2`. */
export function shortName(url) {
    const clean = url.split('?')[0].split('#')[0];
    return clean.split('/').filter(Boolean).pop() || clean;
}

/** The time the checks started in the timeline of the frame, or no limit when the drawer set no mark. */
function checksStart(win) {
    const marks = win.performance.getEntriesByName(CHECKS_MARK);
    return marks.length > 0 ? marks.at(-1).startTime : Number.POSITIVE_INFINITY;
}

function originOf(win, url) {
    try {
        return new win.URL(url).origin;
    } catch {
        return null;
    }
}

/** An image of the page as a finding: its address, and its locator in the preview. */
export const imageFinding = img => ({text: shortName(img.currentSrc || img.src || ''), locator: cssPath(img)});

function measure(win, doc) {
    const start = checksStart(win);
    const nav = win.performance.getEntriesByType('navigation')[0] || null;
    const resources = win.performance.getEntriesByType('resource').filter(entry => entry.startTime < start);
    const images = Array.from(doc.images || []);
    const viewportHeight = win.innerHeight || 800;
    const belowFold = images.filter(img => img.getBoundingClientRect().top >= viewportHeight);
    const fonts = resources.filter(entry => FONT_FILE.test(entry.name));
    const origin = win.location.origin;
    const byType = {};
    resources.forEach(entry => {
        const type = entry.initiatorType || 'other';
        byType[type] = (byType[type] || 0) + 1;
    });
    const imageOf = url => images.find(img => (img.currentSrc || img.src) === url);
    return {
        nav,
        resources,
        totalBytes: resources.reduce((sum, entry) => sum + sizeOf(entry), 0) + (nav ? sizeOf(nav) : 0),
        requests: resources.length + 1,
        byType,
        largest: [...resources].sort((a, b) => sizeOf(b) - sizeOf(a)).slice(0, 5)
            .map(entry => ({name: shortName(entry.name), url: entry.name, bytes: sizeOf(entry), type: entry.initiatorType || 'other'})),
        domNodes: doc.querySelectorAll('*').length,
        images,
        missingDims: images.filter(img => !img.getAttribute('width') || !img.getAttribute('height')),
        oversized: images.filter(img => img.naturalWidth > 0 && img.clientWidth > 0 && img.naturalWidth > img.clientWidth * 2),
        belowFold,
        belowFoldEager: belowFold.filter(img => (img.getAttribute('loading') || '').toLowerCase() !== 'lazy'),
        autoplay: Array.from(doc.querySelectorAll('video[autoplay], audio[autoplay]')),
        fonts,
        legacyFonts: fonts.filter(entry => !/\.woff2($|\?)/i.test(entry.name)),
        legacyImages: resources.filter(entry => LEGACY_IMAGE.test(entry.name) && sizeOf(entry) > LEGACY_IMAGE_BYTES)
            .map(entry => ({entry, img: imageOf(entry.name)})),
        thirdParty: [...new Set(resources.map(entry => originOf(win, entry.name)).filter(o => o && o !== origin))]
    };
}

/**
 * The measure of the preview, the same for every check of one audit: each run of the drawer loads a new
 * preview document.
 *
 * @param {Window} win the window of the preview
 * @param {Document} doc its document
 * @returns {object} what the page loaded and shows
 */
export function measurePage(win, doc) {
    if (!measured.has(doc)) {
        measured.set(doc, measure(win, doc));
    }
    return measured.get(doc);
}
