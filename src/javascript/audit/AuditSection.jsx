import React, {useCallback, useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../common/i18n';
import {useDrawerDisplay} from '../common/useDrawerDisplay';
import {useApolloClient} from '@apollo/client';
import {fetchRules} from './rules';
import {checkPage} from './pageCheck';
import {rulesOfTabsOn, runChecks} from './runChecks';
import {browserTabs, RESULT_KEYS, SERVER_KEYS, withBrowserResults} from './browserResults';
import {removeToolingElements} from './tooling';
import {runStep} from './timeLimit';
import {fetchPageAudits, fetchPageLastModified, saveAudit, storedResults} from './auditHistory';
import {auditFindings} from '../drawer/findings';
import {AccessibilityTab} from './tabs/AccessibilityTab';
import {VitalsTab} from './tabs/VitalsTab';
import {ReadabilityTab} from './tabs/ReadabilityTab';
import {EcodesignTab} from './tabs/EcodesignTab';
import {SeoTab} from './tabs/SeoTab';
import {LinksTab} from './tabs/LinksTab';
import {JahiaTab} from './tabs/JahiaTab';
import {IncompleteStep} from './tabs/IncompleteStep';
import {NotChecked} from './tabs/NotChecked';
import {Banner, Button, Download, Loader, Reload, Typography, Visibility} from '@jahia/moonstone';
import {CollapsibleSection, ExpandAll} from '../common/CollapsibleSection';
import {defaultOpen, useOpenSections} from '../common/openSections';
import {findByText, findEditFrameDocument, locateInFrame} from './editFrame';
import styles from './AuditSection.module.css';
import {toLocaleTag} from '../common/locale';
import {tabCounts} from './tabCounts';
import {HighlightContext} from './HighlightContext';

// Let client islands hydrate and layout settle before measuring
const SETTLE_MS = 2500;

// The frame's load event waits for every image, font and script of the page: one request that never
// answers would hold the audit on "Loading" for good. Past this, a page whose document is parsed is
// audited anyway.
const FRAME_LOAD_LIMIT_MS = 20_000;

// Audits a page keeps for every editor of the site (AuditHistoryService.MAX_RUNS)
const AUDITS_KEPT = 10;

// The time the check of the server may take before its tabs are left out of the audit (runStep): it gives
// the tabs SEO, Readability, Links and Jahia health. Each check of the browser has its own (runChecks.js).
const PAGE_LIMIT_MS = 60_000;

// The tabs that read the check of the server: its results, or its HTML rules
const SERVER_TABS = ['seo', 'readability', 'links', 'jahia', 'accessibility'];

// Why a tab misses the facts of the repository: the server rendered the page, and could not read its state
const UNREAD = {incomplete: 'failed', message: 'the server could not read the repository'};

// Results are cached per page+language in localStorage so reopening the
// drawer restores the last audit instantly (with its timestamp shown in the
// header). "Re-run" always performs a fresh audit. LRU-capped.
const CACHE_PREFIX = 'content-governance:audit:';
const CACHE_MAX_ENTRIES = 10;
// Bump whenever the cached `results` shape changes (e.g. a new key of a tab),
// so entries written by an older module version are ignored instead of
// restored into UI that expects the new shape.
const CACHE_SCHEMA = 7;
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
    } catch (e) {
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
                } catch (e) {
                    // Unparseable entry: treat as oldest
                }

                return {k, timestamp};
            })
            .sort((a, b) => b.timestamp - a.timestamp);
        entries.slice(keep).forEach(e => globalThis.localStorage.removeItem(e.k));
    } catch (e) {
        // localStorage unavailable
    }
}

function saveCachedAudit(path, language, entry) {
    const write = () => globalThis.localStorage.setItem(cacheKey(path, language), JSON.stringify(entry));
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

/** The subdomains of the section, in order; each id is also its feature id (`audit.<id>`). */
const TAB_IDS = ['accessibility', 'seo', 'vitals', 'readability', 'ecodesign', 'links', 'jahia'];

// What the tooltip of a subdomain adds to its help: how many issues it holds
const sectionNote = (section, t) => (section.count ? t('tabs.badgeTooltip', {count: section.count}) : undefined);

/** The results of one subdomain, or nothing when the audit holds none for it. */
function TabContent({id, results, onHighlight, onHighlightText}) {
    switch (id) {
        case 'accessibility':
            return results.a11y ? <AccessibilityTab result={results.a11y} onHighlight={onHighlight}/> : null;
        case 'seo':
            return results.seo ? <SeoTab result={results.seo}/> : null;
        case 'links':
            return results.links ? <LinksTab result={results.links} onHighlight={onHighlight}/> : null;
        case 'jahia':
            return results.jahia ? <JahiaTab result={results.jahia} onHighlightText={onHighlightText}/> : null;
        case 'vitals':
            return results.vitals?.metrics ? <VitalsTab result={results.vitals}/> : null;
        case 'readability':
            return results.readability ? <ReadabilityTab result={results.readability} onHighlight={onHighlight}/> : null;
        case 'ecodesign':
            return results.ecodesign ? <EcodesignTab result={results.ecodesign}/> : null;
        default:
            return null;
    }
}

TabContent.propTypes = {
    id: PropTypes.string.isRequired,
    results: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired,
    onHighlightText: PropTypes.func.isRequired
};

const formatDate = (timestamp, language) => new Date(timestamp).toLocaleString(toLocaleTag(language));

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
    // The shape of the results goes with the page facts, which every stored run keeps
    const kept = {page: {...page, schema: CACHE_SCHEMA}};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab) && measured[key]) {
            kept[key] = measured[key];
        }
    }
    const missing = Object.keys(incomplete).filter(key => Object.entries(RESULT_KEYS).some(([tab, k]) => k === key && isOn(tab)));
    // A check of the server that did not finish leaves no tab of its own when they are all off, and its
    // page facts and HTML rules are still missing: such a run is never cached or stored either
    const partial = [...missing, ...(incomplete.page ? ['page'] : [])];
    if (partial.length > 0) {
        kept.incomplete = Object.fromEntries(partial.map(key => [key, incomplete[key]]));
    }
    return kept;
}

/**
 * Marks the tabs of the check of the server that miss their results: each of them when the check did not
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
 * Measures the page for the tabs the site keeps on. The check of the server gives the tabs SEO,
 * Readability, Links and Jahia health, with the findings that `frame.check` gives for the page. The
 * checks that modules register in the browser run on the preview frame: axe-core, the web vitals and
 * eco-design, and those of other modules. Their results join the tab of the subdomain of each rule, and a
 * rule that no check gave a result for is listed as not checked in its tab. The frame is refused when it
 * shows an error page, and its editing tooling is stripped first.
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

    // Only the tabs the site keeps on are kept. The check of the server runs when a tab that reads it is
    // on, Accessibility included, and gives the page facts kept with each audit (title, description,
    // images without alt): it starts at once, since its request leaves no trace in the frame's own
    // resource statistics. The checks of the browser run meanwhile, one after the other.
    // A step that fails or runs out of time leaves its own tabs out; the other tabs are still shown,
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
 * The results of the tabs the site keeps on, or null when one of them is missing (a tab switched
 * back on since the audit was cached) or when an older version of the module stored them in a shape
 * that this version cannot show: a tab switched off is never shown nor exported. An audit of the
 * shape before #140 is kept, marked `legacy`: it shows its old text, with "Run again".
 */
function keepEnabled(results, isOn) {
    const schema = results.page?.schema;
    if (schema !== CACHE_SCHEMA && schema !== LEGACY_SCHEMA) {
        return null;
    }
    const kept = {page: results.page, ...(schema === LEGACY_SCHEMA ? {legacy: true} : {})};
    for (const [tab, key] of Object.entries(RESULT_KEYS)) {
        if (isOn(tab)) {
            if (!results[key]) {
                return null;
            }
            kept[key] = results[key];
        }
    }
    return kept;
}

/**
 * The Page quality section of the drawer: the page checked by the server (SEO, readability, links,
 * Jahia health) and rendered in a preview frame, measured there by the checks of the browser
 * (accessibility, vitals, eco-design, and those of other modules). Results are cached per page and
 * language; the section measures the page when it first shows and on every re-run.
 */
export function AuditSection({path, language, isOn, onCount, onFindings, requestedTab}) {
    const {t, i18n} = useGovernanceTranslation('audit');
    // The texts of the rules follow the editor's interface language, not the language of the content
    const uiLanguage = i18n?.language || 'en';
    const apolloClient = useApolloClient();
    // The "Show preview" button shows where the cfg of the server shows it
    const {previewButton} = useDrawerDisplay();
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
    const [previewCollapsed, setPreviewCollapsed] = useState(() => Boolean(findEditFrameDocument(path, language)));
    const frameRef = useRef(null);
    const previewRef = useRef(null);
    const highlightedRef = useRef(null);
    // The run whose analysis has started: the frame's load event and the load time limit both start
    // it, and a later load of the frame (a link followed in the preview) never starts it again.
    const analyzedRunRef = useRef(null);

    const previewUrl = `${globalThis.contextJsParameters?.contextPath || ''}/cms/render/default/${language}${path}.html?pageAuditRun=${runId}`;

    useEffect(() => {
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

    // Hidden behind the results, nothing of the preview takes focus or the pointer
    useEffect(() => {
        previewRef.current?.toggleAttribute('inert', previewCollapsed);
    }, [previewCollapsed]);

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
        a.download = `content-governance-audit-${path.split('/').filter(Boolean).pop() || 'page'}.json`;
        a.click();
        URL.revokeObjectURL(a.href);
    }, [results, path, language, previewUrl, auditedAt]);

    // The count of a subdomain is its issues to review; null-safe so a partial result never breaks
    // the list, and null for a subdomain the audit did not measure. Subdomains the site switched off
    // are left out.
    const counts = tabCounts(results);
    const sections = TAB_IDS.filter(id => isOn(id)).map(id => ({
        id,
        label: t(`tabs.${id}`),
        count: counts[id] ? counts[id].count : null,
        serious: Boolean(counts[id] && counts[id].serious),
        incomplete: Boolean(results?.incomplete?.[RESULT_KEYS[id]])
    }));
    const total = sections.reduce((sum, section) => sum + (section.count || 0), 0);
    // A new audit opens the sections with a major defect again
    const {isOpen, setOpen} = useOpenSections(defaultOpen(sections), auditedAt);

    useEffect(() => {
        onCount(total);
    }, [onCount, total]);

    useEffect(() => {
        onFindings(status === 'ready' ? auditFindings(results, t) : []);
    }, [onFindings, status, results, t]);

    // A "Fix first" link opens its subdomain and brings it into view, once per request
    const handledRequest = useRef(null);
    useEffect(() => {
        if (requestedTab && requestedTab !== handledRequest.current) {
            handledRequest.current = requestedTab;
            setOpen([requestedTab.tab], true);
            document.getElementById(`audit-subdomain-${requestedTab.tab}`)?.scrollIntoView({block: 'start'});
        }
    }, [requestedTab, setOpen]);

    const busy = status === 'loading' || status === 'analyzing';
    const rerun = () => setRunId(id => id + 1);

    return (
        <>
            <div className={styles.toolbar}>
                <Button variant="outlined"
                        icon={<Reload/>}
                        label={t('drawer.rerun')}
                        isDisabled={busy}
                        onClick={rerun}/>
                <Button variant="outlined"
                        icon={<Download/>}
                        label={t('drawer.export')}
                        isDisabled={!results}
                        onClick={exportJson}/>
                {previewButton && (
                    <Button variant="ghost"
                            icon={<Visibility/>}
                            label={previewCollapsed ? t('drawer.showPreview') : t('drawer.hidePreview')}
                            aria-expanded={!previewCollapsed}
                            onClick={() => setPreviewCollapsed(c => !c)}/>
                )}
                {auditedAt && (
                    <Typography variant="caption" className={styles.lastAudit}>
                        {auditedBy ?
                            t('drawer.lastAuditBy', {date: formatDate(auditedAt, language), user: auditedBy}) :
                            t('drawer.lastAudit', {date: formatDate(auditedAt, language)})}
                    </Typography>
                )}
                <AuditTrend runs={history} language={language}/>
                {status === 'ready' && results && <ExpandAll ids={sections.map(section => section.id)} isOpen={isOpen} setOpen={setOpen}/>}
            </div>

            <div ref={previewRef}
                 className={`${styles.preview} ${previewCollapsed ? styles.previewCollapsed : ''}`}
                 aria-hidden={previewCollapsed || undefined}
            >
                {frameVisible && (
                    <iframe
                        key={runId}
                        ref={frameRef}
                        title={t('drawer.preview')}
                        src={previewUrl}
                        className={styles.frame}
                        tabIndex={previewCollapsed ? -1 : undefined}
                        onLoad={handleFrameLoad}
                    />
                )}
            </div>

            {status === 'ready' && results?.legacy && (
                <div className={styles.stale}>
                    <Banner variant="info" title={t('drawer.legacyTitle')}>
                        <div className={styles.staleRow}>
                            <Typography>{t('drawer.legacy')}</Typography>
                            <Button size="small" variant="outlined" label={t('drawer.runAgain')} onClick={rerun}/>
                        </div>
                    </Banner>
                </div>
            )}

            {stale && (
                <div className={styles.stale}>
                    <Banner variant="warning" title={t('drawer.staleTitle')}>
                        <div className={styles.staleRow}>
                            <Typography>{t('drawer.stale')}</Typography>
                            <Button size="small" variant="outlined" label={t('drawer.rerun')} onClick={rerun}/>
                        </div>
                    </Banner>
                </div>
            )}

            <HighlightContext.Provider value={highlight}>
            <div className={styles.content}>
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
                {status === 'ready' && results && sections.map(section => {
                    const key = RESULT_KEYS[section.id];
                    return (
                        <CollapsibleSection key={section.id}
                                            id={`audit-subdomain-${section.id}`}
                                            label={section.label}
                                            help={t(`tabHelp.${section.id}`)}
                                            note={sectionNote(section, t)}
                                            count={section.count}
                                            isSerious={section.serious}
                                            isOpen={isOpen(section.id)}
                                            onToggle={() => setOpen([section.id], !isOpen(section.id))}
                        >
                            {results.incomplete?.[key] && <IncompleteStep outcome={results.incomplete[key]} onRerun={rerun}/>}
                            <TabContent id={section.id} results={results} onHighlight={highlight} onHighlightText={highlightText}/>
                            {results[key]?.notChecked?.length > 0 && <NotChecked rules={results[key].notChecked}/>}
                        </CollapsibleSection>
                    );
                })}
            </div>
            </HighlightContext.Provider>
        </>
    );
}

AuditSection.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // isOn(tabId): whether the site switched that tab on
    isOn: PropTypes.func.isRequired,
    onCount: PropTypes.func.isRequired,
    // Reports the section's findings to the drawer's "Fix first" list (drawer/findings.js)
    onFindings: PropTypes.func.isRequired,
    // {tab, at}: a subdomain the drawer asks the section to open (a "Fix first" link)
    requestedTab: PropTypes.shape({tab: PropTypes.string, at: PropTypes.number})
};
