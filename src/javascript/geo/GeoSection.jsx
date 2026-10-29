import React, {useCallback, useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../common/i18n';
import {Badge, Banner, Button, Tab, TabItem, Typography} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import {runCrawlerCheck} from './api/crawlerCheck';
import {ScoreTab} from './tabs/ScoreTab';
import {CrawlerTab} from './tabs/CrawlerTab';
import {SiteFilesTab} from './tabs/SiteFilesTab';
import {SchemaTab} from './tabs/SchemaTab';
import {CitationsTab} from './tabs/CitationsTab';
import {fetchPageCitations} from './api/citations';
import tabStyles from './tabs/Tabs.module.css';
import styles from './GeoSection.module.css';
import {fetchErrorHint} from './util/fetchError';

const CACHE_SCHEMA = 8;

// Results are cached per page and language so reopening the drawer is instant.
// Bump CACHE_SCHEMA whenever the report shape changes, or an old cached entry
// will be restored into a UI that no longer understands it.
const cacheKey = (path, language) => `content-governance:geo:${CACHE_SCHEMA}:${language}:${path}`;

function loadCached(path, language) {
    try {
        const raw = window.localStorage.getItem(cacheKey(path, language));
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
        window.localStorage.setItem(
            cacheKey(path, language),
            JSON.stringify({schema: CACHE_SCHEMA, timestamp: Date.now(), report})
        );
    } catch (e) {
        // localStorage unavailable or full. Caching is a convenience, not a requirement.
    }
}

/** Things worth a badge on the Site files tab: no llms.txt, or an AI bot disallowed. */
function filesIssueCount(report) {
    const files = report.siteFiles || {};
    let n = 0;
    if (!(files.llms && files.llms.present)) {
        n++;
    }
    if (files.robots && files.robots.disallowedAiBotCount) {
        n += files.robots.disallowedAiBotCount;
    }
    if (files.robots && files.robots.present === false) {
        n++;
    }
    return n;
}

/** A stored scan date for a sentence; an unparsable value is shown as stored. */
const formatScanDate = iso => {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? String(iso || '') : d.toLocaleString();
};

/**
 * The tabs of the section the site switched on: the report's four when there is a published
 * report, then citations, greyed out while the site has no BotRank connection.
 */
function sectionTabs(report, isOn, citations, t) {
    const tabs = report ? [
        {id: 'score', label: t('score.tab'), count: report.score ? report.score.criticalFailed : 0},
        {id: 'crawler', label: t('crawler.tab'), count: report.blockedCount},
        {id: 'files', label: t('files.tab'), count: filesIssueCount(report)},
        {
            id: 'schema',
            label: t('schema.tab'),
            count: report.schema && report.schema.mapped && !report.schema.valid ? 1 : 0
        }
    ].filter(tab => isOn(tab.id)) : [];
    if (isOn('citations')) {
        tabs.push({id: 'citations', label: t('citations.tab'), count: 0, disabled: !citations.available});
    }

    return tabs;
}

/**
 * The GEO readiness section of the drawer: can AI crawlers read this page, and what the site's
 * files, structured data and BotRank citations say about it. The check runs on demand (it fetches
 * the public page once per AI crawler); citations load on their own from the site's last sync.
 */
export const GeoSection = ({path, language, isOn, onCount}) => {
    const {t} = useGovernanceTranslation('geo');
    const [report, setReport] = useState(null);
    const [ranAt, setRanAt] = useState(null);
    const [phase, setPhase] = useState('idle');
    const [tab, setTab] = useState(null);
    const [error, setError] = useState(null);
    // Citations come from the site's last sync, not from the crawler check: loaded once per mount.
    // configured false (no binding) and 403 (tab or module off for this site) both mean unavailable.
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
        if (!isOn('citations')) {
            setCitationsState('off');
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
    }, [path, language, isOn]);

    const run = useCallback(async () => {
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

    const citationsAvailable = citationsState === 'ready' || citationsState === 'failed';
    const reportTabs = Boolean(report && report.published);
    const tabs = sectionTabs(reportTabs ? report : null, isOn, {available: citationsAvailable}, t);
    const enabledTabs = tabs.filter(item => !item.disabled);
    // The selected tab, else the first one that can be opened (only citations without a report).
    const activeTab = enabledTabs.some(item => item.id === tab) ? tab : (enabledTabs[0] || {}).id;
    const total = tabs.reduce((sum, item) => sum + (item.count || 0), 0);

    useEffect(() => {
        onCount(total);
    }, [onCount, total]);

    return (
        <>
            <div className={styles.toolbar}>
                <Button
                    size="big"
                    color="accent"
                    isDisabled={phase === 'running'}
                    label={phase === 'running' ? t('drawer.running') : (report ? t('drawer.rerun') : t('drawer.run'))}
                    onClick={run}
                />
                {ranAt && (
                    <Typography variant="caption" className={styles.meta}>
                        {t('drawer.lastRun', {date: new Date(ranAt).toLocaleString()})}
                    </Typography>
                )}
            </div>

            {/* role="alert" carries an implicit assertive live region, so a failed check is announced without a second attribute. */}
            {error && <div className={styles.error} role="alert">{error}</div>}

            <div className={styles.body}>
                {phase === 'idle' && !report && (
                    <Typography variant="body" className={styles.empty}>{t('drawer.subtitle')}</Typography>
                )}
                {/*
                  * Absolute facts about the page, above the tabs rather than
                  * inside one: they frame every number below them, and the
                  * drawer does not open on the tab that used to hold them.
                  */}
                {/*
                  * Its own wording, not the score check's. A check label is
                  * phrased as the passing condition because it sits beside a
                  * pass mark; as a failure banner that reads as the opposite of
                  * what happened.
                  */}
                {report && report.visibility && report.visibility.guestReadable === false && (
                    <Banner variant="danger" title={t('visibility.gatedTitle')}>
                        {t('visibility.gated')}
                    </Banner>
                )}

                {report && report.visibility && report.visibility.noindex && (
                    <Banner variant="info" title={t('visibility.noindexTitle')}>
                        {t('visibility.noindex')}
                    </Banner>
                )}

                {/*
                  * The drawer never fetches the sitemap: these facts come from the last site
                  * scan, so they carry its date, and a scan that could not reach the file says
                  * so rather than leaving the page silently "not missing".
                  */}
                {report && report.sitemap && report.sitemap.unreachable && (
                    <Banner variant="warning" title={t('sitemap.unreachablePageTitle')}>
                        {t('sitemap.unreachablePage', {url: report.sitemap.url, date: formatScanDate(report.sitemap.checkedAt)})}
                        {report.sitemap.error && ` ${fetchErrorHint(t, report.sitemap.error)}`}
                    </Banner>
                )}

                {report && report.sitemap && report.sitemap.missing && (
                    <Banner variant="warning" title={t('sitemap.notListedTitle')}>
                        {t('sitemap.notListed')}
                        {report.sitemap.checkedAt && ` ${t('sitemap.fromScan', {date: formatScanDate(report.sitemap.checkedAt)})}`}
                    </Banner>
                )}

                {/*
                  * Listed and noindex is two of our own files contradicting each
                  * other, and the contradiction is only visible per page.
                  */}
                {report && report.sitemap && report.sitemap.noindexListed && (
                    <Banner variant="warning" title={t('sitemap.noindexListedTitle')}>
                        {t('sitemap.noindexListedPage')}
                        {report.sitemap.checkedAt && ` ${t('sitemap.fromScan', {date: formatScanDate(report.sitemap.checkedAt)})}`}
                    </Banner>
                )}

                {/*
                  * GEO-22. Nothing pointing here is the loudest version of this;
                  * only the menu pointing here is the quieter one. Both are read
                  * from the last site scan, which is the only thing that sees
                  * the whole graph.
                  */}
                {report && report.links && report.links.content === 0 && report.links.nav === 0 && (
                    <Banner variant="warning" title={t('links.orphanTitle')}>
                        {t('links.orphanPage')}
                    </Banner>
                )}

                {report && report.links && report.links.content === 0 && report.links.nav > 0 && (
                    <Banner variant="info" title={t('links.navOnlyTitle')}>
                        {t('links.navOnlyPage', {count: report.links.nav})}
                    </Banner>
                )}

                {/*
                  * GEO-25. One banner for whichever conflict applies: the three
                  * kinds are mutually exclusive per page, and the detail names
                  * the addresses so the fix is obvious.
                  */}
                {/*
                  * GEO-20. Directly actionable while editing: the author in
                  * front of this page is the person who would write the missing
                  * translation.
                  */}
                {report && report.languages && (report.languages.missing || []).length > 0 && (
                    <Banner variant="info" title={t('languages.pageMissingTitle')}>
                        {t('languages.pageMissing', {
                            languages: report.languages.missing.join(', ')
                        })}
                    </Banner>
                )}

                {report && report.languages && (report.languages.notPublished || []).length > 0 && (
                    <Banner variant="warning" title={t('languages.pageUnpublishedTitle')}>
                        {t('languages.pageUnpublished', {
                            languages: report.languages.notPublished.join(', ')
                        })}
                    </Banner>
                )}

                {report && report.vanity && (
                    <Banner
                        variant={report.vanity.kind === 'unresolvable' ? 'warning' : 'info'}
                        title={t(`vanity.page.${report.vanity.why || report.vanity.kind}Title`)}
                    >
                        {t(`vanity.page.${report.vanity.why || report.vanity.kind}`, {
                            detail: report.vanity.detail
                        })}
                    </Banner>
                )}

                {report && report.sitemap && report.sitemap.staleDetail && (
                    <Banner variant="info" title={t('sitemap.staleTitle')}>
                        {t('sitemap.stalePage', {detail: report.sitemap.staleDetail})}
                        {report.sitemap.checkedAt && ` ${t('sitemap.fromScan', {date: formatScanDate(report.sitemap.checkedAt)})}`}
                    </Banner>
                )}

                {tabs.length > 0 && (
                    <Tab className={tabStyles.tabs}>
                        {tabs.map(item => (
                            <HelpTab key={item.id}
                                     help={t(`tabHelp.${item.id}`)}
                                     note={item.disabled ? t('citations.notConfiguredTab') : undefined}
                            >
                                <TabItem
                                    id={`geo-drawer-tab-${item.id}`}
                                    aria-controls={`geo-drawer-panel-${item.id}`}
                                    label={item.label}
                                    isSelected={activeTab === item.id}
                                    isDisabled={Boolean(item.disabled)}
                                    icon={item.count > 0 ? <Badge label={String(item.count)} color="danger"/> : undefined}
                                    onClick={() => setTab(item.id)}
                                />
                            </HelpTab>
                        ))}
                    </Tab>
                )}

                {/*
                  * An unpublished page has no public URL, so there is nothing to fetch and every
                  * report tab is gated on `published`. Said here, outside the tab system, so the
                  * answer does not depend on which tab happens to be selected.
                  */}
                {report && !report.published && (
                    <Banner variant="info" title={t('crawler.verdict.unpublishedTitle')}>
                        {t('crawler.verdict.unpublished')} {t('error.notPublished')}
                    </Banner>
                )}

                {activeTab && (
                    <div
                        role="tabpanel"
                        id={`geo-drawer-panel-${activeTab}`}
                        aria-labelledby={`geo-drawer-tab-${activeTab}`}
                        tabIndex={0}
                    >
                        {reportTabs && activeTab === 'score' && <ScoreTab report={report}/>}
                        {reportTabs && activeTab === 'crawler' && <CrawlerTab report={report}/>}
                        {reportTabs && activeTab === 'files' && <SiteFilesTab report={report}/>}
                        {reportTabs && activeTab === 'schema' && <SchemaTab report={report}/>}
                        {activeTab === 'citations' && <CitationsTab data={citations} failed={citationsState === 'failed'}/>}
                    </div>
                )}
            </div>
        </>
    );
};

GeoSection.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // isOn(tabId): whether the site switched that tab on
    isOn: PropTypes.func.isRequired,
    onCount: PropTypes.func.isRequired
};
