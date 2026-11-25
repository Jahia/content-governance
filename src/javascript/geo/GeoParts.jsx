import React from 'react';
import PropTypes from 'prop-types';
import {Button, Reload, Typography} from '@jahia/moonstone';
import {useDateFormat, useGovernanceTranslation} from '../common/i18n';
import {ScoreTab, FailedChecks} from './tabs/ScoreTab';
import {CrawlerTab} from './tabs/CrawlerTab';
import {SiteFilesTab} from './tabs/SiteFilesTab';
import {SchemaTab} from './tabs/SchemaTab';
import {CitationsTab} from './tabs/CitationsTab';
import {fetchErrorHint} from './util/fetchError';
import {pageFindingsOf, publishedReport} from './geoState';
import {ResultCard} from '../common/results/ResultCard';
import {ResultBanner} from '../common/results/ResultBanner';
import styles from './GeoParts.module.css';

/**
 * What the GEO check shows in the drawer: its toolbar and the facts of the page, once per domain that reads
 * it, the view of each GEO subdomain, and the failed checks of the rules that a site places in another
 * subdomain, such as `geo.content`. The state comes from `useGeoCheck`.
 */

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
    const {formatDateTime} = useDateFormat();
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
                    {t('drawer.lastRun', {date: formatDateTime(geo.ranAt)})}
                </Typography>
            )}
        </>
    );
}

GeoToolbar.propTypes = {
    geo: PropTypes.object.isRequired
};

/**
 * The state of the GEO check, above the sections: why it failed, what it is for while it never ran, and that
 * an unpublished page has nothing to fetch. The facts of the page that are findings, such as a page missing
 * from the sitemap, are result cards in their subdomain (`GeoPageFindings`).
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
              * An unpublished page has no public URL, so there is nothing to fetch and every
              * report view is gated on `published`. Said here, above the sections, so the
              * answer does not depend on which section is open.
              */}
            {report && !report.published && (
                <ResultBanner variant="info" title={t('crawler.verdict.unpublishedTitle')}>
                    {t('crawler.verdict.unpublished')} {t('error.notPublished')}
                </ResultBanner>
            )}
        </>
    );
}

GeoFacts.propTypes = {
    geo: PropTypes.object.isRequired
};

/** The title and the detail of each finding of `pageFindingsOf`, worded from the report. */
const PAGE_FINDING_TEXT = {
    gated: (report, t) => [t('visibility.gatedTitle'), t('visibility.gated')],
    noindex: (report, t) => [t('visibility.noindexTitle'), t('visibility.noindex')],
    notListed: (report, t) => [t('sitemap.notListedTitle'), t('sitemap.notListed')],
    noindexListed: (report, t) => [t('sitemap.noindexListedTitle'), t('sitemap.noindexListedPage')],
    stale: (report, t) => [t('sitemap.staleTitle'), t('sitemap.stalePage', {detail: report.sitemap.staleDetail})],
    vanity: (report, t) => {
        const key = `vanity.page.${report.vanity.why || report.vanity.kind}`;
        return [t(`${key}Title`), t(key, {detail: report.vanity.detail})];
    },
    orphan: (report, t) => [t('links.orphanTitle'), t('links.orphanPage')],
    navOnly: (report, t) => [t('links.navOnlyTitle'), t('links.navOnlyPage', {count: report.links.nav})],
    languagesMissing: (report, t) => [t('languages.pageMissingTitle'), t('languages.pageMissing', {languages: report.languages.missing.join(', ')})],
    languagesUnpublished: (report, t) => [t('languages.pageUnpublishedTitle'), t('languages.pageUnpublished', {languages: report.languages.notPublished.join(', ')})]
};

// The findings read from the last site scan carry its date
const FROM_SCAN = new Set(['notListed', 'noindexListed', 'stale']);

/**
 * The facts of the page that are findings in a subdomain, one result card each: a gated page in the
 * crawlers, a page missing from the sitemap in the site files, an orphan page in the content. In the site
 * files, a sitemap the last scan could not reach is a banner: it is no finding about the page.
 */
export function GeoPageFindings({report, id}) {
    const {t} = useGovernanceTranslation('geo');
    const {formatDateTime} = useDateFormat();
    const sitemap = report.sitemap || {};
    const scanDate = sitemap.checkedAt ? ` ${t('sitemap.fromScan', {date: formatDateTime(sitemap.checkedAt)})}` : '';
    return (
        <>
            {id === 'geo.files' && sitemap.unreachable && (
                <ResultBanner variant="warning" title={t('sitemap.unreachablePageTitle')}>
                    {t('sitemap.unreachablePage', {url: sitemap.url, date: formatDateTime(sitemap.checkedAt)})}
                    {sitemap.error && ` ${fetchErrorHint(t, sitemap.error)}`}
                </ResultBanner>
            )}
            {pageFindingsOf(report).filter(finding => finding.place === id).map(finding => {
                const [title, detail] = PAGE_FINDING_TEXT[finding.id](report, t);
                return <ResultCard key={finding.id} level={finding.level} title={title} detail={FROM_SCAN.has(finding.id) ? `${detail}${scanDate}` : detail}/>;
            })}
        </>
    );
}

GeoPageFindings.propTypes = {
    report: PropTypes.object.isRequired,
    // The subdomain
    id: PropTypes.string.isRequired
};

/**
 * The view of a GEO subdomain: the score, the crawler access, the site files, the structured data or the
 * citations. A view of the report says to run the check while the page has no report of a published page.
 */
export function GeoSubdomainView({geo, id, path}) {
    const {t} = useGovernanceTranslation('geo');
    if (id === 'geo.citations') {
        return geo.citationsState === 'notConfigured' ?
            <ResultBanner>{t('citations.notConfiguredTab')}</ResultBanner> :
            <CitationsTab data={geo.citations} failed={geo.citationsState === 'failed'}/>;
    }
    const report = publishedReport(geo);
    if (!report) {
        return <ResultBanner>{t('drawer.runFirst')}</ResultBanner>;
    }
    switch (id) {
        case 'geo.score':
            return <ScoreTab report={report} path={path}/>;
        case 'geo.crawlers':
            return (
                <>
                    <GeoPageFindings report={report} id={id}/>
                    <CrawlerTab report={report}/>
                </>
            );
        case 'geo.files':
            return (
                <>
                    <GeoPageFindings report={report} id={id}/>
                    <SiteFilesTab report={report}/>
                </>
            );
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
 * In a subdomain with no GEO view, such as `geo.content`: the facts of the page placed there, and the checks
 * of the GEO score that failed on the rules that the site places there. Nothing while the page has no report,
 * or when there is nothing to show.
 */
export function GeoFindingsOf({geo, id, path, ruleIds}) {
    const report = publishedReport(geo);
    return report ? (
        <>
            <GeoPageFindings report={report} id={id}/>
            <FailedChecks report={report} path={path} ruleIds={ruleIds}/>
        </>
    ) : null;
}

GeoFindingsOf.propTypes = {
    geo: PropTypes.object.isRequired,
    // The subdomain
    id: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired,
    // The rules on in the subdomain
    ruleIds: PropTypes.instanceOf(Set).isRequired
};
