import {useCallback, useEffect, useState} from 'react';
import {useGovernanceTranslation} from '../common/i18n';
import {runCrawlerCheck} from './api/crawlerCheck';
import {fetchPageCitations} from './api/citations';

const CACHE_SCHEMA = 8;

// Results are cached per page and language so reopening the drawer is instant.
// Bump CACHE_SCHEMA whenever the report shape changes, or an old cached entry
// will be restored into a UI that no longer understands it.
const cacheKey = (path, language) => `content-governance:geo:${CACHE_SCHEMA}:${language}:${path}`;

function loadCached(path, language) {
    try {
        const raw = globalThis.localStorage.getItem(cacheKey(path, language));
        if (!raw) {
            return null;
        }
        const entry = JSON.parse(raw);
        return entry && entry.schema === CACHE_SCHEMA ? entry : null;
    } catch (e) {
        return null;
    }
}

function saveCached(path, language, report) {
    try {
        globalThis.localStorage.setItem(
            cacheKey(path, language),
            JSON.stringify({schema: CACHE_SCHEMA, timestamp: Date.now(), report})
        );
    } catch (e) {
        // localStorage unavailable or full. Caching is a convenience, not a requirement.
    }
}

/**
 * The GEO check of the drawer: can AI crawlers read this page, and what the site's files, structured data
 * and BotRank citations say about it. The check runs on demand, since it fetches the public page once per AI
 * crawler, and its last report on the page is kept in the browser. Citations load on their own from the
 * site's last sync, once a domain that shows them showed (`citationsOn` and `active`).
 *
 * @param {{path: string, language: string, active: boolean, citationsOn: boolean}} props the page, its
 *     language, whether a domain that reads the check showed, and whether the site shows the citations
 * @returns {object} the report, its state, `runs` (the number of the check run in the drawer), the citations and
 *     `run`
 */
export function useGeoCheck({path, language, active, citationsOn}) {
    const {t} = useGovernanceTranslation('geo');
    const [report, setReport] = useState(null);
    const [ranAt, setRanAt] = useState(null);
    const [phase, setPhase] = useState('idle');
    const [error, setError] = useState(null);
    // The number of the check run in the drawer: 0 for the report kept in the browser
    const [runs, setRuns] = useState(0);
    // Citations come from the site's last sync, not from the crawler check: loaded once per mount.
    // configured false (no binding) and 403 (citations or module off for this site) both mean unavailable.
    const [citations, setCitations] = useState(null);
    const [citationsState, setCitationsState] = useState('loading');

    useEffect(() => {
        const cached = loadCached(path, language);
        if (cached) {
            setReport(cached.report);
            setRanAt(cached.timestamp);
            setPhase('done');
        } else {
            setReport(null);
            setRanAt(null);
            setPhase('idle');
        }
        setError(null);
    }, [path, language]);

    useEffect(() => {
        if (!citationsOn) {
            setCitationsState('off');
            return undefined;
        }
        if (!active) {
            return undefined;
        }
        let alive = true;
        setCitations(null);
        setCitationsState('loading');
        fetchPageCitations({path, language})
            .then(d => {
                if (alive) {
                    setCitations(d);
                    setCitationsState(d && d.configured ? 'ready' : 'notConfigured');
                }
            })
            .catch(e => alive && setCitationsState(e.code === 403 ? 'notConfigured' : 'failed'));
        return () => {
            alive = false;
        };
    }, [path, language, active, citationsOn]);

    const run = useCallback(async () => {
        setRuns(count => count + 1);
        setPhase('running');
        setError(null);
        try {
            const result = await runCrawlerCheck({path, language});
            setReport(result);
            setRanAt(Date.now());
            saveCached(path, language, result);
            setPhase('done');
        } catch (e) {
            setError(e.code === 'rateLimit' ? t('error.rateLimit') : t('error.generic'));
            setPhase('idle');
        }
    }, [path, language, t]);

    return {report, ranAt, runs, phase, error, citations, citationsState, run};
}
