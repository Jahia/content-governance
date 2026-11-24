import {useCallback, useEffect, useRef, useState} from 'react';
import {useSelector} from 'react-redux';
import {useApolloClient} from '@apollo/client';
import {SCAN_PAGE} from './scan.gql';
import {collectRenderedPaths} from './renderedPaths';

/**
 * The compliance scan of the drawer: the page and everything it shows, checked against the site's term rules
 * (the rules of the evaluator pattern) for the language being edited, such as the terms of the legal team and
 * the glossary of the brand. It runs the first time a domain that reads it shows (`active`), on a page that the
 * editor may write; "Scan again" runs it again.
 *
 * @param {{path: string, language: string, active: boolean}} props the page, its language, and whether a
 *     domain that reads the scan showed and the editor may use it
 * @returns {{result: object, run: Function, uiLanguage: string}} `result` is `{loading}`, `{error}` or
 *     `{report, framed}`, each with `run`, the number of the scan, and `{}` before the first run
 */
export function useComplianceScan({path, language, active}) {
    const client = useApolloClient();
    const uiLanguage = useSelector(state => state.uilang);
    const [result, setResult] = useState({});
    const runId = useRef(0);

    const run = useCallback(() => {
        const id = ++runId.current;
        const renderedPaths = collectRenderedPaths(path);
        setResult({loading: true, run: id});
        client.query({
            query: SCAN_PAGE,
            variables: {path, language, uiLanguage, renderedPaths: renderedPaths || []},
            fetchPolicy: 'network-only'
        }).then(({data}) => {
            if (id === runId.current) {
                setResult({report: data.complianceScan.scanPage, framed: renderedPaths !== null, run: id});
            }
        }).catch(error => {
            if (id === runId.current) {
                console.error('[content-governance] compliance scan failed', error);
                setResult({error, run: id});
            }
        });
    }, [client, path, language, uiLanguage]);

    useEffect(() => {
        if (!active) {
            return undefined;
        }
        run();
        return () => {
            runId.current++;
        };
    }, [active, run]);

    return {result, run, uiLanguage};
}
