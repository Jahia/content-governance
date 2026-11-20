import {describe, expect, it} from 'vitest';
import {recommendationsByTab, tabOf, tabResults} from '../../javascript/audit/pageCheck.js';
import {auditFindings, recommendationText} from '../../javascript/drawer/findings.js';
import {BASE_RULES} from './baseRules.js';

const t = (key, params) => `${key}${params ? JSON.stringify(params) : ''}`;

/** The facts of a page, as checkPage gives them, with what a test changes. */
const facts = change => ({
    seo: {
        title: {text: 'Home', length: 4},
        description: {text: null, length: 0},
        robots: {content: null, noindex: false, nofollow: false},
        canonical: null,
        og: {title: null, description: null, image: '/files/card.png', type: null},
        twitterCard: null,
        jsonLd: {count: 0, errors: 0},
        lang: {htmlLang: 'fr', expected: 'en'},
        hreflang: 0,
        totalImages: 2,
        imagesWithoutAlt: 1,
        weakCtas: []
    },
    readability: {lang: 'en', spaced: true, empty: false, scored: true, scale: 'flesch', score: 42, band: 'difficult',
        words: 120, longSentences: 3, avgWordsPerParagraph: 90, h1Count: 2, outline: []},
    links: {total: 3, internal: 2, external: 1, checked: 2, skipped: 0, broken: [{path: '/sites/digitall/gone', reason: 'missing'}]},
    jahia: {
        pageStatus: 'modified',
        activeLanguages: ['en', 'fr'],
        scanned: 4,
        unpublished: [{path: '/sites/digitall/home', type: 'jnt:page', status: 'modified'},
            {path: '/sites/digitall/home/main/intro', type: 'jnt:text', status: 'not_published'}],
        untranslated: [{path: '/sites/digitall/home/main/intro', type: 'jnt:text', missing: ['fr']}]
    },
    ...change
});

const finding = (ruleId, text, context, severity = 'moderate') => ({ruleId, severity, text, context: context || null});

describe('tabOf', () => {
    it('puts a rule in the tab of its domain', () => {
        expect(tabOf('seo.canonical')).toBe('seo');
        expect(tabOf('links.broken')).toBe('links');
        expect(tabOf('health.placeholderText')).toBe('jahia');
        expect(tabOf('html.buttonNameMissing')).toBe('accessibility');
    });

    it('keeps the HTML rules that SEO and Readability word in those tabs', () => {
        expect(tabOf('html.imageAltMissing')).toBe('seo');
        expect(tabOf('html.headingLevelSkipped')).toBe('readability');
    });

    it('puts a rule of another domain, such as a check of another module, in Jahia health', () => {
        expect(tabOf('acme.erpPrice', {domain: 'acme'})).toBe('jahia');
        expect(tabOf('voice.tone', {domain: 'seo'})).toBe('seo');
    });
});

describe('recommendationsByTab', () => {
    it('gives one recommendation per rule, with each finding of the rule, in the order of the rules', () => {
        const recs = recommendationsByTab([
            finding('links.mixedContent', 'http://example.com/a'),
            finding('links.broken', '/sites/digitall/gone', 'missing', 'critical'),
            finding('links.mixedContent', 'http://example.com/b')
        ], facts(), BASE_RULES);

        expect(recs.links.map(rec => rec.rule)).toEqual(['links.mixedContent', 'links.broken']);
        expect(recs.links[0]).toMatchObject({key: 'mixedContent', params: {count: 2}, severity: 'moderate',
            items: [{text: 'http://example.com/a'}, {text: 'http://example.com/b'}]});
        expect(recs.links[1]).toMatchObject({key: 'brokenLinks', severity: 'critical',
            items: [{text: '/sites/digitall/gone', context: 'missing'}]});
    });

    it('words a known rule with the facts of the page and the limits of the rule', () => {
        const recs = recommendationsByTab([finding('seo.title', '4'), finding('seo.metaDescription', '0'),
            finding('html.pageLanguageMismatch', '<html lang="fr">')], facts(), BASE_RULES);

        expect(recs.seo[0]).toMatchObject({key: 'titleLength', params: {length: 4, min: 30, max: 60}});
        expect(recs.seo[1]).toMatchObject({key: 'noDescription'});
        expect(recs.seo[2]).toMatchObject({key: 'langMismatch', params: {htmlLang: 'fr', expected: 'en'}});
    });

    it('words a readability rule on the scale of the score', () => {
        const flesch = recommendationsByTab([finding('readability.hardText', '42')], facts(), BASE_RULES);
        const lix = recommendationsByTab([finding('readability.hardText', '50')],
            facts({readability: {...facts().readability, scale: 'lix', score: 50}}), BASE_RULES);

        expect(flesch.readability[0]).toMatchObject({key: 'hardText', params: {score: 42}});
        expect(lix.readability[0]).toMatchObject({key: 'hardTextLix', params: {score: 50}});
    });

    it('names the languages that the content lacks', () => {
        const recs = recommendationsByTab([finding('health.missingTranslations', '/a', 'de, fr'),
            finding('health.missingTranslations', '/b', 'fr')], facts(), BASE_RULES);

        expect(recs.jahia[0].params).toMatchObject({count: 2, languages: 'de, fr'});
    });

    it('shows a rule that no tab words with its id and its advice', () => {
        const recs = recommendationsByTab([finding('html.buttonNameMissing', '<button></button>', null, 'serious')],
            facts(), BASE_RULES);

        expect(recs.accessibility[0]).toMatchObject({key: 'rule', rule: 'html.buttonNameMissing', severity: 'serious',
            params: {rule: 'html.buttonNameMissing', count: 1}});
        expect(recommendationText(recs.accessibility[0], 'a11y', t))
            .toEqual({title: 'recs.rule{"rule":"html.buttonNameMissing","count":1}', detail: ''});
        expect(recommendationText({...recs.accessibility[0], advice: 'Name the button.'}, 'a11y', t).detail)
            .toBe('Name the button.');
    });
});

describe('tabResults', () => {
    it('gives each tab its facts and its recommendations', () => {
        const results = tabResults({findings: [finding('seo.canonical', '<head>'), finding('html.duplicateId', '<div id="x">')],
            facts: facts()}, BASE_RULES, '/sites/digitall/home');

        expect(results.seo).toMatchObject({title: {text: 'Home'}, imagesWithoutAlt: 1,
            ranges: {title: {min: 30, max: 60}}, recommendations: [{key: 'noCanonical'}]});
        expect(results.seo.ogImageResolved).toMatch(/\/files\/card\.png$/);
        expect(results.readability).toMatchObject({score: 42, recommendations: []});
        expect(results.links).toMatchObject({broken: [{path: '/sites/digitall/gone', reason: 'missing'}]});
        expect(results.accessibility.map(rec => rec.rule)).toEqual(['html.duplicateId']);
    });

    it('resolves a relative image of the sharing card against the address of the page', () => {
        const page = facts();
        const results = tabResults({findings: [], facts: {...page, seo: {...page.seo, og: {...page.seo.og, image: 'images/card.png'}}}},
            BASE_RULES, '/sites/digitall/home', 'http://jahia.test/cms/render/default/en/sites/digitall/home.html');

        expect(results.seo.ogImageResolved).toBe('http://jahia.test/cms/render/default/en/sites/digitall/images/card.png');
    });

    it('names the tabs whose state of the content the server could not read', () => {
        expect(tabResults({findings: [], facts: facts()}, BASE_RULES, '/sites/digitall/home').unread).toEqual([]);
        const unread = tabResults({findings: [], facts: {...facts(), links: {...facts().links, checked: null}, jahia: {}}},
            BASE_RULES, '/sites/digitall/home').unread;
        expect(unread).toEqual(['links', 'jahia']);
    });

    it('lists the content of the page under its path, with the status of the drawer', () => {
        const results = tabResults({findings: [finding('health.rawI18nKeys', 'jnt:text.title', 'jnt:text.title here'),
            finding('health.placeholderText', 'lorem ipsum', 'Lorem ipsum dolor')], facts: facts()}, BASE_RULES,
        '/sites/digitall/home');

        expect(results.jahia).toMatchObject({
            pageStatus: 'MODIFIED',
            scanned: 4,
            unpublished: [{path: '/', type: 'jnt:page', status: 'MODIFIED'}, {path: '/main/intro', status: 'NOT_PUBLISHED'}],
            untranslated: [{path: '/main/intro', missing: ['fr']}],
            rawKeys: {count: 1, samples: [{key: 'jnt:text.title', text: 'jnt:text.title here'}]},
            placeholders: {count: 1, samples: [{match: 'lorem ipsum', text: 'Lorem ipsum dolor'}]}
        });
    });

    it('gives the Fix first list of the drawer each rule of the server once, with its count', () => {
        const results = tabResults({findings: [finding('links.mixedContent', 'http://a'), finding('links.mixedContent', 'http://b'),
            finding('html.buttonNameMissing', '<button></button>', null, 'serious')], facts: facts()}, BASE_RULES, '/sites/digitall/home');
        const found = auditFindings({links: results.links, a11y: {violations: [], recommendations: results.accessibility}}, t);

        expect(found.map(item => [item.rule, item.tab, item.count])).toEqual([
            ['html.buttonNameMissing', 'accessibility', 1],
            ['links.mixedContent', 'links', 2]
        ]);
        expect(found[0].key).toBe('audit.a11y.html.buttonNameMissing');
    });
});
