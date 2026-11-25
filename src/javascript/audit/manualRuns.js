import {gql} from '@apollo/client';
import {recommendationsOf} from './browserResults';
import {paramsOf, ruleViewsOf, severityOf} from './ruleViews';
import {auditFindingKey, isSerious} from '../drawer/findings';

/**
 * The runs of the rules on demand (`trigger: manual`): no audit runs such a rule, and the section of its subdomain
 * offers to run it. A run applies every rule on demand of the subdomain, those of the server on the server and those
 * of the browser on the preview, and the server stores it for every editor of the site. The section shows the last
 * run that the server stored, without running it again, and counts its failed rules.
 */

const RUN_FIELDS = `
    auditedAt
    auditedBy
    results
`;

export const MANUAL_RUNS = gql`
    query ContentGovernanceManualRuns($path: String!, $language: String!, $subdomain: String!) {
        contentGovernance {
            manualRuns(path: $path, language: $language, subdomain: $subdomain) {${RUN_FIELDS}}
        }
    }
`;

export const RUN_MANUAL_RULES = gql`
    mutation ContentGovernanceRunManualRules($path: String!, $language: String!, $subdomain: String!, $browserResults: String) {
        contentGovernance {
            runManualRules(path: $path, language: $language, subdomain: $subdomain, browserResults: $browserResults) {${RUN_FIELDS}}
        }
    }
`;

/**
 * A stored run as the drawer reads it.
 *
 * @param {?{auditedAt: string, auditedBy: ?string, results: ?string}} run a run of the API
 * @returns {?{at: number, by: ?string, results: Array<object>}} the run, with the result of each rule; null for no run
 *     or a run whose results cannot be read
 */
export function parsedRun(run) {
    if (!run?.results) {
        return null;
    }
    try {
        const results = JSON.parse(run.results).results;
        return Array.isArray(results) ? {at: Date.parse(run.auditedAt), by: run.auditedBy || null, results} : null;
    } catch (error) {
        console.warn('[content-governance] a stored run cannot be read', error);
        return null;
    }
}

/**
 * What a run shows, worded by the rules of the site in the editor's interface language: the recommendation of each
 * failed rule, and the facts and scores of the rules that show their values.
 *
 * @param {Array<{ruleId: string, passed: boolean, values: object, findings: Array<object>}>} results the results of the run
 * @param {object} rules the rules of the site (`ruleBook`)
 * @returns {{recommendations: Array<object>, views: Array<object>}} what the section shows
 */
export function shownRun(results, rules) {
    const known = results.filter(result => rules.find(result.ruleId))
        .map(result => ({...result, values: result.values || {}, findings: result.findings || []}));
    return {
        recommendations: Object.values(recommendationsOf(known, rules)).flat(),
        views: ruleViewsOf(known, rules, paramsOf)
    };
}

/**
 * What a run has to review: its failed rules, and whether one of them is critical or serious.
 *
 * @param {?{results: Array<object>}} run the run
 * @returns {{count: ?number, serious: boolean}} `count` is null with no run
 */
export function runStateOf(run) {
    if (!run) {
        return {count: null, serious: false};
    }
    const failed = run.results.filter(result => !result.passed);
    return {count: failed.length, serious: failed.some(result => isSerious(severityOf(result.findings, 'minor')))};
}

/**
 * The findings of a run for "Fix first": one per failed rule, in the subdomain of the run.
 *
 * @param {?{results: Array<object>}} run the run
 * @param {string} subdomainId the subdomain of the run
 * @param {?object} rules the rules of the site, which word the titles; the id of the rule without them
 * @returns {Array<object>} the findings
 */
export function runFindings(run, subdomainId, rules) {
    return (run?.results || []).filter(result => !result.passed).map(result => {
        const rule = rules?.find(result.ruleId);
        return {
            key: auditFindingKey(manualKey(subdomainId), result.ruleId),
            tab: subdomainId,
            rule: result.ruleId,
            severity: severityOf(result.findings, rule?.severity || 'minor'),
            title: rule ? rules.word(result.ruleId, paramsOf({...result, values: result.values || {}, findings: result.findings || []}, rule)).title : result.ruleId,
            count: (result.findings || []).length
        };
    });
}

/** The key of the results of the run on demand of a subdomain, which the keys of its findings start with. */
export const manualKey = subdomainId => `manual.${subdomainId}`;
