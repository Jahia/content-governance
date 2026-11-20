import {gql} from '@apollo/client';
import {limits} from './rules';
import {RULE_KEY} from '../drawer/findings';

/**
 * The page check service of the server for the Page quality tabs SEO, Readability, Links and Jahia
 * health: `checkPage` renders the page as the editor, in the edit workspace, runs every check of the
 * server on it, and measures the facts these tabs show. `frame.check` gives the same findings for the
 * same path. Each finding lands in one tab, under the id of its rule.
 */

export const PAGE_CHECK = gql`
    query ContentGovernanceCheckPage($path: String!, $language: String!) {
        contentGovernance {
            checkPage(path: $path, language: $language) {
                path
                findings {
                    ruleId
                    severity
                    text
                    context
                }
                facts
            }
        }
    }
`;

/** The tab of each domain of rules; a rule of another domain, such as a check of another module, goes to Jahia health. */
const DOMAIN_TABS = {seo: 'seo', readability: 'readability', links: 'links', health: 'jahia', html: 'accessibility'};

/** The tab of the rules that a tab of the drawer words itself, when it is not the tab of their domain. */
const RULE_TABS = {
    'html.pageLanguageMissing': 'seo',
    'html.pageLanguageMismatch': 'seo',
    'html.imageAltMissing': 'seo',
    'html.linkTextVague': 'seo',
    'html.headingH1Missing': 'readability',
    'html.headingH1Multiple': 'readability',
    'html.headingLevelSkipped': 'readability',
    'html.headingEmpty': 'readability'
};

const lixSuffix = facts => (facts.readability.scale === 'lix' ? 'Lix' : '');

/**
 * The text of each rule that a tab words itself: its key under `<tab>.recs` and its values. A rule
 * that is not here shows its id and its advice (`recs.rule`).
 */
const RULE_TEXTS = {
    'seo.title': facts => (facts.seo.title.length === 0 ? ['noTitle'] : ['titleLength', {length: facts.seo.title.length}]),
    'seo.metaDescription': facts => (facts.seo.description.length === 0 ?
        ['noDescription'] :
        ['descriptionLength', {length: facts.seo.description.length}]),
    'seo.indexable': facts => ['noindex', {content: facts.seo.robots.content}],
    'seo.followLinks': facts => ['nofollow', {content: facts.seo.robots.content}],
    'seo.canonical': () => ['noCanonical'],
    'seo.openGraph': () => ['incompleteOg'],
    'seo.structuredDataValid': (facts, items) => ['invalidStructuredData', {count: items.length}],
    'seo.structuredData': () => ['noStructuredData'],
    'html.pageLanguageMissing': () => ['noLang'],
    'html.pageLanguageMismatch': facts => ['langMismatch', {htmlLang: facts.seo.lang.htmlLang, expected: facts.seo.lang.expected}],
    'html.imageAltMissing': (facts, items) => ['missingAlt', {count: items.length}],
    'html.linkTextVague': (facts, items) => ['genericAnchors', {count: items.length}],
    'readability.veryHardText': facts => [`veryHardText${lixSuffix(facts)}`, {score: facts.readability.score}],
    'readability.hardText': facts => [`hardText${lixSuffix(facts)}`, {score: facts.readability.score}],
    'readability.longSentences': facts => ['longSentences', {count: facts.readability.longSentences}],
    'readability.longParagraphs': facts => ['longParagraphs', {count: facts.readability.avgWordsPerParagraph}],
    'readability.thinContent': facts => ['thinContent', {count: facts.readability.words}],
    'html.headingH1Missing': facts => ['h1Count', {count: facts.readability.h1Count}],
    'html.headingH1Multiple': facts => ['h1Count', {count: facts.readability.h1Count}],
    'html.headingLevelSkipped': (facts, items) => ['headingSkips', {count: items.length}],
    'links.broken': (facts, items) => ['brokenLinks', {count: items.length}],
    'links.hardcodedUrls': (facts, items) => ['hardcodedUrls', {count: items.length}],
    'links.mixedContent': (facts, items) => ['mixedContent', {count: items.length}],
    'links.blankNoopener': (facts, items) => ['blankNoopener', {count: items.length}],
    'health.rawI18nKeys': (facts, items) => ['rawI18nKeys', {count: items.length}],
    'health.unpublishedChanges': (facts, items) => ['unpublishedChanges', {count: items.length}],
    'health.missingTranslations': (facts, items) => ['missingTranslations', {
        count: items.length,
        languages: [...new Set(items.flatMap(item => (item.context || '').split(', ').filter(Boolean)))].join(', ')
    }],
    'health.placeholderText': (facts, items) => ['placeholderText', {count: items.length}]
};

/**
 * The tab that shows the findings of a rule.
 *
 * @param {string} ruleId the id of the rule
 * @param {object} [rule] the rule of the site, whose `domain` names the group of the rule
 * @returns {string} `seo`, `readability`, `links`, `jahia` or `accessibility`
 */
export function tabOf(ruleId, rule) {
    return RULE_TABS[ruleId] || DOMAIN_TABS[rule?.domain || ruleId.split('.')[0]] || 'jahia';
}

/**
 * The recommendations of the findings, one for each rule in the order of the rules, by tab: what the
 * tab shows, with each finding of the rule in `items`.
 *
 * @param {Array<{ruleId: string, severity: string, text: string, context: ?string}>} findings the findings of the service
 * @param {object} facts the facts of the service
 * @param {{find: function(string): ?object}} rules the rules of the site
 * @returns {Object<string, Array<{key: string, rule: string, severity: string, params: object, items: Array<object>, advice: ?string}>>}
 */
export function recommendationsByTab(findings, facts, rules) {
    const byRule = new Map();
    findings.forEach(finding => {
        const items = byRule.get(finding.ruleId) || [];
        items.push(finding.context ? {text: finding.text, context: finding.context} : {text: finding.text});
        byRule.set(finding.ruleId, items);
    });
    const severities = new Map(findings.map(finding => [finding.ruleId, finding.severity]));
    const out = {seo: [], readability: [], links: [], jahia: [], accessibility: []};
    byRule.forEach((items, ruleId) => {
        const rule = rules.find(ruleId);
        const known = RULE_TEXTS[ruleId];
        const [key, params] = known ? known(facts, items) : [RULE_KEY, {rule: ruleId, count: items.length}];
        out[tabOf(ruleId, rule)].push({
            key,
            rule: ruleId,
            severity: severities.get(ruleId) || rule?.severity || 'minor',
            params: {...(rule ? limits(rule) : {}), ...params},
            items,
            advice: known ? null : rule?.advice || null
        });
    });
    return out;
}

/** An address of the page as the preview resolves it, such as the image of the sharing card. */
const resolved = address => {
    try {
        return new URL(address, globalThis.location.href).href;
    } catch {
        return address;
    }
};

/** A path under the page, as the Jahia health tab lists it: `/main/intro`, or `/` for the page itself. */
const under = (path, pagePath) => (path.startsWith(pagePath) ? path.slice(pagePath.length) || '/' : path);

/**
 * The results of the tabs SEO, Readability, Links and Jahia health, and the recommendations of the
 * Accessibility tab, from one answer of the service.
 *
 * @param {{findings: Array<object>, facts: object}} check the answer of `checkPage`
 * @param {{get: function(string): object, find: function(string): ?object}} rules the rules of the site
 * @param {string} pagePath the path of the page
 * @returns {{seo: object, readability: object, links: object, jahia: object, accessibility: Array<object>}}
 */
export function tabResults({findings, facts}, rules, pagePath) {
    const recs = recommendationsByTab(findings, facts, rules);
    const itemsOf = ruleId => recs.jahia.find(rec => rec.rule === ruleId)?.items || [];
    const {seo, readability, links, jahia} = facts;
    return {
        seo: {
            ...seo,
            ogImageResolved: seo.og.image ? resolved(seo.og.image) : null,
            ranges: {title: limits(rules.get('seo.title')), description: limits(rules.get('seo.metaDescription'))},
            recommendations: recs.seo
        },
        readability: {...readability, recommendations: recs.readability},
        links: {...links, recommendations: recs.links},
        jahia: {
            pageStatus: jahia.pageStatus ? jahia.pageStatus.toUpperCase() : null,
            activeLanguages: jahia.activeLanguages || [],
            scanned: jahia.scanned || 0,
            unpublished: (jahia.unpublished || []).map(node => ({...node, path: under(node.path, pagePath), status: node.status.toUpperCase()})),
            untranslated: (jahia.untranslated || []).map(node => ({...node, path: under(node.path, pagePath)})),
            rawKeys: {count: itemsOf('health.rawI18nKeys').length, samples: itemsOf('health.rawI18nKeys').map(item => ({key: item.text, text: item.context || ''}))},
            placeholders: {count: itemsOf('health.placeholderText').length, samples: itemsOf('health.placeholderText').map(item => ({match: item.text, text: item.context || ''}))},
            recommendations: recs.jahia
        },
        accessibility: recs.accessibility
    };
}

/**
 * Checks the page with the service of the server.
 *
 * @param {object} client the Apollo client
 * @param {string} path the page
 * @param {string} language the content language
 * @param {{get: function(string): object, find: function(string): ?object}} rules the rules of the site
 * @returns {Promise<object>} the results of `tabResults`
 */
export async function checkPage(client, path, language, rules) {
    const {data} = await client.query({query: PAGE_CHECK, variables: {path, language}, fetchPolicy: 'no-cache'});
    const check = data.contentGovernance.checkPage;
    return tabResults({findings: check.findings, facts: JSON.parse(check.facts)}, rules, check.path);
}
