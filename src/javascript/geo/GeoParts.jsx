import React from 'react';
import PropTypes from 'prop-types';
import {Banner, Button, Reload, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {ScoreTab, FailedChecks} from './tabs/ScoreTab';
import {CrawlerTab} from './tabs/CrawlerTab';
import {SiteFilesTab} from './tabs/SiteFilesTab';
import {SchemaTab} from './tabs/SchemaTab';
import {CitationsTab} from './tabs/CitationsTab';
import {fetchErrorHint} from './util/fetchError';
import {publishedReport} from './geoState';
import styles from './GeoParts.module.css';

/**
 * What the GEO check shows in the drawer: its toolbar and the facts of the page, once per domain that reads
 * it, the view of each GEO subdomain, and the failed checks of the rules that a site places in another
 * subdomain, such as `geo.content`. The state comes from `useGeoCheck`.
 */

/** A stored scan date for a sentence; an unparsable value is shown as stored. */
const formatScanDate = iso => {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? String(iso || '') : d.toLocaleString();
};

// The label of the run button: running, run before, never run
const runLabelKey = (phase, report) => {
    if (phase === 'running') {
        return 'drawer.running';
    }
    return report ? 'drawer.rerun' : 'drawer.run';
};

/** "Run check" or "Run again", and the date of the last check. */
export function GeoToolbar({geo}) {
    const {t} = useGovernanceTranslation('geo');
    return (
        <>
            <Button
                variant="outlined"
                icon={geo.report ? <Reload/> : undefined}
                isDisabled={geo.phase === 'running'}
                label={t(runLabelKey(geo.phase, geo.report))}
                onClick={geo.run}
            />
            {geo.ranAt && (
                <Typography variant="caption" className={styles.meta}>
                    {t('drawer.lastRun', {date: new Date(geo.ranAt).toLocaleString()})}
                </Typography>
            )}
        </>
    );
}

GeoToolbar.propTypes = {
    geo: PropTypes.object.isRequired
};

/**
 * Why the check failed, what it is for while it never ran, and the facts of the page that frame every number
 * of the GEO views: they sit above the sections, so the answer does not depend on which section is open.
 */
export function GeoFacts({geo}) {
    const {t} = useGovernanceTranslation('geo');
    const {report, phase, error} = geo;
    return (
        <>
            {/* role="alert" carries an implicit assertive live region, so a failed check is announced without a second attribute. */}
            {error && <div className={styles.error} role="alert">{error}</div>}

            {phase === 'idle' && !report && (
                <Typography variant="body" className={styles.empty}>{t('drawer.subtitle')}</Typography>
            )}

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

            {/*
              * GEO-25. One banner for whichever conflict applies: the three
              * kinds are mutually exclusive per page, and the detail names
              * the addresses so the fix is obvious.
              */}
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

            {/*
              * An unpublished page has no public URL, so there is nothing to fetch and every
              * report view is gated on `published`. Said here, above the sections, so the
              * answer does not depend on which section is open.
              */}
            {report && !report.published && (
                <Banner variant="info" title={t('crawler.verdict.unpublishedTitle')}>
                    {t('crawler.verdict.unpublished')} {t('error.notPublished')}
                </Banner>
            )}
        </>
    );
}

GeoFacts.propTypes = {
    geo: PropTypes.object.isRequired
};

/**
 * The view of a GEO subdomain: the score, the crawler access, the site files, the structured data or the
 * citations. A view of the report says to run the check while the page has no report of a published page.
 */
export function GeoSubdomainView({geo, id, path}) {
    const {t} = useGovernanceTranslation('geo');
    if (id === 'geo.citations') {
        return geo.citationsState === 'notConfigured' ?
            <Typography>{t('citations.notConfiguredTab')}</Typography> :
            <CitationsTab data={geo.citations} failed={geo.citationsState === 'failed'}/>;
    }
    const report = publishedReport(geo);
    if (!report) {
        return <Typography variant="caption" className={styles.empty}>{t('drawer.runFirst')}</Typography>;
    }
    switch (id) {
        case 'geo.score':
            return <ScoreTab report={report} path={path}/>;
        case 'geo.crawlers':
            return <CrawlerTab report={report}/>;
        case 'geo.files':
            return <SiteFilesTab report={report}/>;
        case 'geo.schema':
            return <SchemaTab report={report}/>;
        default:
            return null;
    }
}

GeoSubdomainView.propTypes = {
    geo: PropTypes.object.isRequired,
    // The subdomain
    id: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired
};

/**
 * The checks of the GEO score that failed on the rules that a site places in a subdomain with no GEO view,
 * such as `geo.content`. Nothing while the page has no report, or when none failed.
 */
export function GeoFindingsOf({geo, path, ruleIds}) {
    const report = publishedReport(geo);
    return report ? <FailedChecks report={report} path={path} ruleIds={ruleIds}/> : null;
}

GeoFindingsOf.propTypes = {
    geo: PropTypes.object.isRequired,
    path: PropTypes.string.isRequired,
    // The rules on in the subdomain
    ruleIds: PropTypes.instanceOf(Set).isRequired
};
