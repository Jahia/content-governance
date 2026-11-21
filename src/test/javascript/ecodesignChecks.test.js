import {describe, expect, it} from 'vitest';
import {MEASURES} from '../../javascript/checks/ecodesign.js';
import {BASE_RULES, rulesWith} from './baseRules.js';

const KILOBYTE = 1024;

/** An element of the preview with an id, which cssPath names `#<id>`. */
const element = (id, fields = {}) => ({nodeType: 1, tagName: 'IMG', id, ownerDocument: {body: null, defaultView: null}, ...fields});

/** What pageMeasure gives for a page that breaks no rule. */
const page = (change = {}) => ({
    nav: null,
    resources: [],
    totalBytes: 200 * KILOBYTE,
    requests: 10,
    byType: {},
    largest: [],
    domNodes: 300,
    images: [],
    missingDims: [],
    oversized: [],
    belowFold: [],
    belowFoldEager: [],
    autoplay: [],
    fonts: [],
    legacyFonts: [],
    legacyImages: [],
    thirdParty: [],
    ...change
});

const apply = (evaluator, measured, rules = BASE_RULES) => MEASURES[evaluator](measured, rules.get(evaluator));

describe('the eco-design checks', () => {
    it('pass on a light page, each with its RGESN family and its value for the list of criteria', () => {
        const results = Object.keys(MEASURES).map(evaluator => [evaluator, apply(evaluator, page())]);

        expect(results.filter(([, result]) => !result.passed)).toEqual([]);
        expect(results.every(([, result]) => result.values.family && result.findings.length === 0)).toBe(true);
        expect(apply('ecodesign.pageWeight', page()).values.display).toBe('0.20 MB');
    });

    it('apply the limit of the rule, so a site that raises it passes', () => {
        const heavy = page({totalBytes: 3 * KILOBYTE * KILOBYTE, largest: [{name: 'hero.jpg', bytes: 2100 * KILOBYTE}]});

        expect(apply('ecodesign.pageWeight', heavy)).toEqual({passed: false,
            values: {family: 'frontend', bytes: 3 * KILOBYTE * KILOBYTE, size: '3.0 MB', display: '3.00 MB'},
            findings: [{text: 'hero.jpg', context: '2100 KB'}]});
        expect(apply('ecodesign.pageWeight', heavy, rulesWith('ecodesign.pageWeight', {maximum: 4096})).passed).toBe(true);
        expect(apply('ecodesign.domSize', page({domNodes: 2000})).values.count).toBe(2000);
        expect(apply('ecodesign.domSize', page({domNodes: 2000})).passed).toBe(false);
    });

    it('give one finding per element, with its locator in the preview', () => {
        const result = apply('ecodesign.imageDimensions', page({missingDims: [element('hero', {src: 'https://example.com/img/hero.png'})]}));

        expect(result).toEqual({passed: false, values: {family: 'contents', count: 1, display: '1'},
            findings: [{text: 'hero.png', locator: '#hero'}]});
    });

    it('fail on a legacy font file, inside the limit of font files', () => {
        const fonts = [{name: 'https://example.com/a.woff2', transferSize: 20 * KILOBYTE}, {name: 'https://example.com/b.ttf', transferSize: 40 * KILOBYTE}];
        const result = apply('ecodesign.webFonts', page({fonts, legacyFonts: [fonts[1]]}));

        expect(result.passed).toBe(false);
        expect(result.values).toEqual({family: 'frontend', count: 2, legacy: 1, display: '2 (1 legacy)'});
        expect(result.findings).toEqual([{text: 'a.woff2', context: '20 KB'}, {text: 'b.ttf', context: '40 KB'}]);
    });

    it('name the heaviest script over the limit', () => {
        const resources = [
            {name: 'https://example.com/js/vendor.js', initiatorType: 'script', transferSize: 900 * KILOBYTE},
            {name: 'https://example.com/js/app.js', initiatorType: 'script', transferSize: 120 * KILOBYTE},
            {name: 'https://example.com/img/hero.jpg', initiatorType: 'img', transferSize: 2000 * KILOBYTE}
        ];
        const result = apply('ecodesign.heavyAssets', page({resources}));

        expect(result.passed).toBe(false);
        expect(result.values.name).toBe('vendor.js');
        expect(result.findings).toEqual([{text: 'vendor.js', context: '900 KB'}]);
    });
});
