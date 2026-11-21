/**
 * The web vitals of the preview, measured in the frame from the buffered PerformanceObserver entries, so it
 * runs after the page finished loading. Chrome emits no paint timing (FCP) or LCP entry for a document in a
 * frame, so LCP falls back to the largest image of the first screen, flagged `lcpApprox`. INP needs a real
 * interaction and is null.
 *
 * The evaluator `webVitals` applies a measure rule, such as `ecodesign.webVitals`: it always passes, and its
 * `values` hold the figures of the Web Vitals tab. The timings come from the editing preview, in the editor's
 * browser, so they cannot match the live site: they are indicative figures, never a finding.
 */

import {measurePage} from './pageMeasure';

const OBSERVE_WINDOW_MS = 700;

function observeBuffered(win, type) {
    return new Promise(resolve => {
        const entries = [];
        let observer;
        try {
            observer = new win.PerformanceObserver(list => entries.push(...list.getEntries()));
            observer.observe({type, buffered: true});
        } catch {
            resolve(entries);
            return;
        }
        setTimeout(() => {
            try {
                observer.disconnect();
            } catch {
                // The frame may be gone meanwhile
            }
            resolve(entries);
        }, OBSERVE_WINDOW_MS);
    });
}

/** The LCP approximation of a frame: when the largest image of the first screen finished loading, else DOMContentLoaded. */
function approximateLcp(win, doc, page) {
    const width = win.innerWidth;
    const height = win.innerHeight;
    let best = null;
    Array.from(doc.images || []).forEach(img => {
        const rect = img.getBoundingClientRect();
        if (rect.width * rect.height === 0 || rect.top >= height || rect.bottom <= 0 || rect.left >= width) {
            return;
        }
        const area = Math.min(rect.width, width) * Math.min(rect.height, height);
        if (!best || area > best.area) {
            best = {area, src: img.currentSrc || img.src};
        }
    });
    const resource = best && page.resources.find(entry => entry.name === best.src);
    if (resource) {
        return resource.responseEnd;
    }
    return page.nav?.domContentLoadedEventEnd || null;
}

/**
 * The timings of the preview and what it loaded.
 *
 * @param {Window} win the window of the preview
 * @param {Document} doc its document
 * @returns {Promise<{metrics: object, diagnostics: object}>} the figures of the Web Vitals tab
 */
export async function measureVitals(win, doc) {
    const page = measurePage(win, doc);
    const [lcpEntries, shiftEntries] = await Promise.all([
        observeBuffered(win, 'largest-contentful-paint'),
        observeBuffered(win, 'layout-shift')
    ]);
    const lcpEntry = lcpEntries.at(-1);
    const measuredLcp = lcpEntry ? (lcpEntry.renderTime || lcpEntry.loadTime) : null;
    const lcp = measuredLcp ?? approximateLcp(win, doc, page);
    const {nav} = page;
    return {
        metrics: {
            ttfb: nav ? nav.responseStart : null,
            dcl: nav?.domContentLoadedEventEnd || null,
            load: nav?.loadEventEnd || null,
            lcp,
            lcpApprox: measuredLcp === null && lcp !== null,
            cls: shiftEntries.filter(entry => !entry.hadRecentInput).reduce((sum, entry) => sum + entry.value, 0),
            inp: null
        },
        diagnostics: {
            requests: page.requests,
            totalBytes: page.totalBytes,
            byType: page.byType,
            largest: page.largest,
            domNodes: page.domNodes,
            images: page.images.length,
            missingDims: page.missingDims.length,
            oversized: page.oversized.length,
            thirdParty: page.thirdParty.length
        }
    };
}

/**
 * Runs the check `webVitals` on the preview: each rule passes, with the figures as its values.
 *
 * @param {{document: Document, window: Window, rules: Array<object>}} context what the drawer gives a check
 * @returns {Promise<Array<object>>} one result per rule
 */
export async function runWebVitals({document, window, rules}) {
    const values = await measureVitals(window, document);
    return rules.map(rule => ({ruleId: rule.id, passed: true, values, findings: []}));
}

