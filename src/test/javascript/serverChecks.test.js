import {describe, expect, it} from 'vitest';
import {serverResults} from '../../javascript/audit/serverChecks.js';
import {BASE_RULES} from './baseRules.js';

const PATH = '/sites/digitall/home';

const FACTS = {
    seo: {
        title: {text: '', length: 0},
        description: {text: 'A description', length: 13},
        robots: {content: null, noindex: false, nofollow: false},
        canonical: null,
        og: {title: null, description: null, image: '/files/og.png', type: null},
        twitterCard: null,
        jsonLd: {count: 0, errors: 0},
        lang: {htmlLang: 'en', expected: 'en'},
        hreflang: 0,
        totalImages: 2,
        imagesWithoutAlt: 1
    },
    readability: {lang: 'en', spaced: true, empty: false, scored: true, scale: 'lix', score: 58, band: 'veryDifficult', words: 120, h1Count: 0},
    links: {total: 3, internal: 2, external: 1, broken: [{path: '/sites/digitall/old', reason: 'missing'}], hardcodedHost: 0, mixedContent: 0, blankNoopener: 0},
    jahia: {
        scanned: 3,
        activeLanguages: ['en', 'fr'],
        unpublished: [{path: PATH, status: 'modified'}, {path: `${PATH}/main/intro`, status: 'not_published'}],
        untranslated: [{path: `${PATH}/main/intro`, missing: ['fr']}],
        rawKeys: {count: 0, samples: []},
        placeholders: {count: 0, samples: []}
    }
};

const finding = (ruleId, severity, text, context = null) => ({ruleId, severity, text, context});

const results = findings => serverResults({findings, facts: JSON.stringify(FACTS)},
    {rules: BASE_RULES, path: PATH, pageUrl: 'http://localhost:8080/cms/render/default/en/sites/digitall/home.html'});

describe('the results of the check of the page on the server', () => {
    it('gives each finding of a worded rule to its tab, once per rule', () => {
        const r = results([
            finding('seo.title', 'serious', '0'),
            finding('html.imageAltMissing', 'critical', '<img src="a.png">'),
            finding('readability.veryHardText', 'serious', '58', 'LIX'),
            finding('html.headingH1Missing', 'moderate', '<body>'),
            finding('links.broken', 'critical', '/sites/digitall/old', 'missing'),
            finding('health.missingTranslations', 'serious', `${PATH}/main/intro`, 'fr'),
            finding('health.missingTranslations', 'serious', `${PATH}/main/other`, 'de, fr')
        ]);

        expect(r.seo.recommendations).toEqual([
            {key: 'noTitle', rule: 'seo.title', severity: 'serious', params: {min: 30, max: 60, length: 0}},
            {key: 'missingAlt', rule: 'html.imageAltMissing', severity: 'critical', params: {count: 1}}
        ]);
        expect(r.readability.recommendations.map(rec => [rec.rule, rec.key])).toEqual([
            ['readability.veryHardText', 'veryHardTextLix'], ['html.headingH1Missing', 'h1Count']
        ]);
        expect(r.links.recommendations).toEqual([{key: 'brokenLinks', rule: 'links.broken', severity: 'critical', params: {count: 1}}]);
        expect(r.jahia.recommendations[0].params).toEqual({count: 2, languages: 'fr, de'});
    });

    it('lists another rule under the advice of the rule, in the tab of its domain', () => {
        const r = results([
            finding('html.formLabelMissing', 'critical', '<input name="q">'),
            finding('html.formLabelMissing', 'critical', '<input name="r">'),
            finding('brand.tone', 'minor', 'Buy now')
        ]);

        expect(r.a11y).toEqual([{
            key: 'html.formLabelMissing', rule: 'html.formLabelMissing', severity: 'critical', advice: 'html.formLabelMissing',
            params: {count: 2, samples: '<input name="q"> · <input name="r">'}
        }]);
        expect(r.jahia.recommendations.map(rec => rec.rule)).toEqual(['brand.tone']);
    });

    it('keeps the facts that the tabs show, with the paths under the page', () => {
        const r = results([]);

        expect(r.seo.ogImageResolved).toBe('http://localhost:8080/files/og.png');
        expect(r.seo.ranges).toEqual({title: {min: 30, max: 60}, description: {min: 50, max: 160}});
        expect(r.links.broken).toEqual(FACTS.links.broken);
        expect(r.jahia.unpublished).toEqual([{path: '/', status: 'MODIFIED'}, {path: '/main/intro', status: 'NOT_PUBLISHED'}]);
        expect(r.jahia.untranslated).toEqual([{path: '/main/intro', missing: ['fr']}]);
        expect(r.seo.recommendations).toEqual([]);
    });
});
