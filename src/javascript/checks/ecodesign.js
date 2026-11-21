/**
 * The eco-design checks of a page, from RGESN (Référentiel Général d'Écoconception de Services Numériques,
 * 2024): the criteria of its Frontend and Contents families that a single rendered page shows. They give
 * no RGESN conformity score; the Ecodesign tab lists the rest as a manual checklist.
 *
 * Each check measures one thing and is an evaluator of its own, such as `ecodesign.pageWeight`. A rule of
 * that evaluator gives the limit: `maximum` in its unit, or no element at all for a count with no maximum.
 * A site that adds a rule of the same evaluator with another limit gets a second result.
 *
 * Each result carries in `values` its RGESN `family`, `display` (the measured value as the list of
 * criteria shows it) and the values that the texts of the rule name, such as `count` and `size`.
 */

import {imageFinding, measurePage, shortName, sizeOf} from './pageMeasure';
import {cssPath} from './cssPath';

const KILOBYTE = 1024;
const MEGABYTE = KILOBYTE * KILOBYTE;
const ASSET_TYPES = new Set(['script', 'link', 'css']);

/** True when the value is inside the limits of the rule; a count with no maximum must be 0. */
const within = (rule, value, max = 0) => (rule.minimum === null || rule.minimum === undefined || value >= rule.minimum)
    && value <= (rule.maximum ?? max);

const megabytes = (bytes, digits) => `${(bytes / MEGABYTE).toFixed(digits)} MB`;

const resourceFinding = entry => ({text: shortName(entry.name), context: `${Math.round(sizeOf(entry) / KILOBYTE)} KB`});

/** A measure of elements of the page: it passes with no element, and fails with one finding per element. */
const elements = (family, list, finding) => (page, rule) => {
    const found = list(page);
    const passed = within(rule, found.length);
    return {passed, values: {family, count: found.length, display: String(found.length)}, findings: passed ? [] : found.map(finding)};
};

/** The measure of each evaluator: what it gives for a rule of the page. */
export const MEASURES = {
    'ecodesign.pageWeight': (page, rule) => {
        const passed = within(rule, page.totalBytes / KILOBYTE, Number.POSITIVE_INFINITY);
        return {
            passed,
            values: {family: 'frontend', bytes: page.totalBytes, size: megabytes(page.totalBytes, 1), display: megabytes(page.totalBytes, 2)},
            findings: passed ? [] : page.largest.map(entry => ({text: entry.name, context: `${Math.round(entry.bytes / KILOBYTE)} KB`}))
        };
    },
    'ecodesign.requests': (page, rule) => ({
        passed: within(rule, page.requests, Number.POSITIVE_INFINITY),
        values: {family: 'frontend', count: page.requests, display: String(page.requests)},
        findings: []
    }),
    'ecodesign.domSize': (page, rule) => ({
        passed: within(rule, page.domNodes, Number.POSITIVE_INFINITY),
        values: {family: 'frontend', count: page.domNodes, display: String(page.domNodes)},
        findings: []
    }),
    'ecodesign.lazyLoading': (page, rule) => {
        const eager = page.belowFoldEager;
        const passed = within(rule, eager.length);
        return {
            passed,
            values: {family: 'frontend', count: eager.length, display: eager.length > 0 ? `${eager.length}/${page.belowFold.length}` : String(page.belowFold.length)},
            findings: passed ? [] : eager.map(imageFinding)
        };
    },
    'ecodesign.webFonts': (page, rule) => {
        const legacy = page.legacyFonts.length;
        const passed = within(rule, page.fonts.length, Number.POSITIVE_INFINITY) && legacy === 0;
        const legacyNote = legacy > 0 ? ` (${legacy} legacy)` : '';
        return {
            passed,
            values: {family: 'frontend', count: page.fonts.length, legacy, display: `${page.fonts.length}${legacyNote}`},
            findings: passed ? [] : page.fonts.map(resourceFinding)
        };
    },
    'ecodesign.heavyAssets': (page, rule) => {
        const limit = (rule.maximum ?? Number.POSITIVE_INFINITY) * KILOBYTE;
        const heavy = page.resources.filter(entry => ASSET_TYPES.has(entry.initiatorType) && sizeOf(entry) > limit)
            .sort((a, b) => sizeOf(b) - sizeOf(a));
        return {
            passed: heavy.length === 0,
            values: {family: 'frontend', count: heavy.length, display: String(heavy.length), ...(heavy.length > 0 ? {name: shortName(heavy[0].name)} : {})},
            findings: heavy.map(resourceFinding)
        };
    },
    'ecodesign.autoplay': elements('ux', page => page.autoplay, media => ({text: media.tagName.toLowerCase(), locator: cssPath(media)})),
    'ecodesign.imageFormats': elements('contents', page => page.legacyImages, ({entry, img}) => ({
        ...resourceFinding(entry),
        ...(img ? {locator: cssPath(img)} : {})
    })),
    'ecodesign.oversizedImages': elements('contents', page => page.oversized, img => ({
        ...imageFinding(img),
        context: `${img.naturalWidth}px → ${img.clientWidth}px`
    })),
    'ecodesign.imageDimensions': elements('contents', page => page.missingDims, imageFinding),
    'ecodesign.thirdParty': (page, rule) => {
        const passed = within(rule, page.thirdParty.length, Number.POSITIVE_INFINITY);
        return {
            passed,
            values: {family: 'architecture', count: page.thirdParty.length, display: String(page.thirdParty.length)},
            findings: passed ? [] : page.thirdParty.map(origin => ({text: origin}))
        };
    }
};

/**
 * Runs the eco-design check of one evaluator on the preview.
 *
 * @param {string} evaluator a key of {@link MEASURES}
 * @param {{document: Document, window: Window, rules: Array<object>}} context what the drawer gives a check
 * @returns {Array<{ruleId: string, passed: boolean, values: object, findings: Array<object>}>} one result per rule
 */
export function runEcodesign(evaluator, {document, window, rules}) {
    const page = measurePage(window, document);
    return rules.map(rule => ({ruleId: rule.id, ...MEASURES[evaluator](page, rule)}));
}
