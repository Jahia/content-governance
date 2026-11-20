import {gql} from '@apollo/client';
import {limits} from './rules';

export const CHECK_PAGE = gql`
    query ContentGovernanceCheckPage($path: String!, $language: String!) {
        contentGovernance {
            checkPage(path: $path, language: $language) {
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

const count = (facts, hits) => ({count: hits.length});

/**
 * The finding of a rule that a tab words itself: the tab, the key of its text under `<ns>.recs`, and its
 * params, from the facts of the server and the places it found.
 */
const WORDED = {
    'seo.title': {tab: 'seo', key: f => (f.seo.title.length === 0 ? 'noTitle' : 'titleLength'), params: f => ({length: f.seo.title.length})},
    'seo.metaDescription': {tab: 'seo', key: f => (f.seo.description.length === 0 ? 'noDescription' : 'descriptionLength'), params: f => ({length: f.seo.description.length})},
    'seo.indexable': {tab: 'seo', key: () => 'noindex', params: f => ({content: f.seo.robots.content})},
    'seo.followLinks': {tab: 'seo', key: () => 'nofollow', params: f => ({content: f.seo.robots.content})},
    'seo.canonical': {tab: 'seo', key: () => 'noCanonical'},
    'seo.openGraph': {tab: 'seo', key: () => 'incompleteOg'},
    'seo.structuredDataValid': {tab: 'seo', key: () => 'invalidStructuredData', params: count},
    'seo.structuredData': {tab: 'seo', key: () => 'noStructuredData'},
    'html.pageLanguageMissing': {tab: 'seo', key: () => 'noLang'},
    'html.pageLanguageMismatch': {tab: 'seo', key: () => 'langMismatch', params: f => ({htmlLang: f.seo.lang.htmlLang, expected: f.seo.lang.expected})},
    'html.imageAltMissing': {tab: 'seo', key: () => 'missingAlt', params: count},
    'html.linkTextVague': {tab: 'seo', key: () => 'genericAnchors', params: count},
    'readability.veryHardText': {tab: 'readability', key: f => (f.readability.scale === 'lix' ? 'veryHardTextLix' : 'veryHardText'), params: f => ({score: f.readability.score})},
    'readability.hardText': {tab: 'readability', key: f => (f.readability.scale === 'lix' ? 'hardTextLix' : 'hardText'), params: f => ({score: f.readability.score})},
    'readability.longSentences': {tab: 'readability', key: () => 'longSentences', params: f => ({count: f.readability.longSentences})},
    'readability.longParagraphs': {tab: 'readability', key: () => 'longParagraphs', params: f => ({count: f.readability.avgWordsPerParagraph})},
    'readability.thinContent': {tab: 'readability', key: () => 'thinContent', params: f => ({count: f.readability.words})},
    'html.headingH1Missing': {tab: 'readability', key: () => 'h1Count', params: f => ({count: f.readability.h1Count})},
    'html.headingH1Multiple': {tab: 'readability', key: () => 'h1Count', params: f => ({count: f.readability.h1Count})},
    'html.headingLevelSkipped': {tab: 'readability', key: () => 'headingSkips', params: count},
    'links.broken': {tab: 'links', key: () => 'brokenLinks', params: count},
    'links.hardcodedUrls': {tab: 'links', key: () => 'hardcodedUrls', params: count},
    'links.mixedContent': {tab: 'links', key: () => 'mixedContent', params: count},
    'links.blankNoopener': {tab: 'links', key: () => 'blankNoopener', params: count},
    'health.rawI18nKeys': {tab: 'jahia', key: () => 'rawI18nKeys', params: count},
    'health.unpublishedChanges': {tab: 'jahia', key: () => 'unpublishedChanges', params: count},
    'health.missingTranslations': {tab: 'jahia', key: () => 'missingTranslations', params: (f, hits) => ({count: hits.length, languages: [...new Set(hits.flatMap(hit => (hit.context || '').split(', ')))].join(', ')})},
    'health.placeholderText': {tab: 'jahia', key: () => 'placeholderText', params: count}
};

/** The tab that lists the findings of another rule, by the domain of the rule; any other domain goes to Jahia. */
const DOMAIN_TABS = {seo: 'seo', readability: 'readability', links: 'links', health: 'jahia', html: 'a11y'};

/** The places of a finding a rule shows, beside the advice of the rule. */
const MAX_SAMPLES = 3;

function ruleOf(rules, ruleId) {
    try {
        return rules.get(ruleId);
    } catch {
        return {id: ruleId, minimum: null, maximum: null};
    }
}

/**
 * The recommendation of one rule, from the places where the server found that the page breaks it.
 *
 * @returns {{tab: string, rec: {key: string, rule: string, severity: string, params: object, advice: (string|undefined)}}}
 *     a rule with no wording of its own shows its advice, and `key` is then its id
 */
function recommendation(ruleId, hits, facts, rules) {
    const rule = ruleOf(rules, ruleId);
    const worded = WORDED[ruleId];
    if (worded) {
        return {
            tab: worded.tab,
            rec: {key: worded.key(facts), rule: ruleId, severity: hits[0].severity, params: {...limits(rule), ...(worded.params ? worded.params(facts, hits) : {})}}
        };
    }
    const domain = rule.domain || ruleId.split('.')[0];
    return {
        tab: DOMAIN_TABS[domain] || 'jahia',
        rec: {
            key: ruleId,
            rule: ruleId,
            severity: hits[0].severity,
            advice: rule.advice || ruleId,
            params: {count: hits.length, samples: hits.slice(0, MAX_SAMPLES).map(hit => hit.text).join(' · ')}
        }
    };
}

/** A path under the page, from its root: `/` for the page itself. */
const under = (path, page) => (path.startsWith(page) ? path.slice(page.length) || '/' : path);

/**
 * The results of the tabs SEO, readability, links and Jahia health, and the findings of the other rules
 * of accessibility, from the check of the page on the server.
 *
 * @param {{findings: Array<{ruleId: string, severity: string, text: string, context: ?string}>, facts: string}} check
 *     what `checkPage` answered
 * @param {{rules: {get: function(string): object}, path: string, pageUrl: string}} options the rules of the
 *     site, the path of the page and its address, for the Open Graph image
 * @returns {{seo: object, readability: object, links: object, jahia: object, a11y: Array<object>}} each tab
 *     has its facts and `recommendations`; `a11y` holds recommendations only
 */
export function serverResults(check, {rules, path, pageUrl}) {
    const facts = JSON.parse(check.facts);
    const byRule = new Map();
    check.findings.forEach(finding => {
        byRule.set(finding.ruleId, [...(byRule.get(finding.ruleId) || []), finding]);
    });
    const recs = {seo: [], readability: [], links: [], jahia: [], a11y: []};
    byRule.forEach((hits, ruleId) => {
        const {tab, rec} = recommendation(ruleId, hits, facts, rules);
        recs[tab].push(rec);
    });

    let ogImageResolved = null;
    if (facts.seo?.og.image) {
        try {
            ogImageResolved = new URL(facts.seo.og.image, pageUrl).href;
        } catch {
            ogImageResolved = facts.seo.og.image;
        }
    }
    return {
        seo: facts.seo && {
            ...facts.seo,
            ogImageResolved,
            ranges: {title: limits(ruleOf(rules, 'seo.title')), description: limits(ruleOf(rules, 'seo.metaDescription'))},
            recommendations: recs.seo
        },
        readability: {...facts.readability, recommendations: recs.readability},
        links: {...facts.links, recommendations: recs.links},
        jahia: {
            ...facts.jahia,
            unpublished: facts.jahia.unpublished.map(node => ({path: under(node.path, path), status: node.status.toUpperCase()})),
            untranslated: facts.jahia.untranslated.map(node => ({...node, path: under(node.path, path)})),
            recommendations: recs.jahia
        },
        a11y: recs.a11y
    };
}

/**
 * Checks the page on the server: Jahia renders it in the edit workspace, as the editor, and runs every
 * check of the server on it, the same as `frame.check`.
 *
 * @param {object} client the Apollo client
 * @param {{path: string, language: string, rules: object, pageUrl: string}} options what to check
 * @returns {Promise<object>} the results, as `serverResults` gives them
 */
export async function runServerChecks(client, {path, language, rules, pageUrl}) {
    const {data} = await client.query({query: CHECK_PAGE, variables: {path, language}, fetchPolicy: 'no-cache'});
    return serverResults(data.contentGovernance.checkPage, {rules, path, pageUrl});
}
