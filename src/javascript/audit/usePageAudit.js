import {useCallback, useEffect, useRef, useState} from 'react';
import {useApolloClient} from '@apollo/client';
import {useGovernanceTranslation} from '../common/i18n';
import {fetchRules} from './rules';
import {checkPage} from './pageCheck';
import {rulesOfTabsOn, runChecks} from './runChecks';
import {browserTabs, RESULT_KEYS, SERVER_KEYS, withBrowserResults} from './browserResults';
import {removeToolingElements} from './tooling';
import {runStep} from './timeLimit';
import {fetchPageAudits, fetchPageLastModified, saveAudit, storedResults} from './auditHistory';
import {findByText, findEditFrameDocument, locateInFrame} from './editFrame';

// Let client islands hydrate and layout settle before measuring
const SETTLE_MS = 2500;

// The frame's load event waits for every image, font and script of the page: one request that never
// answers would hold the audit on "Loading" for good. Past this, a page whose document is parsed is
// audited anyway.
const FRAME_LOAD_LIMIT_MS = 20_000;

// Audits a page keeps for every editor of the site (AuditHistoryService.MAX_RUNS)
const AUDITS_KEPT = 10;

// The time the check of the server may take before its results are left out of the audit (runStep): it gives
// SEO, Readability, Links, Jahia health and the rules of the other subdomains. Each check of the browser has its
// own (runChecks.js).
const PAGE_LIMIT_MS = 60_000;

// The results that read the check of the server: its results, or its HTML rules
const SERVER_TABS = ['seo', 'readability', 'links', 'jahia', 'accessibility', 'other'];

// Results that an audit may lack and still show: an audit stored before 2.0.0 holds no `other`
const OPTIONAL_TABS = new Set(['other']);

// Why a view misses the facts of the repository: the server rendered the page, and could not read its state
const UNREAD = {incomplete: 'failed', message: 'the server could not read the repository'};

// Results are cached per page+language in localStorage so reopening the
// drawer restores the last audit instantly (with its timestamp shown in the
// header). "Re-run" always performs a fresh audit. LRU-capped.
const CACHE_PREFIX = 'content-governance:audit:';
const CACHE_MAX_ENTRIES = 10;
// Bump whenever the cached `results` shape changes (e.g. a new key of a tab),
// so entries written by an older module version are ignored instead of
// restored into UI that expects the new shape.
export const CACHE_SCHEMA = 7;
// The shape of the results before every finding was worded from its rule (Jahia/content-governance#140):
// such an audit shows its old text, with "Run again"
const LEGACY_SCHEMA = 6;

const cacheKey = (path, language) => `${CACHE_PREFIX}${language}:${path}`;

function loadCachedAudit(path, language) {
    try {
        const raw = globalThis.localStorage.getItem(cacheKey(path, language));
        if (!raw) {
            return null;
        }

        const entry = JSON.parse(raw);
        // Ignore (and clear) entries from an older results schema
        if (entry.schema !== CACHE_SCHEMA) {
            globalThis.localStorage.removeItem(cacheKey(path, language));
            return null;
        }

        return entry;
    } catch (error) {
        console.debug('[content-governance] no cached audit: the browser storage could not be read', error);
        return null;
    }
}

function pruneCache(keep) {
    try {
        const entries = Object.keys(globalThis.localStorage)
            .filter(k => k.startsWith(CACHE_PREFIX))
            .map(k => {
                let timestamp = 0;
                try {
                    timestamp = JSON.parse(globalThis.localStorage.getItem(k)).timestamp || 0;
                } catch (error) {
                    // Unparseable entry: treat as oldest
                    console.debug(`[content-governance] the cached audit ${k} is unreadable, and goes first`, error);
                }

                return {k, timestamp};
            })
            .sort((a, b) => b.timestamp - a.timestamp);
        entries.slice(keep).forEach(e => globalThis.localStorage.removeItem(e.k));
    } catch (error) {
        console.debug('[content-governance] the cached audits could not be pruned: no browser storage', error);
    }
}

function saveCachedAudit(path, language, entry) {
    const write = () => globalThis.localStorage.setItem(cacheKey(path, language), JSON.stringify(entry));
    try {
        write();
        pruneCache(CACHE_MAX_ENTRIES);
    } catch (error) {
        // Quota exceeded: evict everything but the most recent and retry once
        console.debug('[content-governance] the audit could not be cached, retrying with less', error);
        pruneCache(2);
        try {
            write();
        } catch (retryError) {
            // Caching is best-effort
            console.debug('[content-governance] the audit is not cached', retryError);
        }
    }
}

/**
 * The results a fresh audit measured for the views the site shows, plus the page facts and the
 * steps that did not finish (`incomplete`, by result key).
 */
function measuredResults(measured, page, incomplete, isOn) {
    // The shape of the results goes with the page facts, which every stored run keeps
    const kept = {page: {...page, schema: CACHE_SCHEMA}};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab) && measured[key]) {
            kept[key] = measured[key];
        }
    }
    const missing = Object.keys(incomplete).filter(key => Object.entries(RESULT_KEYS).some(([tab, k]) => k === key && isOn(tab)));
    // A check of the server that did not finish leaves no view of its own when they are all off, and its
    // page facts and HTML rules are still missing: such a run is never cached or stored either
    const partial = [...missing, ...(incomplete.page ? ['page'] : [])];
    if (partial.length > 0) {
        kept.incomplete = Object.fromEntries(partial.map(key => [key, incomplete[key]]));
    }
    return kept;
}

/**
 * Marks the results of the check of the server that miss their values: each of them when the check did not
 * finish, else the ones whose state of the content the server could not read.
 */
function markServerTabs(incomplete, checked) {
    if (incomplete.page) {
        SERVER_KEYS.forEach(key => {
            incomplete[key] = incomplete.page;
        });
    }
    (checked?.unread || []).forEach(key => {
        incomplete[key] = UNREAD;
    });
}

/** The page facts kept with each audit, from the SEO facts of the server: empty without them. */
const pageFactsOf = seo => ({
    title: seo ? seo.title.text || '' : '',
    description: seo ? seo.description.text || '' : '',
    imagesWithoutAlt: seo ? seo.imagesWithoutAlt : 0
});

/**
 * Measures the page for the views the site shows. The check of the server gives SEO, Readability, Links,
 * Jahia health and the rules of the other subdomains, with the findings that `frame.check` gives for the
 * page. The checks that modules register in the browser run on the preview frame: axe-core, the web vitals
 * and eco-design, and those of other modules. Their results join the results of the subdomain of each rule,
 * and a rule that no check gave a result for is listed as not checked. The frame is refused when it shows an
 * error page, and its editing tooling is stripped first.
 *
 * @returns {Promise<object>} the results (`measuredResults`), with `incomplete` when a step did not finish
 */
async function auditFrame(frame, {isOn, language, uiLanguage, path, apolloClient}) {
    // Refuse to audit an error page (404/500 render) - scoring it
    // would silently produce meaningless results.
    const navEntry = frame.contentWindow.performance.getEntriesByType('navigation')[0];
    if (navEntry && navEntry.responseStatus >= 400) {
        throw new Error(`HTTP ${navEntry.responseStatus}`);
    }

    // Strip editor/preview tooling (jExperience persona panel…)
    // before any check of the browser sees the DOM.
    removeToolingElements(frame.contentDocument);

    // Read once per audit, so that a rule the site changed applies at the next run, with the texts of the
    // rules in the editor's interface language.
    const rules = await fetchRules(apolloClient, path, uiLanguage);

    // Only the views the site shows are kept. The check of the server runs when a view that reads it is
    // shown, Accessibility included, and gives the page facts kept with each audit (title, description,
    // images without alt): it starts at once, since its request leaves no trace in the frame's own
    // resource statistics. The checks of the browser run meanwhile, one after the other.
    // A step that fails or runs out of time leaves its own results out; the others are still shown,
    // and such a partial audit is never cached. So is an audit whose server could not read the state of
    // the content: Jahia health and Links say so.
    const incomplete = {};
    const server = SERVER_TABS.some(tab => isOn(tab)) ? runStep(() => checkPage(apolloClient, path, language, rules), PAGE_LIMIT_MS) : null;
    const browser = browserTabs(await runChecks(frame, rulesOfTabsOn(rules, isOn), uiLanguage), rules);
    const outcome = server && await server;
    if (outcome?.incomplete) {
        console.warn('[content-governance] the check of the server did not finish', outcome);
        incomplete.page = outcome;
    }
    const checked = outcome?.value;
    markServerTabs(incomplete, checked);
    Object.entries(browser.incomplete).forEach(([tab, step]) => {
        incomplete[RESULT_KEYS[tab]] = incomplete[RESULT_KEYS[tab]] || step;
    });
    const page = pageFactsOf(checked?.seo);
    return measuredResults(withBrowserResults(checked, browser), page, incomplete, isOn);
}

/**
 * The results of the views the site shows, or null when one of them is missing (a view shown since the audit
 * was cached) or when an older version of the module stored them in a shape that this version cannot show: a
 * view the site does not show is never shown nor exported. An audit of the shape before #140 is kept, marked
 * `legacy`: it shows its old text, with "Run again".
 */
export function keepEnabled(results, isOn) {
    const schema = results.page?.schema;
    if (schema !== CACHE_SCHEMA && schema !== LEGACY_SCHEMA) {
        return null;
    }
    const kept = {page: results.page, ...(schema === LEGACY_SCHEMA ? {legacy: true} : {})};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab)) {
            if (!results[key] && !OPTIONAL_TABS.has(tab)) {
                return null;
            }
            if (results[key]) {
                kept[key] = results[key];
            }
        }
    }
    return kept;
}

/**
 * The page check of the drawer: the page checked by the server and rendered in a preview frame, measured
 * there by the checks of the browser (accessibility, vitals, eco-design, and those of other modules). Every
 * domain of the drawer reads its results, so it runs once per opening of the drawer, the first time a
 * domain that shows one of its subdomains shows (`active`). Results are cached per page and language, and
 * the audits stored for every editor of the site come first; "Re-run" measures the page again.
 *
 * @param {{path: string, language: string, active: boolean, isOn: function(string): boolean}} props the
 *     page, its language, whether a domain that reads the check showed, and whether the site shows the
 *     results of a key of `RESULT_KEYS`. `isOn` must keep its identity while the layout stays the same.
 * @returns {object} the state of the audit and its actions
 */
export function usePageAudit({path, language, active, isOn}) {
    const {t, i18n} = useGovernanceTranslation('audit');
    // The texts of the rules follow the editor's interface language, not the language of the content
    const uiLanguage = i18n?.language || 'en';
    const apolloClient = useApolloClient();
    const [status, setStatus] = useState('idle');
    const [results, setResults] = useState(null);
    const [error, setError] = useState(null);
    const [auditedAt, setAuditedAt] = useState(null);
    // True when the page was modified after the cached audit: the old report
    // stays visible (the editor may be mid-fix) with a re-run notice.
    const [stale, setStale] = useState(false);
    // The audits of the page stored for every editor of the site, newest first, and who ran the one shown
    const [history, setHistory] = useState([]);
    const [auditedBy, setAuditedBy] = useState(null);
    // Set when results were restored from cache: skips re-analysis on frame load
    const cacheHitRef = useRef(false);
    const [runId, setRunId] = useState(0);
    // The iframe is mounted only once the drawer slide-in transition is done:
    // loading it while the drawer is still translated off-screen makes Chrome
    // throttle rendering, and FCP/LCP paint entries never fire.
    const [frameVisible, setFrameVisible] = useState(false);
    // Hiding the preview keeps the iframe mounted at full size behind the results - the checks of the
    // browser and highlight still need its document, and a frame with no height never paints.
    // Beside Page Builder the page is already on screen: the preview starts hidden behind the
    // results, where it keeps its size and paints, so the checks of the browser measure it as before.
    // Null until the check is active, which decides.
    const [previewCollapsed, setPreviewCollapsed] = useState(null);
    const frameRef = useRef(null);
    const highlightedRef = useRef(null);
    // The run whose analysis has started: the frame's load event and the load time limit both start
    // it, and a later load of the frame (a link followed in the preview) never starts it again.
    const analyzedRunRef = useRef(null);

    const previewUrl = `${globalThis.contextJsParameters?.contextPath || ''}/cms/render/default/${language}${path}.html?pageAuditRun=${runId}`;

    useEffect(() => {
        if (active) {
            setPreviewCollapsed(collapsed => (collapsed === null ? Boolean(findEditFrameDocument(path, language)) : collapsed));
        }
    }, [active, path, language]);

    useEffect(() => {
        if (!active) {
            return undefined;
        }
        setError(null);
        highlightedRef.current = null;

        const showCached = (entry, by) => {
            cacheHitRef.current = true;
            setResults(entry.results);
            setAuditedAt(entry.timestamp || null);
            setAuditedBy(by);
            setStatus('ready');
            // Staleness probe: was the page modified after this audit?
            if (entry.timestamp) {
                fetchPageLastModified(apolloClient, path, language).then(modifiedAt => {
                    if (modifiedAt && modifiedAt > entry.timestamp) {
                        setStale(true);
                    }
                });
            }
        };

        const cached = runId === 0 ? loadCachedAudit(path, language) : null;
        const cachedResults = cached?.results ? keepEnabled(cached.results, isOn) : null;
        setStale(false);
        if (cachedResults) {
            showCached({...cached, results: cachedResults}, null);
        } else {
            cacheHitRef.current = false;
            setStatus('loading');
            setResults(null);
            setAuditedAt(null);
            setAuditedBy(null);
        }

        // The frame mounts a moment after the drawer shows: a frame loaded while its box is still
        // being laid out makes Chrome throttle rendering, and FCP/LCP paint entries never fire.
        // On opening, the audits stored for the site come first: the newest, whoever ran it, is shown
        // instead of measuring the page again, and without any the frame measures it.
        setFrameVisible(false);
        let cancelled = false;
        let timer = null;
        const mountFrame = () => {
            if (!cancelled) {
                timer = setTimeout(() => setFrameVisible(true), 400);
            }
        };
        if (runId === 0) {
            fetchPageAudits(apolloClient, path, language).then(runs => {
                if (cancelled) {
                    return;
                }
                setHistory(runs);
                const newest = runs[0];
                const newestAt = newest ? Date.parse(newest.auditedAt) : 0;
                const shared = storedResults(newest);
                const sharedResults = shared ? keepEnabled(shared, isOn) : null;
                if (sharedResults && newestAt > (cachedResults ? cached.timestamp || 0 : 0)) {
                    showCached({results: sharedResults, timestamp: newestAt}, newest.auditedBy);
                }
                mountFrame();
            });
        } else {
            mountFrame();
        }
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [active, runId, path, language, apolloClient, isOn]);

    // A run stored for the site becomes the newest of the page's history
    const addRun = useCallback(run => {
        if (run) {
            setAuditedBy(run.auditedBy);
            setHistory(runs => [run, ...runs].slice(0, AUDITS_KEPT));
        }
    }, []);

    const handleFrameLoad = useCallback(() => {
        if (cacheHitRef.current) {
            // Results restored from cache: the frame is only a preview /
            // highlight target, no re-analysis
            return;
        }
        if (analyzedRunRef.current === runId) {
            return;
        }
        analyzedRunRef.current = runId;

        setStatus('analyzing');
        setTimeout(async () => {
            const frame = frameRef.current;
            try {
                if (!frame || !frame.contentDocument) {
                    throw new Error('Preview frame unavailable');
                }

                const newResults = await auditFrame(frame, {isOn, language, uiLanguage, path, apolloClient});
                const timestamp = Date.now();
                setResults(newResults);
                setAuditedAt(timestamp);
                setAuditedBy(null);
                if (!newResults.incomplete) {
                    saveCachedAudit(path, language, {schema: CACHE_SCHEMA, timestamp, results: newResults});
                    saveAudit(apolloClient, path, language, newResults).then(addRun);
                }
                setStatus('ready');
            } catch (e) {
                console.error('[content-governance] analysis failed', e);
                setError(e.message);
                setStatus('error');
            }
        }, SETTLE_MS);
    }, [language, uiLanguage, path, apolloClient, isOn, runId, addRun]);

    // Starts the analysis of a page whose load event does not come (a request that never answers)
    // once its document is parsed, or reports a preview that did not load at all.
    useEffect(() => {
        const waiting = frameVisible && status === 'loading';
        const timer = waiting && setTimeout(() => {
            const doc = frameRef.current && frameRef.current.contentDocument;
            if (doc && doc.readyState !== 'loading' && doc.body) {
                console.warn('[content-governance] the preview did not finish loading; auditing it as it is');
                handleFrameLoad();
            } else {
                setError(t('drawer.frameTimeout', {seconds: FRAME_LOAD_LIMIT_MS / 1000}));
                setStatus('error');
            }
        }, FRAME_LOAD_LIMIT_MS);
        return () => clearTimeout(timer);
    }, [frameVisible, status, handleFrameLoad, t]);

    // Marks one element: in the page under edit when Page Builder shows it beside the drawer,
    // else in the preview, which opens for it.
    const mark = useCallback(target => {
        if (highlightedRef.current) {
            try {
                highlightedRef.current.style.outline = '';
                highlightedRef.current.style.outlineOffset = '';
            } catch {
                // Element may be gone after re-run or a reload of the page under edit
            }
            highlightedRef.current = null;
        }
        if (!target) {
            return;
        }
        if (target.ownerDocument === (frameRef.current && frameRef.current.contentDocument)) {
            setPreviewCollapsed(false);
        }
        target.scrollIntoView({behavior: 'smooth', block: 'center'});
        target.style.outline = '3px solid #e0182d';
        target.style.outlineOffset = '2px';
        highlightedRef.current = target;
    }, []);

    // Marks the element of a selector and answers whether the page has it: false gives "Item not found"
    const highlight = useCallback(selector => {
        const doc = frameRef.current && frameRef.current.contentDocument;
        if (!doc) {
            return false;
        }
        let el = null;
        try {
            el = doc.querySelector(selector);
        } catch {
            return false;
        }
        if (!el) {
            return false;
        }
        const editDoc = findEditFrameDocument(path, language);
        mark((editDoc && locateInFrame(el, editDoc)) || el);
        return true;
    }, [mark, path, language]);

    const highlightText = useCallback(sample => {
        const editDoc = findEditFrameDocument(path, language);
        mark((editDoc && findByText(editDoc, sample))
            || findByText(frameRef.current && frameRef.current.contentDocument, sample));
    }, [mark, path, language]);

    // A mark left in the page under edit goes with the drawer
    useEffect(() => () => mark(null), [mark]);

    const exportJson = useCallback(() => {
        if (!results) {
            return;
        }

        const payload = {path, language, url: previewUrl.split('?')[0], auditedAt, results};
        const blob = new Blob([JSON.stringify(payload, null, 2)], {type: 'application/json'});
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `content-governance-audit-${path.split('/').findLast(Boolean) || 'page'}.json`;
        a.click();
        URL.revokeObjectURL(a.href);
    }, [results, path, language, previewUrl, auditedAt]);

    const rerun = useCallback(() => setRunId(id => id + 1), []);

    return {
        active,
        status,
        busy: status === 'loading' || status === 'analyzing',
        ready: status === 'ready' && Boolean(results),
        results,
        error,
        auditedAt,
        auditedBy,
        stale,
        history,
        runId,
        previewUrl,
        frameVisible,
        frameRef,
        previewCollapsed: previewCollapsed !== false,
        setPreviewCollapsed,
        handleFrameLoad,
        highlight,
        highlightText,
        rerun,
        exportJson
    };
}
