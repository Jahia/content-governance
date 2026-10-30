import React, {useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useGovernanceTranslation} from '../../common/i18n';
import {useSiteInfo} from '@jahia/data-helper';
import {Button, Download, Header, LayoutContent, Tab, TabItem} from '@jahia/moonstone';
import {RobotsControlPanel} from '../tabs/RobotsControlPanel';
import {LlmsPanel} from '../tabs/LlmsPanel';
import {VisibilityPanel} from '../tabs/VisibilityPanel';
import {SiteScorePanel} from '../tabs/SiteScorePanel';
import {SitemapPanel} from '../tabs/SitemapPanel';
import {LinksPanel} from '../tabs/LinksPanel';
import {VanityPanel} from '../tabs/VanityPanel';
import {FreshnessPanel} from '../tabs/FreshnessPanel';
import {LanguagesPanel} from '../tabs/LanguagesPanel';
import {SchemaPanel} from '../tabs/SchemaPanel';
import {ReportPanel} from '../tabs/ReportPanel';
import {CITATION_VIEWS, SiteCitationsPanel} from '../tabs/SiteCitationsPanel';
import {fetchReportStatus} from '../api/geoReport';
import {scanStatus} from '../api/siteScore';
import {exportCsv, exportJson} from './exportScan';
import {languageLabel} from '../util/languageFlag';
import styles from './GeoDashboard.module.css';


/**
 * Ten panels is a list, not a structure. These are the same three groups the
 * drawer already sorts its checks into - can a crawler reach it, is what
 * arrives usable, site-level files - so an editor learns one vocabulary and
 * meets it again here rather than a second taxonomy invented for the dashboard.
 *
 * The site score sits outside them because it is the summary of all three.
 */
const GROUPS = [
    // The written report is always listed, greyed out while no model is configured.
    {id: 'overview', tabs: ['score', 'report']},
    {id: 'reach', tabs: ['visibility', 'links', 'vanity']},
    {id: 'usable', tabs: ['schema', 'languages', 'freshness']},
    {id: 'files', tabs: ['sitemap', 'robots', 'llms']},
    // Not a readiness check: what AI answers actually do with the site, from BotRank (screen B).
    {id: 'citations', tabs: CITATION_VIEWS.map(v => `citations-${v}`)}
];

/**
 * Site-level GEO settings, under Additional > SEO. Rendered by the route only while the site keeps the
 * GEO section switched on; the citations group follows the Citations tab switch.
 *
 * The drawer answers a question about one page. robots.txt and llms.txt are
 * properties of the whole site, so they are shown here, and the drawer only reports
 * on them. robots.txt is edited here; llms.txt is written in the llms module's own
 * screen and shown read only.
 *
 * Laid out like the other settings panels: LayoutContent with a Header whose
 * title names the site and the language, and the two files on their own tabs.
 */
export const GeoDashboard = ({isOn}) => {
    const {t} = useGovernanceTranslation('geo');
    const siteKey = useSelector(state => state.site);
    const language = useSelector(state => state.language);
    const uilang = useSelector(state => state.uilang);
    const [group, setGroup] = useState(GROUPS[0].id);
    const [tab, setTab] = useState(GROUPS[0].tabs[0]);
    const [reportEnabled, setReportEnabled] = useState(false);
    const [hasScan, setHasScan] = useState(false);
    const [exporting, setExporting] = useState(false);

    // The written report needs a configured provider. The answer never carries
    // the key, only whether there is one.
    //
    // Asked per site rather than once, because the server now resolves the site
    // to decide whether this caller may be told at all (JAHIA-SEC-432). A
    // caller without `publish` on it gets a 403, which the catch below reads the
    // same way as "no provider": the tab is not offered.
    useEffect(() => {
        if (!siteKey) {
            return undefined;
        }
        let alive = true;
        fetchReportStatus({path: `/sites/${siteKey}`, language})
            .then(s => alive && setReportEnabled(Boolean(s && s.enabled)))
            .catch(() => alive && setReportEnabled(false));
        return () => {
            alive = false;
        };
    }, [siteKey, language]);

    // The exports are built from the last site scan, which the panels each read a part of.
    useEffect(() => {
        if (!siteKey) {
            return undefined;
        }
        let alive = true;
        scanStatus({path: `/sites/${siteKey}`, language})
            .then(s => alive && setHasScan(Boolean(s?.run)))
            .catch(() => alive && setHasScan(false));
        return () => {
            alive = false;
        };
    }, [siteKey, language]);

    // Read again at click time: a scan started from the Site score tab since the page opened is
    // the one exported.
    const exportScan = useCallback(async format => {
        setExporting(true);
        try {
            const scan = await scanStatus({path: `/sites/${siteKey}`, language});
            if (format === 'csv') {
                exportCsv(siteKey, scan, t);
            } else {
                exportJson(siteKey, scan);
            }
        } catch (e) {
            console.error('[content-governance] GEO export failed', e);
            globalThis.jahia?.toastDispatcher?.add({message: t('export.error'), variant: 'error'});
        } finally {
            setExporting(false);
        }
    }, [siteKey, language, t]);

    const groups = useMemo(() => GROUPS.filter(g => g.id !== 'citations' || isOn('citations')), [isOn]);

    const {siteInfo} = useSiteInfo({
        siteKey,
        displayLanguage: language,
        uiLanguage: uilang || language
    });

    if (!siteKey) {
        return null;
    }

    if (!siteInfo || !siteInfo.displayName) {
        // LayoutContent's isLoading replaces the content with its own loader.
        return <LayoutContent hasPadding isLoading/>;
    }

    // The site's own name for this language, so the header reads the way the
    // rest of jContent does rather than showing a technical site key.
    const current = (siteInfo.languages || []).find(l => l.language === language);
    const label = languageLabel(language, current && (current.uiLanguageDisplayName || current.displayName));

    // Any path inside the site resolves to the site node server-side, and the
    // site node itself is the most honest thing to send from a site-level page.
    const sitePath = `/sites/${siteKey}`;
    const activeGroup = groups.find(g => g.id === group) || groups[0];

    return (
        <LayoutContent
            hasPadding
            className={styles.layout}
            header={
                <Header
                    title={t('dashboard.title', {site: siteInfo.displayName, language: label})}
                    mainActions={[
                        <Button key="csv" variant="outlined" size="big" icon={<Download/>} label={t('export.csv')}
                                isDisabled={!hasScan || exporting} onClick={() => exportScan('csv')}/>,
                        <Button key="json" variant="outlined" size="big" icon={<Download/>} label={t('export.json')}
                                isDisabled={!hasScan || exporting} onClick={() => exportScan('json')}/>
                    ]}
                    toolbarLeft={
                        <div className={styles.headerTabs}>
                            <Tab className={styles.headerTabsInner}>
                                {groups.map(g => (
                                    <TabItem
                                        key={g.id}
                                        id={`geo-dash-group-${g.id}`}
                                        aria-controls={`geo-dash-panel-${g.tabs[0]}`}
                                        label={t(`dashboard.group.${g.id}`)}
                                        isSelected={group === g.id}
                                        onClick={() => {
                                            setGroup(g.id);
                                            setTab(g.tabs[0]);
                                        }}
                                    />
                                ))}
                            </Tab>
                        </div>
                    }
                />
            }
        >
            <div className={styles.content}>
                {/*
                  * Second level, quieter than the first. Skipped entirely for a
                  * group with one panel, where a row of one tab would just be
                  * furniture.
                  */}
                {activeGroup.tabs.length > 1 && (
                    <div className={styles.subTabs}>
                        <Tab>
                            {activeGroup.tabs.map(id => {
                                const disabled = id === 'report' && !reportEnabled;
                                const item = (
                                    <TabItem
                                        key={id}
                                        id={`geo-dash-tab-${id}`}
                                        aria-controls={`geo-dash-panel-${id}`}
                                        label={t(`dashboard.tab.${id}`)}
                                        isSelected={tab === id}
                                        isDisabled={disabled}
                                        onClick={() => setTab(id)}
                                    />
                                );
                                return disabled ? <span key={id} title={t('report.notConfiguredTab')}>{item}</span> : item;
                            })}
                        </Tab>
                    </div>
                )}

                {/*
                  * Moonstone's Tab/TabItem already speak role="tablist" /
                  * role="tab" / aria-selected; the panel underneath was a
                  * bare conditionally-rendered component with no matching
                  * role="tabpanel", so the relationship stopped at the tabs.
                  * Labelled by whichever tab level actually controls this
                  * panel: the sub-tab when one is showing, otherwise the
                  * group tab (a group with one tab skips the sub-tab row).
                  */}
                <div
                    role="tabpanel"
                    id={`geo-dash-panel-${tab}`}
                    aria-labelledby={activeGroup.tabs.length > 1 ? `geo-dash-tab-${tab}` : `geo-dash-group-${group}`}
                    tabIndex={0}
                >
                    {tab === 'score' && <SiteScorePanel path={sitePath} language={language}/>}
                    {tab === 'report' && (
                        <ReportPanel
                            path={sitePath}
                            language={language}
                            reportLanguage={uilang || language}
                            siteKey={siteKey}
                            siteName={siteInfo.displayName}
                        />
                    )}
                    {tab === 'languages' && <LanguagesPanel path={sitePath} language={language}/>}
                    {tab === 'sitemap' && <SitemapPanel path={sitePath} language={language}/>}
                    {tab === 'links' && <LinksPanel path={sitePath} language={language}/>}
                    {tab === 'vanity' && <VanityPanel path={sitePath} language={language}/>}
                    {tab === 'freshness' && <FreshnessPanel path={sitePath} language={language}/>}
                    {tab === 'schema' && <SchemaPanel path={sitePath} language={language}/>}
                    {tab === 'robots' && <RobotsControlPanel path={sitePath} language={language}/>}
                    {tab === 'llms' && <LlmsPanel path={sitePath} siteKey={siteKey} language={language}/>}
                    {tab === 'visibility' && <VisibilityPanel path={sitePath} language={language}/>}
                    {tab.startsWith('citations-') && (
                        <SiteCitationsPanel path={sitePath} language={language} view={tab.slice('citations-'.length)}/>
                    )}
                </div>
            </div>
        </LayoutContent>
    );
};

GeoDashboard.propTypes = {
    // isOn(tabId): whether the site switched that GEO tab on
    isOn: PropTypes.func.isRequired
};
