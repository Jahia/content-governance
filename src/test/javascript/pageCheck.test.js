import {describe, expect, it} from 'vitest';
import {recommendationsByTab, tabOf, tabResults} from '../../javascript/audit/pageCheck.js';
import {auditFindings, recommendationText} from '../../javascript/drawer/findings.js';
import {BASE_RULES, rulesIn} from './baseRules.js';
import {ruleBook} from '../../javascript/audit/rules.js';

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
        imagesWithoutAlt: 1
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
    it('puts a rule in the tab of its subdomain, and nowhere else', () => {
        expect(tabOf(BASE_RULES.get('seo.canonical'))).toBe('seo');
        expect(tabOf(BASE_RULES.get('links.broken'))).toBe('links');
        expect(tabOf(BASE_RULES.get('health.placeholderText'))).toBe('jahia');
        expect(tabOf(BASE_RULES.get('html.buttonNameMissing'))).toBe('accessibility');
    });

    it('puts every rule of the HTML check in the Accessibility tab', () => {
        expect(tabOf(BASE_RULES.get('html.imageAltMissing'))).toBe('accessibility');
        expect(tabOf(BASE_RULES.get('html.headingLevelSkipped'))).toBe('accessibility');
        expect(tabOf(BASE_RULES.get('html.linkTextVague'))).toBe('accessibility');
    });

    it('puts a rule of another module in the tab of its subdomain, and a rule of unknown subdomains in none', () => {
        expect(tabOf({id: 'acme.erpPrice', subdomains: ['writing']})).toBe('readability');
        expect(tabOf({id: 'voice.tone', subdomains: ['seo']})).toBe('seo');
        expect(tabOf({id: 'brand.tone', subdomains: ['brand']})).toBeNull();
        expect(tabOf({id: 'brand.none'})).toBeNull();
        expect(tabOf(BASE_RULES.get('geo.reachable'))).toBeNull();
    });

    it('puts a rule of two subdomains in the tab of the first that has one', () => {
        expect(BASE_RULES.get('seo.title').subdomains).toEqual(['seo', 'geo.score']);
        expect(tabOf(BASE_RULES.get('seo.title'))).toBe('seo');
        expect(tabOf({id: 'acme.score', subdomains: ['geo.score', 'accessibility']})).toBe('accessibility');
    });
});

describe('recommendationsByTab', () => {
    it('gives one recommendation per rule, with each finding of the rule, in the order of the rules', () => {
        const recs = recommendationsByTab([
            finding('links.mixedContent', 'http://example.com/a'),
            finding('links.broken', '/sites/digitall/gone', 'missing', 'critical'),
            finding('links.mixedContent', 'http://example.com/b')
        ], BASE_RULES);

        expect(recs.links.map(rec => rec.rule)).toEqual(['links.mixedContent', 'links.broken']);
        expect(recs.links[0]).toMatchObject({key: 'links.mixedContent', params: {count: 2}, severity: 'moderate',
            title: '2 links use http://', items: [{text: 'http://example.com/a'}, {text: 'http://example.com/b'}]});
        expect(recs.links[1]).toMatchObject({key: 'links.broken', severity: 'critical', title: '1 link is broken',
            items: [{text: '/sites/digitall/gone', context: 'missing'}]});
    });

    it('words every rule from the rule: its title, its values and its limits', () => {
        const recs = recommendationsByTab([finding('seo.title', '72'), finding('seo.metaDescription', '0')], BASE_RULES);

        expect(recs.seo[0]).toMatchObject({title: 'The title has 72 characters (keep 30 to 60)', label: 'Title length',
            detail: 'Give the page a title, with the main subject first, inside the length range.',
            params: {value: '72', min: 30, max: 60}});
        expect(recs.seo[1].title).toBe('The meta description has 0 characters (keep 50 to 160)');
    });

    it('takes the plural form of the count in the language of the texts', () => {
        const one = [finding('html.imageAltMissing', '<img src="a.png">')];
        const two = [...one, finding('html.imageAltMissing', '<img src="b.png">')];

        expect(recommendationsByTab(one, BASE_RULES).accessibility[0].title).toBe('1 image has no text alternative');
        expect(recommendationsByTab(two, BASE_RULES).accessibility[0].title).toBe('2 images have no text alternative');
        expect(recommendationsByTab(two, rulesIn('fr')).accessibility[0]).toMatchObject({
            title: '2 images sans alternative textuelle', label: 'Alternatives textuelles des images',
            detail: expect.stringContaining('attribut alt')});
    });

    it('words a rule of another module the same way, and its id when it has no text', () => {
        const rules = ruleBook([...BASE_RULES.all(), {id: 'acme.erpPrice', subdomains: ['writing'], severity: 'serious',
            title: [{form: 'one', text: '{{count}} price typed, such as {{value}}'}], advice: 'Insert the product.'},
        {id: 'acme.bare', subdomains: ['health'], severity: 'minor', title: [], advice: ''}]);
        const recs = recommendationsByTab([finding('acme.erpPrice', '12.50 EUR'), finding('acme.bare', 'x')], rules);

        expect(recs.readability[0]).toMatchObject({title: '1 price typed, such as 12.50 EUR', detail: 'Insert the product.'});
        expect(recs.jahia[0]).toMatchObject({title: 'acme.bare', label: 'acme.bare'});
    });

    it('keeps the locator of a finding, and drops a rule that no tab shows', () => {
        const recs = recommendationsByTab([{...finding('html.imageAltMissing', '<img>'), locator: 'main > img'},
            finding('geo.reachable', 'GPTBot')], BASE_RULES);

        expect(recs.accessibility[0].items).toEqual([{text: '<img>', locator: 'main > img'}]);
        expect(Object.values(recs).flat().map(rec => rec.rule)).toEqual(['html.imageAltMissing']);
    });

    it('shows the old text of an audit stored before the rules worded it', () => {
        expect(recommendationText({key: 'titleLength', params: {length: 72}}, 'seo', t))
            .toEqual({title: 'seo.recs.titleLength.title{"length":72}', detail: 'seo.recs.titleLength.detail{"length":72}'});
        expect(recommendationText({key: 'rule', params: {rule: 'acme.x', count: 1}, advice: 'Do it.'}, 'a11y', t))
            .toEqual({title: 'recs.rule{"rule":"acme.x","count":1}', detail: 'Do it.'});
    });
});

describe('tabResults', () => {
    it('gives each tab its facts and its recommendations', () => {
        const results = tabResults({findings: [finding('seo.canonical', '<head>'), finding('html.duplicateId', '<div id="x">')],
            facts: facts()}, BASE_RULES, '/sites/digitall/home');

        expect(results.seo).toMatchObject({title: {text: 'Home'}, imagesWithoutAlt: 1,
            ranges: {title: {min: 30, max: 60}}, recommendations: [{key: 'seo.canonical', title: 'The page declares no canonical URL'}]});
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
