import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../common/i18n';
import {useApolloClient} from '@apollo/client';
import {runAccessibility} from './analyzers/accessibility';
import {runWebVitals} from './analyzers/webVitals';
import {runReadability} from './analyzers/readability';
import {runEcodesign} from './analyzers/ecodesign';
import {runSeo} from './analyzers/seo';
import {runLinks} from './analyzers/links';
import {runJahiaHealth, fetchPageLastModified} from './analyzers/jahiaHealth';
import {removeToolingElements} from './analyzers/tooling';
import {runStep} from './analyzers/timeLimit';
import {fetchPageAudits, saveAudit, storedResults} from './auditHistory';
import {requestAiReview, requestSeoAssist, requestAltAssist, requestSimplifyAssist} from './analyzers/aiReview';
import {AccessibilityTab} from './tabs/AccessibilityTab';
import {VitalsTab} from './tabs/VitalsTab';
import {ReadabilityTab} from './tabs/ReadabilityTab';
import {EcodesignTab} from './tabs/EcodesignTab';
import {SeoTab} from './tabs/SeoTab';
import {LinksTab} from './tabs/LinksTab';
import {JahiaTab} from './tabs/JahiaTab';
import {AiTab} from './tabs/AiTab';
import {IncompleteStep} from './tabs/IncompleteStep';
import {Badge, Banner, Button, Download, Loader, Reload, Tab, TabItem, Typography, Visibility} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import styles from './AuditSection.module.css';

// Let client islands hydrate and layout settle before measuring
const SETTLE_MS = 2500;

// The frame's load event waits for every image, font and script of the page: one request that never
// answers would hold the audit on "Loading" for good. Past this, a page whose document is parsed is
// audited anyway.
const FRAME_LOAD_LIMIT_MS = 20_000;

// Audits a page keeps for every editor of the site (AuditHistoryService.MAX_RUNS)
const AUDITS_KEPT = 10;

// The time each network-bound step may take before its tab is left out of the audit (runStep).
const STEP_LIMITS_MS = {vitals: 15_000, a11y: 60_000, links: 45_000, jahia: 30_000};

// Results are cached per page+language in localStorage so reopening the
// drawer restores the last audit instantly (with its timestamp shown in the
// header). "Re-run" always performs a fresh audit. LRU-capped.
const CACHE_PREFIX = 'content-governance:audit:';
const CACHE_MAX_ENTRIES = 10;
// Bump whenever the cached `results` shape changes (e.g. a new analyzer key),
// so entries written by an older module version are ignored instead of
// restored into UI that expects the new shape.
const CACHE_SCHEMA = 5;

const cacheKey = (path, language) => `${CACHE_PREFIX}${language}:${path}`;

function loadCachedAudit(path, language) {
    try {
        const raw = window.localStorage.getItem(cacheKey(path, language));
        if (!raw) {
            return null;
        }

        const entry = JSON.parse(raw);
        // Ignore (and clear) entries from an older results schema
        if (entry.schema !== CACHE_SCHEMA) {
            window.localStorage.removeItem(cacheKey(path, language));
            return null;
        }

        return entry;
    } catch (e) {
        return null;
    }
}

function pruneCache(keep) {
    try {
        const entries = Object.keys(window.localStorage)
            .filter(k => k.startsWith(CACHE_PREFIX))
            .map(k => {
                let timestamp = 0;
                try {
                    timestamp = JSON.parse(window.localStorage.getItem(k)).timestamp || 0;
                } catch (e) {
                    // Unparseable entry: treat as oldest
                }

                return {k, timestamp};
            })
            .sort((a, b) => b.timestamp - a.timestamp);
        entries.slice(keep).forEach(e => window.localStorage.removeItem(e.k));
    } catch (e) {
        // localStorage unavailable
    }
}

function saveCachedAudit(path, language, entry) {
    const write = () => window.localStorage.setItem(cacheKey(path, language), JSON.stringify(entry));
    try {
        write();
        pruneCache(CACHE_MAX_ENTRIES);
    } catch (e) {
        // Quota exceeded: evict everything but the most recent and retry once
        pruneCache(2);
        try {
            write();
        } catch (e2) {
            // Give up silently - caching is best-effort
        }
    }
}

/** The tabs of the section, in order; each id is also its feature id (`audit.<id>`). */
const TAB_IDS = ['accessibility', 'seo', 'vitals', 'readability', 'ecodesign', 'links', 'jahia', 'ai'];

/** The key of each measured tab in the results (the AI tab measures nothing). */
const RESULT_KEYS = {
    accessibility: 'a11y',
    seo: 'seo',
    vitals: 'vitals',
    readability: 'readability',
    ecodesign: 'ecodesign',
    links: 'links',
    jahia: 'jahia'
};

const formatDate = (timestamp, language) => new Date(timestamp).toLocaleString(language === 'fr' ? 'fr-FR' : language);

/**
 * The issues of the stored audits of the page, oldest to newest ("12 → 9 → 7"), each with its date and
 * who ran it on hover. Nothing while the page has fewer than two.
 */
function AuditTrend({runs, language}) {
    const {t} = useGovernanceTranslation('audit');
    if (runs.length < 2) {
        return null;
    }
    const oldestFirst = [...runs].reverse();
    return (
        <Typography variant="caption" className={styles.trend}>
            {t('drawer.trend', {count: runs.length})}{' '}
            {oldestFirst.map((run, index) => (
                <React.Fragment key={run.auditedAt}>
                    {index > 0 && ' → '}
                    <span title={`${formatDate(Date.parse(run.auditedAt), language)} · ${run.auditedBy || ''}`}>{run.summary.total}</span>
                </React.Fragment>
            ))}
        </Typography>
    );
}

AuditTrend.propTypes = {
    runs: PropTypes.arrayOf(PropTypes.shape({
        auditedAt: PropTypes.string.isRequired,
        auditedBy: PropTypes.string,
        summary: PropTypes.shape({total: PropTypes.number.isRequired}).isRequired
    })).isRequired,
    language: PropTypes.string.isRequired
};

/**
 * The results a fresh audit measured for the tabs the site keeps on, plus the page facts and the
 * steps that did not finish (`incomplete`, by result key).
 */
function measuredResults(measured, page, incomplete, isOn) {
    const kept = {page};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab) && measured[key]) {
            kept[key] = measured[key];
        }
    }
    const missing = Object.keys(incomplete).filter(key => Object.entries(RESULT_KEYS).some(([tab, k]) => k === key && isOn(tab)));
    if (missing.length > 0) {
        kept.incomplete = Object.fromEntries(missing.map(key => [key, incomplete[key]]));
    }
    return kept;
}

/**
 * Measures the preview frame's page for the tabs the site keeps on: refuses an error page, strips
 * editing tooling, then runs the analyzers in order.
 *
 * @returns {Promise<object>} the results (`measuredResults`), with `incomplete` when a step did not finish
 */
async function auditFrame(frame, {isOn, language, path, apolloClient}) {
    // Refuse to audit an error page (404/500 render) - scoring it
    // would silently produce meaningless results.
    const navEntry = frame.contentWindow.performance.getEntriesByType('navigation')[0];
    if (navEntry && navEntry.responseStatus >= 400) {
        throw new Error(`HTTP ${navEntry.responseStatus}`);
    }

    // Strip editor/preview tooling (jExperience persona panel…)
    // before any analyzer sees the DOM.
    removeToolingElements(frame.contentDocument);

    // Only the tabs the site keeps on are measured. Eco-design reads the vitals
    // measurement, so vitals run as its input; the SEO read is a cheap DOM pass that
    // always runs, for the page facts the AI tasks need (title, description, images
    // without alt). What is kept is those facts and the switched-on tabs only.
    // Vitals first: axe injection and link checks would otherwise
    // appear in the page's own resource statistics.
    // A network-bound step that fails or runs out of time leaves its own tab out; the
    // other tabs are still shown, and such a partial audit is never cached.
    const incomplete = {};
    const step = async (key, start) => {
        const outcome = await runStep(start, STEP_LIMITS_MS[key]);
        if (outcome.incomplete) {
            console.warn(`[content-governance] ${key} did not finish`, outcome);
            incomplete[key] = outcome;
            return null;
        }
        return outcome.value;
    };
    const vitals = isOn('vitals') || isOn('ecodesign') ? await step('vitals', () => runWebVitals(frame)) : null;
    let ecodesign = null;
    if (isOn('ecodesign')) {
        if (vitals) {
            ecodesign = runEcodesign(frame, vitals);
        } else {
            incomplete.ecodesign = incomplete.vitals;
        }
    }
    const readability = isOn('readability') ? runReadability(frame, language) : null;
    const seo = runSeo(frame, language);
    const page = {
        title: seo.title ? seo.title.text : '',
        description: seo.description ? (seo.description.text || '') : '',
        imagesWithoutAlt: seo.imagesWithoutAlt || 0
    };
    const a11y = isOn('accessibility') ? await step('a11y', () => runAccessibility(frame)) : null;
    const links = isOn('links') ? await step('links', () => runLinks(frame)) : null;
    const jahia = isOn('jahia') ? await step('jahia', () => runJahiaHealth(frame, {client: apolloClient, path, language})) : null;
    return measuredResults({a11y, vitals, ecodesign, readability, seo, links, jahia}, page, incomplete, isOn);
}

/**
 * The results of the tabs the site keeps on, or null when one of them is missing (a tab switched
 * back on since the audit was cached): a tab switched off is never shown, exported, nor sent to the
 * AI review.
 */
function keepEnabled(results, isOn) {
    const kept = {};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab)) {
            if (!results[key]) {
                return null;
            }
            kept[key] = results[key];
        }
    }
    if (results.page) {
        kept.page = results.page;
    }
    return kept;
}

/** Images without alt for the alt text assist, from the page facts kept with every audit. */
function imagesWithoutAlt(results) {
    return results.page ? results.page.imagesWithoutAlt : 0;
}

/**
 * The Page quality section of the drawer: the page rendered in a preview frame, measured by the
 * analyzers (accessibility, SEO, vitals, readability, eco-design, links, Jahia health), with the AI
 * review and assists on demand. Results are cached per page and language; the section measures
 * the page when it first shows and on every re-run.
 */
export function AuditSection({path, language, isOn, ai, onCount}) {
    const {t} = useGovernanceTranslation('audit');
    const apolloClient = useApolloClient();
    const [activeTab, setActiveTab] = useState(null);
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
    // AI review state lives here so it survives tab switches
    const [aiReview, setAiReview] = useState(null);
    const [aiPhase, setAiPhase] = useState('idle');
    const [aiError, setAiError] = useState(null);
    // SEO assist (AI suggestions inside the SEO tab) - same lifecycle rules
    const [seoAssist, setSeoAssist] = useState(null);
    const [seoAssistPhase, setSeoAssistPhase] = useState('idle');
    const [seoAssistError, setSeoAssistError] = useState(null);
    // Alt text assist (AI suggestions inside the Accessibility tab)
    const [altAssist, setAltAssist] = useState(null);
    const [altPhase, setAltPhase] = useState('idle');
    const [altError, setAltError] = useState(null);
    // Plain-language rewrites (AI suggestions inside the Readability tab)
    const [simplifyAssist, setSimplifyAssist] = useState(null);
    const [simplifyPhase, setSimplifyPhase] = useState('idle');
    const [simplifyError, setSimplifyError] = useState(null);
    // {enabled, provider, model, vision}: the module's shared model, read with the site settings;
    // shared by the AI tab and the assist sections
    const aiStatus = useMemo(() => ({
        enabled: Boolean(ai && ai.configured),
        provider: ai ? ai.provider : '',
        model: ai ? ai.model : '',
        vision: Boolean(ai && ai.images)
    }), [ai]);
    const [runId, setRunId] = useState(0);
    // The iframe is mounted only once the drawer slide-in transition is done:
    // loading it while the drawer is still translated off-screen makes Chrome
    // throttle rendering, and FCP/LCP paint entries never fire.
    const [frameVisible, setFrameVisible] = useState(false);
    // Collapsing hides the preview visually (height 0) but keeps the iframe
    // mounted - analyzers and highlight still need its document.
    const [previewCollapsed, setPreviewCollapsed] = useState(false);
    const frameRef = useRef(null);
    const highlightedRef = useRef(null);
    // The run whose analysis has started: the frame's load event and the load time limit both start
    // it, and a later load of the frame (a link followed in the preview) never starts it again.
    const analyzedRunRef = useRef(null);

    const previewUrl = `${globalThis.contextJsParameters?.contextPath || ''}/cms/render/default/${language}${path}.html?pageAuditRun=${runId}`;

    useEffect(() => {
        setError(null);
        setAiError(null);
        setSeoAssistError(null);
        setAltError(null);
        setSimplifyError(null);
        // Re-runs need a visible frame for paint-dependent measurements
        setPreviewCollapsed(false);
        highlightedRef.current = null;

        const showCached = (entry, by) => {
            cacheHitRef.current = true;
            setResults(entry.results);
            setAuditedAt(entry.timestamp || null);
            setAuditedBy(by);
            setAiReview(entry.aiReview || null);
            setAiPhase(entry.aiReview ? 'done' : 'idle');
            setSeoAssist(entry.seoAssist || null);
            setSeoAssistPhase(entry.seoAssist ? 'done' : 'idle');
            setAltAssist(entry.altAssist || null);
            setAltPhase(entry.altAssist ? 'done' : 'idle');
            setSimplifyAssist(entry.simplifyAssist || null);
            setSimplifyPhase(entry.simplifyAssist ? 'done' : 'idle');
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
            setAiReview(null);
            setAiPhase('idle');
            setSeoAssist(null);
            setSeoAssistPhase('idle');
            setAltAssist(null);
            setAltPhase('idle');
            setSimplifyAssist(null);
            setSimplifyPhase('idle');
        }

        // The frame mounts a moment after the section shows: a frame loaded while its box is still
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
    }, [runId, path, language, apolloClient, isOn]);

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

                const newResults = await auditFrame(frame, {isOn, language, path, apolloClient});
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
    }, [language, path, apolloClient, isOn, runId, addRun]);

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

    const highlight = useCallback(selector => {
        const doc = frameRef.current && frameRef.current.contentDocument;
        if (!doc) {
            return;
        }

        setPreviewCollapsed(false);

        if (highlightedRef.current) {
            try {
                highlightedRef.current.style.outline = '';
                highlightedRef.current.style.outlineOffset = '';
            } catch (e) {
                // Element may be gone after re-run
            }
        }

        let el = null;
        try {
            el = doc.querySelector(selector);
        } catch (e) {
            return;
        }

        if (el) {
            el.scrollIntoView({behavior: 'smooth', block: 'center'});
            el.style.outline = '3px solid #e0182d';
            el.style.outlineOffset = '2px';
            highlightedRef.current = el;
        }
    }, []);

    const highlightText = useCallback(sample => {
        const doc = frameRef.current && frameRef.current.contentDocument;
        if (!doc || !doc.body || !sample) {
            return;
        }

        setPreviewCollapsed(false);

        if (highlightedRef.current) {
            try {
                highlightedRef.current.style.outline = '';
                highlightedRef.current.style.outlineOffset = '';
            } catch (e) {
                // Element may be gone after re-run
            }
        }

        // LLM quotes are approximate: normalize whitespace, apostrophes and
        // quotes, compare case-insensitively, and search at ELEMENT level so
        // wording spanning several inline text nodes still matches. Fall back
        // to the first words of the quote when the full text is not found.
        const normalize = s => s
            .replace(/[’‘]/g, '\'')
            .replace(/[“”«»]/g, '"')
            .replace(/\s+/g, ' ')
            .trim()
            .toLowerCase();

        const fullNeedle = normalize(sample);
        const words = fullNeedle.split(' ');
        const needles = [...new Set([
            fullNeedle,
            words.slice(0, 8).join(' '),
            words.slice(0, 4).join(' ')
        ])].filter(n => n.length >= 3);

        const elements = [...doc.body.querySelectorAll('*')]
            .filter(el => !['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE'].includes(el.tagName));

        let target = null;
        for (const needle of needles) {
            const matches = elements.filter(el => normalize(el.textContent || '').includes(needle));
            if (matches.length > 0) {
                // Tightest container = the match with the least text
                target = matches.reduce((a, b) =>
                    ((a.textContent || '').length <= (b.textContent || '').length ? a : b));
                break;
            }
        }

        if (target) {
            target.scrollIntoView({behavior: 'smooth', block: 'center'});
            target.style.outline = '3px solid #e0182d';
            target.style.outlineOffset = '2px';
            highlightedRef.current = target;
        }
    }, []);

    // Every AI result rides in the same cache entry: always persist all of
    // them, overriding the one that just changed, or one save wipes the others.
    const persistAi = useCallback(override => {
        saveCachedAudit(path, language, {
            schema: CACHE_SCHEMA,
            timestamp: auditedAt || Date.now(),
            results,
            aiReview,
            seoAssist,
            altAssist,
            simplifyAssist,
            ...override
        });
    }, [path, language, auditedAt, results, aiReview, seoAssist, altAssist, simplifyAssist]);

    // The three AI tasks share one lifecycle: explicit click, running/done/error
    // phases, result cached with the audit. Never auto-triggered (cost control).
    const runAiTask = useCallback(async ({request, cacheKey: key, setResult, setPhase, setErr, label}) => {
        if (!results) {
            return;
        }

        setPhase('running');
        setErr(null);
        try {
            const answer = await request({language, path, results, frame: frameRef.current, aiStatus});
            setResult(answer);
            setPhase('done');
            persistAi({[key]: answer});
        } catch (e) {
            console.error(`[content-governance] ${label} failed`, e);
            setErr(e.code ? t(`ai.errors.${e.code}`, {defaultValue: e.message}) : e.message);
            setPhase('error');
        }
    }, [results, language, path, persistAi, aiStatus, t]);

    const generateAiReview = useCallback(() => runAiTask({
        request: requestAiReview, cacheKey: 'aiReview', label: 'AI review',
        setResult: setAiReview, setPhase: setAiPhase, setErr: setAiError
    }), [runAiTask]);

    const generateSeoAssist = useCallback(() => runAiTask({
        request: requestSeoAssist, cacheKey: 'seoAssist', label: 'SEO assist',
        setResult: setSeoAssist, setPhase: setSeoAssistPhase, setErr: setSeoAssistError
    }), [runAiTask]);

    const generateAltAssist = useCallback(() => runAiTask({
        request: requestAltAssist, cacheKey: 'altAssist', label: 'Alt text assist',
        setResult: setAltAssist, setPhase: setAltPhase, setErr: setAltError
    }), [runAiTask]);

    const generateSimplifyAssist = useCallback(() => runAiTask({
        request: requestSimplifyAssist, cacheKey: 'simplifyAssist', label: 'Plain-language assist',
        setResult: setSimplifyAssist, setPhase: setSimplifyPhase, setErr: setSimplifyError
    }), [runAiTask]);

    const exportJson = useCallback(() => {
        if (!results) {
            return;
        }

        const payload = {path, language, url: previewUrl.split('?')[0], auditedAt, results};
        const blob = new Blob([JSON.stringify(payload, null, 2)], {type: 'application/json'});
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `content-governance-audit-${path.split('/').filter(Boolean).pop() || 'page'}.json`;
        a.click();
        URL.revokeObjectURL(a.href);
    }, [results, path, language, previewUrl, auditedAt]);

    // Badge = number of issues to review in that tab; null-safe so a partial
    // result never breaks the tab bar. Tabs the site switched off are left out; the AI tab stays,
    // greyed out, while no model is configured.
    const count = (r, extract) => (r ? extract(r) : null);
    const badges = {
        accessibility: count(results && results.a11y, a => a.violations.length),
        seo: count(results && results.seo, r => r.recommendations.length),
        vitals: count(results && results.vitals, r => r.recommendations.length),
        readability: count(results && results.readability, r => r.recommendations.length),
        ecodesign: count(results && results.ecodesign, r => r.recommendations.length),
        links: count(results && results.links, r => r.recommendations.length),
        jahia: count(results && results.jahia, r => r.recommendations.length),
        ai: count(aiReview, r => r.recommendations.length)
    };
    const tabs = TAB_IDS.filter(id => isOn(id)).map(id => ({
        key: id,
        label: t(`tabs.${id}`),
        badge: badges[id],
        disabled: id === 'ai' && !aiStatus.enabled
    }));
    const enabledTabs = tabs.filter(tab => !tab.disabled);
    const currentTab = enabledTabs.some(tab => tab.key === activeTab) ? activeTab : (enabledTabs[0] || {}).key;
    const total = tabs.reduce((sum, tab) => sum + (tab.badge || 0), 0);

    useEffect(() => {
        onCount(total);
    }, [onCount, total]);

    const busy = status === 'loading' || status === 'analyzing';

    return (
        <>
            <div className={styles.toolbar}>
                <Button size="big"
                        color="accent"
                        icon={<Reload/>}
                        label={t('drawer.rerun')}
                        isDisabled={busy}
                        onClick={() => setRunId(id => id + 1)}/>
                <Button variant="outlined"
                        icon={<Download/>}
                        label={t('drawer.export')}
                        isDisabled={!results}
                        onClick={exportJson}/>
                <Button variant="ghost"
                        icon={<Visibility/>}
                        label={previewCollapsed ? t('drawer.showPreview') : t('drawer.hidePreview')}
                        aria-expanded={!previewCollapsed}
                        onClick={() => setPreviewCollapsed(c => !c)}/>
                {auditedAt && (
                    <Typography variant="caption" className={styles.lastAudit}>
                        {auditedBy ?
                            t('drawer.lastAuditBy', {date: formatDate(auditedAt, language), user: auditedBy}) :
                            t('drawer.lastAudit', {date: formatDate(auditedAt, language)})}
                    </Typography>
                )}
                <AuditTrend runs={history} language={language}/>
            </div>

            <div className={`${styles.preview} ${previewCollapsed ? styles.previewCollapsed : ''}`}>
                {frameVisible && (
                    <iframe
                        key={runId}
                        ref={frameRef}
                        title={t('drawer.preview')}
                        src={previewUrl}
                        className={styles.frame}
                        onLoad={handleFrameLoad}
                    />
                )}
            </div>

            {stale && (
                <div className={styles.stale}>
                    <Banner variant="warning" title={t('drawer.staleTitle')}>
                        <div className={styles.staleRow}>
                            <Typography>{t('drawer.stale')}</Typography>
                            <Button size="small" variant="outlined" label={t('drawer.rerun')} onClick={() => setRunId(id => id + 1)}/>
                        </div>
                    </Banner>
                </div>
            )}

            <div className={styles.tabBar}>
                <Tab className={styles.tabs}>
                    {tabs.map(tab => (
                        <HelpTab key={tab.key}
                                 help={t(`tabHelp.${tab.key}`)}
                                 note={tab.disabled ? t('tabs.aiNotConfigured') : (tab.badge ? t('tabs.badgeTooltip', {count: tab.badge}) : undefined)}
                                 isSelected={currentTab === tab.key}
                        >
                            <TabItem id={`audit-tab-${tab.key}`}
                                     aria-controls="audit-tab-panel"
                                     label={tab.label}
                                     isSelected={currentTab === tab.key}
                                     isDisabled={tab.disabled}
                                     icon={tab.badge ? <Badge label={String(tab.badge)} color="danger"/> : undefined}
                                     onClick={() => setActiveTab(tab.key)}/>
                        </HelpTab>
                    ))}
                </Tab>
            </div>

            <div id="audit-tab-panel" role="tabpanel" aria-labelledby={currentTab ? `audit-tab-${currentTab}` : undefined} className={styles.content}>
                {busy && (
                    <div className={styles.status}>
                        <Loader size="small"/>
                        <Typography>{t(status === 'loading' ? 'drawer.loading' : 'drawer.analyzing')}</Typography>
                    </div>
                )}
                {status === 'error' && (
                    <Banner variant="danger" title={t('drawer.error')}>
                        <Typography>{error}</Typography>
                    </Banner>
                )}
                {status === 'ready' && results && (
                    <>
                    {results.incomplete && results.incomplete[RESULT_KEYS[currentTab]] && (
                        <IncompleteStep outcome={results.incomplete[RESULT_KEYS[currentTab]]} onRerun={() => setRunId(id => id + 1)}/>
                    )}
                    {currentTab === 'accessibility' && results.a11y &&
                        <AccessibilityTab
                            result={results.a11y}
                            onHighlight={highlight}
                            assist={{
                                aiStatus,
                                assist: altAssist,
                                phase: altPhase,
                                error: altError,
                                language,
                                count: imagesWithoutAlt(results),
                                onGenerate: generateAltAssist,
                                onHighlight: highlight
                            }}
                        />}
                    {currentTab === 'seo' && results.seo &&
                        <SeoTab
                            result={results.seo}
                            assist={{
                                aiStatus,
                                assist: seoAssist,
                                phase: seoAssistPhase,
                                error: seoAssistError,
                                language,
                                onGenerate: generateSeoAssist,
                                onHighlightText: highlightText
                            }}
                        />}
                    {currentTab === 'links' && results.links &&
                        <LinksTab result={results.links} onHighlight={highlight}/>}
                    {currentTab === 'jahia' && results.jahia &&
                        <JahiaTab result={results.jahia} onHighlightText={highlightText}/>}
                    {currentTab === 'ai' &&
                        <AiTab
                            status={aiStatus}
                            review={aiReview}
                            phase={aiPhase}
                            error={aiError}
                            onGenerate={generateAiReview}
                            onHighlightText={highlightText}
                        />}
                    {currentTab === 'vitals' && results.vitals &&
                        <VitalsTab result={results.vitals}/>}
                    {currentTab === 'readability' && results.readability &&
                        <ReadabilityTab
                            result={results.readability}
                            assist={{
                                aiStatus,
                                assist: simplifyAssist,
                                phase: simplifyPhase,
                                error: simplifyError,
                                language,
                                count: results.readability.longSentences || 0,
                                onGenerate: generateSimplifyAssist,
                                onHighlightText: highlightText
                            }}
                        />}
                    {currentTab === 'ecodesign' && results.ecodesign &&
                        <EcodesignTab result={results.ecodesign}/>}
                    </>
                )}
            </div>
        </>
    );
}

AuditSection.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // isOn(tabId): whether the site switched that tab on
    isOn: PropTypes.func.isRequired,
    // {configured, provider, model, images} from the site settings
    ai: PropTypes.object,
    onCount: PropTypes.func.isRequired
};
