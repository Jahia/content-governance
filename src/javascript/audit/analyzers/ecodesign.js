/**
 * RGESN (Référentiel Général d'Écoconception de Services Numériques, 2024)
 * page-level checks. RGESN assesses a whole service against ~78 criteria, most
 * of which are organizational (hosting, governance, backend). This analyzer
 * covers only the subset measurable from a single rendered page - the Frontend
 * and Contents families - and is explicit that it does NOT produce an RGESN
 * conformity score. The rest is surfaced as a manual checklist (ecoChecklist).
 *
 * Reuses the Web Vitals resource data (page weight, request count, DOM size,
 * image issues) and adds a few DOM/resource probes (lazy-loading, fonts,
 * autoplay media, modern image formats, third-party origins).
 */

import {findings} from '../rules';

const KILOBYTE = 1024;

const LEGACY_IMAGE = /\.(jpe?g|png|gif|bmp|tiff?)($|\?)/i;
const MODERN_IMAGE = /\.(webp|avif)($|\?)/i;
const FONT_FILE = /\.(woff2?|ttf|otf|eot)($|\?)/i;

function sizeOf(entry) {
    return entry.transferSize || entry.encodedBodySize || 0;
}

/**
 * @param frame   the preview iframe element
 * @param vitals  the Web Vitals analyzer result (diagnostics reused here)
 */
/**
 * @param {HTMLIFrameElement} frame the preview frame
 * @param {object} vitals the result of the Web Vitals analyzer
 * @param {{get: function(string): object}} rules the rules of the site
 * @returns {object} the criteria and their findings
 */
export function runEcodesign(frame, vitals, rules) {
    const win = frame.contentWindow;
    const doc = frame.contentDocument;
    if (!win || !doc) {
        throw new Error('Preview frame is not accessible');
    }

    const origin = win.location.origin;
    const resources = win.performance.getEntriesByType('resource');

    // --- resource-derived signals -----------------------------------------
    const fontFiles = resources.filter(r => FONT_FILE.test(r.name));
    const legacyFonts = fontFiles.filter(r => !/\.woff2($|\?)/i.test(r.name)).length;

    const imageResources = resources.filter(r =>
        r.initiatorType === 'img' || LEGACY_IMAGE.test(r.name) || MODERN_IMAGE.test(r.name));
    const legacyImages = imageResources.filter(r =>
        LEGACY_IMAGE.test(r.name) && sizeOf(r) > 30_000).length;

    const thirdParty = new Set();
    resources.forEach(r => {
        try {
            const o = new win.URL(r.name).origin;
            if (o !== origin) {
                thirdParty.add(o);
            }
        } catch (e) {
            // ignore malformed
        }
    });

    // --- DOM signals -------------------------------------------------------
    const images = Array.from(doc.images || []);
    const vh = win.innerHeight || 800;
    const belowFold = images.filter(img => {
        const rect = img.getBoundingClientRect();
        return rect.top >= vh;
    });
    const belowFoldEager = belowFold.filter(img =>
        (img.getAttribute('loading') || '').toLowerCase() !== 'lazy').length;

    const autoplay = doc.querySelectorAll('video[autoplay], audio[autoplay]').length;

    const d = vitals && vitals.diagnostics ? vitals.diagnostics : {};
    const totalBytes = d.totalBytes || 0;
    const requests = d.requests || resources.length + 1;
    const domNodes = d.domNodes || doc.querySelectorAll('*').length;
    const oversized = d.oversized || 0;
    const missingDims = d.missingDims || 0;

    const max = id => rules.get(id).maximum;
    const band = (value, limit) => (value <= limit ? 'good' : 'poor');
    const legacyNote = legacyFonts > 0 ? ` (${legacyFonts} legacy)` : '';

    // --- criteria ----------------------------------------------------------
    const criteria = [
        {
            key: 'pageWeight', family: 'frontend',
            ok: totalBytes <= max('ecodesign.pageWeight') * KILOBYTE,
            band: band(totalBytes, max('ecodesign.pageWeight') * KILOBYTE),
            value: `${(totalBytes / 1024 / 1024).toFixed(2)} MB`
        },
        {
            key: 'requests', family: 'frontend',
            ok: requests <= max('ecodesign.requests'),
            band: band(requests, max('ecodesign.requests')),
            value: `${requests}`
        },
        {
            key: 'domSize', family: 'frontend',
            ok: domNodes <= max('ecodesign.domSize'),
            band: band(domNodes, max('ecodesign.domSize')),
            value: `${domNodes}`
        },
        {
            key: 'lazyLoading', family: 'frontend',
            ok: belowFoldEager === 0,
            value: belowFoldEager > 0 ? `${belowFoldEager}/${belowFold.length}` : String(belowFold.length)
        },
        {
            key: 'webFonts', family: 'frontend',
            ok: fontFiles.length <= max('ecodesign.webFonts') && legacyFonts === 0,
            value: `${fontFiles.length}${legacyNote}`
        },
        {
            key: 'autoplay', family: 'ux',
            ok: autoplay === 0,
            value: `${autoplay}`
        },
        {
            key: 'imageFormats', family: 'contents',
            ok: legacyImages === 0,
            value: `${legacyImages}`
        },
        {
            key: 'oversizedImages', family: 'contents',
            ok: oversized === 0,
            value: `${oversized}`
        },
        {
            key: 'imageDimensions', family: 'contents',
            ok: missingDims === 0,
            value: `${missingDims}`
        },
        {
            key: 'thirdParty', family: 'architecture',
            ok: thirdParty.size <= max('ecodesign.thirdParty'),
            value: `${thirdParty.size}`
        }
    ];

    const passed = criteria.filter(c => c.ok).length;

    const result = {
        criteria,
        passed,
        total: criteria.length,
        stats: {
            totalBytes,
            requests,
            domNodes,
            fontFiles: fontFiles.length,
            thirdParty: thirdParty.size,
            legacyImages
        }
    };

    result.recommendations = buildEcoRecommendations(criteria, {belowFoldEager, legacyFonts,
        autoplay, legacyImages, oversized, missingDims, thirdParty: thirdParty.size,
        fontFiles: fontFiles.length, totalBytes, requests, domNodes}, rules);
    return result;
}

/** One finding for each criterion that fails, under the rule of the same name. */
function buildEcoRecommendations(criteria, x, rules) {
    const {recs, push} = findings(rules);
    const params = {
        pageWeight: {size: `${(x.totalBytes / KILOBYTE / KILOBYTE).toFixed(1)} MB`},
        requests: {count: x.requests},
        domSize: {count: x.domNodes},
        lazyLoading: {count: x.belowFoldEager},
        imageFormats: {count: x.legacyImages},
        oversizedImages: {count: x.oversized},
        imageDimensions: {count: x.missingDims},
        webFonts: {count: x.fontFiles, legacy: x.legacyFonts},
        autoplay: {count: x.autoplay},
        thirdParty: {count: x.thirdParty}
    };
    criteria.filter(c => !c.ok).forEach(c => push(`ecodesign.${c.key}`, c.key, params[c.key]));
    return recs;
}
