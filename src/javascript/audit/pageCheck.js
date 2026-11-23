import {gql} from '@apollo/client';
import {limits} from './rules';

/**
 * The page check service of the server for the Page quality tabs SEO, Readability, Links and Jahia
 * health: `checkPage` renders the page as the editor, in the edit workspace, runs every check of the
 * server on it, and gives the result of each rule that ran, `{ruleId, passed, values, findings}`.
 * `frame.check` gives the same findings for the same path. Each finding lands in one tab, under the id
 * of its rule, and each tab shows the values of the results that feed it.
 */

export const PAGE_CHECK = gql`
    query ContentGovernanceCheckPage($path: String!, $language: String!) {
        contentGovernance {
            checkPage(path: $path, language: $language) {
                path
                results {
                    ruleId
                    passed
                    values
                    findings {
                        ruleId
                        severity
                        text
                        context
                        locator
                        values
                    }
                }
            }
        }
    }
`;

/**
 * The rules whose results feed the blocks of the tabs, beside their findings. A rule that the site turned
 * off gives no result, and its block is left out: `readability.statistics` and `readability.headings`
 * feed the Readability tab, `seo.tags` the tag list and the sharing preview of SEO, `links.broken` the
 * links of Links, `health.unpublishedChanges` and `health.missingTranslations` the state of the content
 * of Jahia health.
 */
const FEEDS = {
    tags: 'seo.tags',
    statistics: 'readability.statistics',
    headings: 'readability.headings',
    links: 'links.broken',
    unpublished: 'health.unpublishedChanges',
    untranslated: 'health.missingTranslations'
};

/** The SEO tab with no tag list: the texts of the page facts kept with each audit are empty. */
const NO_TAGS = {title: {text: null, length: 0}, description: {text: null, length: 0}};

/**
 * The Page quality tab of each subdomain of rules (Jahia/content-governance#151). No list names the tab of a
 * rule: a finding goes to the tab of the subdomain of its rule on the site. A rule of a subdomain of SEO & GEO
 * (but `seo`) or of Compliance (but `readability` and `writing`) shows in its own section, and a rule of an
 * unknown subdomain never runs. Jahia/content-governance#167 builds the tabs from the hierarchy that
 * `contentDomains` gives.
 */
export const DOMAIN_TABS = {
    accessibility: 'accessibility',
    seo: 'seo',
    readability: 'readability',
    writing: 'readability',
    links: 'links',
    health: 'jahia',
    ecodesign: 'ecodesign'
};

/**
 * The tab that shows the findings of a rule.
 *
 * @param {object} [rule] the rule of the site, whose `subdomain` names its tab
 * @returns {?string} `accessibility`, `seo`, `readability`, `links`, `jahia` or `ecodesign`, or null for a
 *     rule that no Page quality tab shows
 */
export function tabOf(rule) {
    return DOMAIN_TABS[rule?.subdomain] || null;
}

/**
 * The recommendations of the findings, one for each rule in the order of the rules, by tab: what the
 * tab shows, worded from the rule, with each finding of the rule in `items`.
 *
 * The values of the texts are generic: `count` (the findings of the rule), `value` (the text of the first
 * finding, such as the measured length of a title), and the limits of the rule.
 *
 * @param {Array<{ruleId: string, severity: string, text: string, context: ?string, locator: ?string}>} findings the findings of the service
 * @param {{find: function(string): ?object, word: function(string, object): object}} rules the rules of the site
 * @returns {Object<string, Array<{key: string, rule: string, severity: string, params: object, items: Array<object>,
 *     title: string, detail: string, label: string}>>}
 */
export function recommendationsByTab(findings, rules) {
    const byRule = new Map();
    findings.forEach(finding => {
        const items = byRule.get(finding.ruleId) || [];
        items.push({
            text: finding.text,
            ...(finding.context ? {context: finding.context} : {}),
            ...(finding.locator ? {locator: finding.locator} : {})
        });
        byRule.set(finding.ruleId, items);
    });
    const severities = new Map(findings.map(finding => [finding.ruleId, finding.severity]));
    const out = {seo: [], readability: [], links: [], jahia: [], accessibility: [], ecodesign: []};
    byRule.forEach((items, ruleId) => {
        const rule = rules.find(ruleId);
        const tab = tabOf(rule);
        if (!tab) {
            console.warn(`[content-governance] the rule ${ruleId} has the subdomain ${rule?.subdomain}, which no Page quality tab shows`);
            return;
        }
        const params = {...(rule ? limits(rule) : {}), count: items.length, value: items[0].text};
        out[tab].push({
            key: ruleId,
            rule: ruleId,
            severity: severities.get(ruleId) || rule?.severity || 'minor',
            params,
            items,
            ...rules.word(ruleId, params)
        });
    });
    return out;
}

/**
 * An address of the page as the preview resolves it, such as the image of the sharing card: relative
 * to the address of the render, `images/card.png` beside the page.
 */
const resolved = (address, pageUrl) => {
    try {
        return new URL(address, pageUrl).href;
    } catch {
        return address;
    }
};

/**
 * The tabs whose values the server could not read in the repository, though it rendered the page and ran
 * their rules: Jahia health with no state of the content, Links with no resolution of the internal links.
 */
const unreadTabs = values => [
    ...(values.has(FEEDS.links) && (values.get(FEEDS.links).checked ?? null) === null ? ['links'] : []),
    ...([FEEDS.unpublished, FEEDS.untranslated].some(id => values.has(id) && values.get(id).scanned === undefined) ? ['jahia'] : [])
];

/** A path under the page, as the Jahia health tab lists it: `/main/intro`, or `/` for the page itself. */
const under = (path, pagePath) => (path.startsWith(pagePath) ? path.slice(pagePath.length) || '/' : path);

/**
 * The state of the content of Jahia health, from the values of its two rules. `scanned` is null when
 * neither rule ran.
 */
const contentState = (values, pagePath) => {
    const unpublished = values.get(FEEDS.unpublished) || {};
    const untranslated = values.get(FEEDS.untranslated) || {};
    return {
        pageStatus: unpublished.pageStatus ? unpublished.pageStatus.toUpperCase() : null,
        activeLanguages: untranslated.activeLanguages || [],
        scanned: unpublished.scanned ?? untranslated.scanned ?? null,
        unpublished: (unpublished.unpublished || []).map(node => ({...node, path: under(node.path, pagePath), status: node.status.toUpperCase()})),
        untranslated: (untranslated.untranslated || []).map(node => ({...node, path: under(node.path, pagePath)}))
    };
};

/**
 * The results of the tabs SEO, Readability, Links and Jahia health, and the recommendations of the
 * Accessibility tab, from the results of the service. Each block of a tab reads the values of the rule
 * that feeds it (`FEEDS`), and is left out when that rule did not run.
 *
 * @param {Array<{ruleId: string, passed: boolean, values: object, findings: Array<object>}>} results the
 *     results of `checkPage`, with their values parsed
 * @param {{get: function(string): object, find: function(string): ?object}} rules the rules of the site
 * @param {string} pagePath the path of the page
 * @param {string} pageUrl the address of the render of the page, which a relative address of the page is under
 * @returns {{seo: object, readability: object, links: object, jahia: object, accessibility: Array<object>,
 *     unread: Array<string>}} `unread` names the result key of each tab whose repository values are missing
 */
export function tabResults(results, rules, pagePath, pageUrl) {
    const recs = recommendationsByTab(results.flatMap(result => result.findings), rules);
    const itemsOf = ruleId => recs.jahia.find(rec => rec.rule === ruleId)?.items || [];
    const values = new Map(results.map(result => [result.ruleId, result.values]));
    const tags = values.get(FEEDS.tags);
    return {
        seo: {
            ...(tags ? {...tags, ogImageResolved: tags.og.image ? resolved(tags.og.image, pageUrl) : null} : NO_TAGS),
            ranges: {title: limits(rules.get('seo.title')), description: limits(rules.get('seo.metaDescription'))},
            recommendations: recs.seo
        },
        readability: {...values.get(FEEDS.statistics), ...values.get(FEEDS.headings), recommendations: recs.readability},
        links: {...values.get(FEEDS.links), recommendations: recs.links},
        jahia: {
            ...contentState(values, pagePath),
            rawKeys: {count: itemsOf('health.rawI18nKeys').length, samples: itemsOf('health.rawI18nKeys').map(item => ({key: item.text, text: item.context || ''}))},
            placeholders: {count: itemsOf('health.placeholderText').length, samples: itemsOf('health.placeholderText').map(item => ({match: item.text, text: item.context || ''}))},
            recommendations: recs.jahia
        },
        accessibility: recs.accessibility,
        ecodesign: recs.ecodesign,
        unread: unreadTabs(values)
    };
}

/** A result of the service with its values, and the values of its findings, read from their JSON. */
const parsed = result => ({
    ...result,
    values: JSON.parse(result.values),
    findings: result.findings.map(finding => ({...finding, values: JSON.parse(finding.values)}))
});

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
    const render = `${globalThis.contextJsParameters?.contextPath || ''}/cms/render/default/${language}${check.path}.html`;
    return tabResults(check.results.map(parsed), rules, check.path, new URL(render, globalThis.location.href).href);
}
