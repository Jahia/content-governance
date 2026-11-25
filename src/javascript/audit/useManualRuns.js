import {useCallback, useEffect, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {MANUAL} from './rules';
import {BROWSER, runChecks} from './runChecks';
import {MANUAL_RUNS, parsedRun, RUN_MANUAL_RULES} from './manualRuns';

/**
 * The runs of the rules on demand of the subdomains of the drawer (audit/manualRuns.js): the last run that the
 * server stored for each subdomain that holds such a rule, read once per page and language, and the run of a
 * subdomain on demand.
 *
 * @param {{path: string, language: string, subdomains: Array<string>, rules: ?object, frameRef: object, uiLanguage: string}} props
 *     the page and its language, the subdomains that hold a rule on demand, the rules of the site (`ruleBook`), the
 *     preview of the page, where the rules of the browser run, and the editor's interface language
 * @returns {{of: function(string): object, run: function(string): Promise<void>}} `of(subdomain)` gives
 *     `{status, run, error}`: `status` is `loading`, `ready`, `running` or `error`, and `run` the last stored run or null
 */
export function useManualRuns({path, language, subdomains, rules, frameRef, uiLanguage}) {
    const client = useApolloClient();
    const [state, setState] = useState({});
    const key = subdomains.join(',');

    const set = useCallback((subdomain, value) => setState(s => ({...s, [subdomain]: {...s[subdomain], ...value}})), []);

    useEffect(() => {
        if (!client || key === '') {
            return undefined;
        }
        let cancelled = false;
        setState({});
        key.split(',').forEach(subdomain => {
            set(subdomain, {status: 'loading', run: null, error: null});
            client.query({query: MANUAL_RUNS, variables: {path, language, subdomain}, fetchPolicy: 'no-cache'})
                .then(({data}) => !cancelled && set(subdomain, {status: 'ready', run: parsedRun(data.contentGovernance.manualRuns[0])}))
                .catch(error => !cancelled && set(subdomain, {status: 'error', error: error.message}));
        });
        return () => {
            cancelled = true;
        };
    }, [client, path, language, key, set]);

    const run = useCallback(async subdomain => {
        set(subdomain, {status: 'running', error: null});
        try {
            const inBrowser = (rules?.all() || []).filter(rule => rule.trigger === MANUAL && rule.runtime === BROWSER
                && rule.enabled && rule.subdomain === subdomain);
            let browserResults = null;
            if (inBrowser.length > 0) {
                const frame = frameRef.current;
                if (!frame?.contentDocument) {
                    throw new Error('The preview of the page is not loaded yet');
                }
                const outcome = await runChecks(frame, {all: () => inBrowser}, uiLanguage, undefined, {manual: true});
                browserResults = JSON.stringify(outcome.results);
            }
            const {data} = await client.mutate({mutation: RUN_MANUAL_RULES, variables: {path, language, subdomain, browserResults}});
            set(subdomain, {status: 'ready', run: parsedRun(data.contentGovernance.runManualRules)});
        } catch (error) {
            console.warn(`[content-governance] the rules on demand of ${subdomain} did not run`, error);
            set(subdomain, {status: 'error', error: error.message});
        }
    }, [client, path, language, rules, frameRef, uiLanguage, set]);

    const of = useCallback(subdomain => state[subdomain] || {status: 'loading', run: null, error: null}, [state]);

    return {of, run, subdomains};
}
