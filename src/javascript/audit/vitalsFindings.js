/**
 * The findings on LCP, CLS and TTFB that audits stored before 1.11 carry. The timings are measured
 * on the editing preview, in the editor's browser: they cannot match the live site (no cache, no
 * CDN, an editor's session), so they are shown as indicative figures and are no finding any more.
 */
const TIMING_FINDINGS = new Set(['slowLcp', 'highCls', 'slowTtfb']);

/**
 * @param {Array<{key: string}>} [recommendations] the Web Vitals recommendations of an audit
 * @returns {Array<{key: string}>} the same without the findings on timings
 */
export const withoutTimingFindings = recommendations => (recommendations || []).filter(rec => !TIMING_FINDINGS.has(rec.key));
