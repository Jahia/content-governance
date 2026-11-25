import React, {useEffect, useRef} from 'react';
import PropTypes from 'prop-types';
import {Banner, Button, Download, Loader, Reload, Typography, Visibility} from '@jahia/moonstone';
import {useDateFormat, useGovernanceTranslation} from '../common/i18n';
import {useDrawerDisplay} from '../common/useDrawerDisplay';
import {AccessibilityTab} from './tabs/AccessibilityTab';
import {VitalsTab} from './tabs/VitalsTab';
import {ReadabilityTab} from './tabs/ReadabilityTab';
import {EcodesignTab} from './tabs/EcodesignTab';
import {SeoTab} from './tabs/SeoTab';
import {LinksTab} from './tabs/LinksTab';
import {JahiaTab} from './tabs/JahiaTab';
import {IncompleteStep} from './tabs/IncompleteStep';
import {NotChecked} from './tabs/NotChecked';
import {Recommendations} from './tabs/Recommendations';
import {incompleteIn, notCheckedIn, recommendationsIn, RESULT_OF, viewsIn} from './subdomainResults';
import {RuleViews} from '../common/results/RuleViews';
import styles from './PageAudit.module.css';

/**
 * What the page check shows in the drawer: its toolbar and its state, once per domain that reads it, the
 * preview of the page, once for the drawer, and the view of each subdomain. The state comes from
 * `usePageAudit`.
 */

/**
 * The issues of the stored audits of the page, oldest to newest ("12 → 9 → 7"), each with its date and
 * who ran it on hover. Nothing while the page has fewer than two.
 */
function AuditTrend({runs}) {
    const {t} = useGovernanceTranslation('audit');
    const {formatDateTime} = useDateFormat();
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
                    <span title={`${formatDateTime(run.auditedAt)} · ${run.auditedBy || ''}`}>{run.summary.total}</span>
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
    })).isRequired
};

/** The state of `usePageAudit`, as its parts read it. */
const auditShape = PropTypes.shape({
    status: PropTypes.string.isRequired,
    busy: PropTypes.bool.isRequired,
    ready: PropTypes.bool.isRequired,
    results: PropTypes.object,
    error: PropTypes.string,
    auditedAt: PropTypes.number,
    auditedBy: PropTypes.string,
    stale: PropTypes.bool.isRequired,
    history: PropTypes.array.isRequired,
    previewCollapsed: PropTypes.bool.isRequired,
    setPreviewCollapsed: PropTypes.func.isRequired,
    rerun: PropTypes.func.isRequired,
    exportJson: PropTypes.func.isRequired
});

/** "Re-run", "Export JSON", "Show preview" where the server shows it, and the date of the audit in view. */
export function PageAuditToolbar({audit}) {
    const {t} = useGovernanceTranslation('audit');
    const {formatDateTime} = useDateFormat();
    // The "Show preview" button shows where the cfg of the server shows it
    const {previewButton} = useDrawerDisplay();
    return (
        <>
            <Button variant="outlined"
                    icon={<Reload/>}
                    label={t('drawer.rerun')}
                    isDisabled={audit.busy}
                    onClick={audit.rerun}/>
            <Button variant="outlined"
                    icon={<Download/>}
                    label={t('drawer.export')}
                    isDisabled={!audit.results}
                    onClick={audit.exportJson}/>
            {previewButton && (
                <Button variant="ghost"
                        icon={<Visibility/>}
                        label={audit.previewCollapsed ? t('drawer.showPreview') : t('drawer.hidePreview')}
                        aria-expanded={!audit.previewCollapsed}
                        onClick={() => audit.setPreviewCollapsed(c => !c)}/>
            )}
            {audit.auditedAt && (
                <Typography variant="caption" className={styles.lastAudit}>
                    {audit.auditedBy ?
                        t('drawer.lastAuditBy', {date: formatDateTime(audit.auditedAt), user: audit.auditedBy}) :
                        t('drawer.lastAudit', {date: formatDateTime(audit.auditedAt)})}
                </Typography>
            )}
            <AuditTrend runs={audit.history}/>
        </>
    );
}

PageAuditToolbar.propTypes = {
    audit: auditShape.isRequired
};

/** An audit stored before #140, an audit older than the page, the audit under way, or why it failed. */
export function PageAuditStatus({audit}) {
    const {t} = useGovernanceTranslation('audit');
    return (
        <>
            {audit.ready && audit.results.legacy && (
                <Banner variant="info" title={t('drawer.legacyTitle')}>
                    <div className={styles.staleRow}>
                        <Typography>{t('drawer.legacy')}</Typography>
                        <Button size="small" variant="outlined" label={t('drawer.runAgain')} onClick={audit.rerun}/>
                    </div>
                </Banner>
            )}
            {audit.stale && (
                <Banner variant="warning" title={t('drawer.staleTitle')}>
                    <div className={styles.staleRow}>
                        <Typography>{t('drawer.stale')}</Typography>
                        <Button size="small" variant="outlined" label={t('drawer.rerun')} onClick={audit.rerun}/>
                    </div>
                </Banner>
            )}
            {audit.busy && (
                <div className={styles.status}>
                    <Loader size="small"/>
                    <Typography>{t(audit.status === 'loading' ? 'drawer.loading' : 'drawer.analyzing')}</Typography>
                </div>
            )}
            {audit.status === 'error' && (
                <Banner variant="danger" title={t('drawer.error')}>
                    <Typography>{audit.error}</Typography>
                </Banner>
            )}
        </>
    );
}

PageAuditStatus.propTypes = {
    audit: auditShape.isRequired
};

/**
 * The preview of the page, which the checks of the browser measure and where a finding is highlighted. It
 * stays mounted at full size: hidden, it sits behind the content of the drawer, where it keeps its size and
 * paints, and nothing of it takes focus or the pointer.
 */
export function PagePreview({audit, collapsed}) {
    const {t} = useGovernanceTranslation('audit');
    const previewRef = useRef(null);
    useEffect(() => {
        previewRef.current?.toggleAttribute('inert', collapsed);
    }, [collapsed]);
    return (
        <div ref={previewRef}
             className={`${styles.preview} ${collapsed ? styles.previewCollapsed : ''}`}
             aria-hidden={collapsed || undefined}
        >
            {audit.frameVisible && (
                <iframe
                    key={audit.runId}
                    ref={audit.frameRef}
                    title={t('drawer.preview')}
                    src={audit.previewUrl}
                    className={styles.frame}
                    tabIndex={collapsed ? -1 : undefined}
                    onLoad={audit.handleFrameLoad}
                />
            )}
        </div>
    );
}

PagePreview.propTypes = {
    audit: PropTypes.shape({
        frameVisible: PropTypes.bool.isRequired,
        frameRef: PropTypes.object.isRequired,
        runId: PropTypes.number.isRequired,
        previewUrl: PropTypes.string.isRequired,
        handleFrameLoad: PropTypes.func.isRequired
    }).isRequired,
    // Behind the content, rather than above it
    collapsed: PropTypes.bool.isRequired
};

/** The dedicated view of a subdomain of the page check, with the recommendations that show in the subdomain. */
function DedicatedView({id, results, recommendations, onHighlight, onHighlightText}) {
    const result = results[RESULT_OF[id]];
    if (!result) {
        return null;
    }
    const placed = {...result, recommendations};
    switch (id) {
        case 'accessibility':
            return <AccessibilityTab result={placed} onHighlight={onHighlight}/>;
        case 'seo':
            return <SeoTab result={placed}/>;
        case 'links':
            return <LinksTab result={placed} onHighlight={onHighlight}/>;
        case 'health':
            return <JahiaTab result={placed} onHighlightText={onHighlightText}/>;
        case 'vitals':
            return result.metrics ? <VitalsTab result={placed}/> : null;
        case 'readability':
            return <ReadabilityTab result={placed} onHighlight={onHighlight}/>;
        case 'ecodesign':
            return <EcodesignTab result={placed}/>;
        default:
            return null;
    }
}

DedicatedView.propTypes = {
    id: PropTypes.string.isRequired,
    results: PropTypes.object.isRequired,
    recommendations: PropTypes.array.isRequired,
    onHighlight: PropTypes.func.isRequired,
    onHighlightText: PropTypes.func.isRequired
};

/**
 * What the page check found in a subdomain: why its step did not finish, the facts and scores of the rules that
 * show their values, its dedicated view, or else the list of the recommendations of the rules that the site places in it, and the rules of the browser that no check
 * gave a result for. Nothing while the audit has no results.
 */
export function PageSubdomainView({audit, id, subdomainOf}) {
    if (!audit.ready) {
        return null;
    }
    const {results} = audit;
    const incomplete = incompleteIn(results, id);
    const recommendations = recommendationsIn(results, id, subdomainOf);
    const notChecked = notCheckedIn(results, id, subdomainOf);
    return (
        <>
            {incomplete && <IncompleteStep outcome={incomplete} onRerun={audit.rerun}/>}
            <RuleViews views={viewsIn(results, id, subdomainOf)}/>
            {RESULT_OF[id] ?
                <DedicatedView id={id}
                               results={results}
                               recommendations={recommendations}
                               onHighlight={audit.highlight}
                               onHighlightText={audit.highlightText}/> :
                <Recommendations items={recommendations} ns="recs" tab="other"/>}
            {notChecked.length > 0 && <NotChecked rules={notChecked}/>}
        </>
    );
}

PageSubdomainView.propTypes = {
    audit: auditShape.isRequired,
    // The subdomain
    id: PropTypes.string.isRequired,
    // The subdomain of a rule on the site
    subdomainOf: PropTypes.func.isRequired
};

/**
 * The recommendations of the page check that a section with a view of another check lists: the rules that
 * the site moved there, such as a rule of SEO placed in the subdomain of the GEO score. Nothing when it has none.
 */
export function PageFindingsOf({audit, id, subdomainOf}) {
    if (!audit.ready) {
        return null;
    }
    const recommendations = recommendationsIn(audit.results, id, subdomainOf);
    return (
        <>
            <RuleViews views={viewsIn(audit.results, id, subdomainOf)}/>
            {recommendations.length > 0 && <Recommendations items={recommendations} ns="recs" tab="other"/>}
        </>
    );
}

PageFindingsOf.propTypes = PageSubdomainView.propTypes;
