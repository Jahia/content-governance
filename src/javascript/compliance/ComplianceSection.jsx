import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useApolloClient} from '@apollo/client';
import {useGovernanceTranslation} from '../common/i18n';
import {
    Banner,
    Button,
    Check,
    Chip,
    Edit,
    Information,
    Loader,
    Reload,
    Typography,
    Warning
} from '@jahia/moonstone';
import {SCAN_PAGE} from './scan.gql';
import {collectRenderedPaths} from './renderedPaths';
import styles from './ComplianceSection.module.css';

const languageName = (code, uiLanguage) => {
    try {
        return new Intl.DisplayNames([uiLanguage || 'en'], {type: 'language'}).of(code.replace('_', '-')) || code;
    } catch {
        return code;
    }
};

/** Issues grouped by content, in the order the scan found them. */
const groupByContent = issues => {
    const groups = new Map();
    issues.forEach(issue => {
        if (!groups.has(issue.nodeUuid)) {
            groups.set(issue.nodeUuid, {...issue, items: []});
        }
        groups.get(issue.nodeUuid).items.push(issue);
    });
    return [...groups.values()];
};

const StatusChip = ({status}) => {
    const {t} = useGovernanceTranslation('compliance');
    if (status === 'SAFE') {
        return <Chip color="success" icon={<Check/>} label={t('status.safe')}/>;
    }
    if (status === 'ISSUES') {
        return <Chip color="danger" icon={<Warning/>} label={t('status.issues')}/>;
    }
    return <Chip color="warning" icon={<Information/>} label={t('status.noGlossary')}/>;
};

StatusChip.propTypes = {status: PropTypes.string.isRequired};

const IssueGroup = ({group, glossaryTitles, onEdit}) => {
    const {t} = useGovernanceTranslation('compliance');
    return (
        <li className={styles.group}>
            <div className={styles.groupHeader}>
                <div className={styles.groupTitle}>
                    <Typography component="h3" variant="subheading" weight="bold">{group.nodeName || group.nodePath}</Typography>
                    <Typography variant="caption" className={styles.muted}>{group.nodeType} · {group.nodePath}</Typography>
                </div>
                {group.origin === 'REFERENCE' && <Chip color="accent" label={t('origin.reference')}/>}
                {group.origin === 'SHARED' && <Chip color="accent" label={t('origin.shared')}/>}
                <Button variant="outlined"
                        size="small"
                        icon={<Edit/>}
                        label={t('issue.edit')}
                        aria-label={t('issue.editLabel', {name: group.nodeName || group.nodePath})}
                        isDisabled={!window.CE_API?.edit}
                        onClick={() => onEdit(group.nodeUuid)}/>
            </div>
            {group.origin !== 'PAGE' && (
                <Typography variant="caption" className={styles.muted}>
                    {group.origin === 'REFERENCE' && t('origin.referenceHint', {path: group.originPath})}
                    {group.origin === 'SHARED' && (group.originPath ? t('origin.sharedAreaHint', {path: group.originPath}) : t('origin.sharedHint'))}
                </Typography>
            )}
            <ul className={styles.issues}>
                {group.items.map((issue, index) => (
                    // eslint-disable-next-line react/no-array-index-key
                    <li key={`${issue.propertyName}-${index}`} className={styles.issue}>
                        <Typography variant="caption" weight="bold">{issue.propertyLabel || issue.propertyName}</Typography>
                        <Typography className={styles.excerpt}>
                            {issue.before}<mark className={styles.mark}>{issue.matchedText}</mark>{issue.after}
                        </Typography>
                        <dl className={styles.details}>
                            <dt>{t('issue.term')}</dt>
                            <dd>{issue.term}</dd>
                            {issue.reason && <><dt>{t('issue.reason')}</dt><dd>{issue.reason}</dd></>}
                            {issue.alternative && <><dt>{t('issue.alternative')}</dt><dd>{issue.alternative}</dd></>}
                            {glossaryTitles.size > 1 && <><dt>{t('issue.glossary')}</dt><dd>{glossaryTitles.get(issue.glossaryPath) || issue.glossaryPath}</dd></>}
                        </dl>
                    </li>
                ))}
            </ul>
        </li>
    );
};

IssueGroup.propTypes = {
    group: PropTypes.object.isRequired,
    glossaryTitles: PropTypes.instanceOf(Map).isRequired,
    onEdit: PropTypes.func.isRequired
};

const Report = ({report, framed, uiLanguage, onEdit}) => {
    const {t} = useGovernanceTranslation('compliance');
    const language = languageName(report.language, uiLanguage);
    const groups = useMemo(() => groupByContent(report.issues), [report.issues]);
    const glossaryTitles = useMemo(() => new Map(report.glossaries.map(g => [g.path, g.title])), [report.glossaries]);

    let summary;
    if (report.status === 'SAFE') {
        summary = t('summary.safe', {count: report.scannedContents, language});
    } else if (report.status === 'ISSUES') {
        summary = t('summary.issues', {count: report.issues.length, contents: groups.length, language});
    } else {
        summary = t('summary.noGlossary', {language});
    }

    return (
        <>
            <div className={styles.summary}>
                <StatusChip status={report.status}/>
                <Typography>{summary}</Typography>
            </div>
            {report.status === 'ISSUES' && (
                <Banner variant="warning" title={t('banner.beforePublishing.title')}>
                    <Typography>{t('banner.beforePublishing.message')}</Typography>
                </Banner>
            )}
            {report.status === 'NO_GLOSSARY' && (
                <Banner variant="info" title={t('banner.noGlossary.title', {language})}>
                    <Typography>{t('banner.noGlossary.message', {language})}</Typography>
                </Banner>
            )}
            {!framed && report.status !== 'NO_GLOSSARY' && (
                <Banner variant="info" title={t('banner.notFramed.title')}>
                    <Typography>{t('banner.notFramed.message')}</Typography>
                </Banner>
            )}
            {report.truncated && (
                <Banner variant="warning" title={t('banner.truncated.title')}>
                    <Typography>{t('banner.truncated.message')}</Typography>
                </Banner>
            )}
            {report.glossaries.length > 0 && (
                <div className={styles.glossaries}>
                    <Typography variant="caption" weight="bold">{t('glossaries.applied')}</Typography>
                    {report.glossaries.map(glossary => (
                        <Button key={glossary.uuid}
                                variant="ghost"
                                size="small"
                                label={t('glossaries.item', {title: glossary.title, count: glossary.termCount})}
                                isDisabled={!window.CE_API?.edit}
                                onClick={() => onEdit(glossary.uuid)}/>
                    ))}
                </div>
            )}
            {groups.length > 0 && (
                <ul className={styles.groups} aria-label={t('issue.listLabel')}>
                    {groups.map(group => <IssueGroup key={group.nodeUuid} group={group} glossaryTitles={glossaryTitles} onEdit={onEdit}/>)}
                </ul>
            )}
        </>
    );
};

Report.propTypes = {
    report: PropTypes.object.isRequired,
    framed: PropTypes.bool.isRequired,
    uiLanguage: PropTypes.string,
    onEdit: PropTypes.func.isRequired
};

/**
 * The Compliance section of the drawer: the page and everything it shows, checked against the
 * site's glossaries of non-authorised terms for the language being edited. Runs when the section
 * first shows; editing a content closes the drawer, and it reopens on this section, scanned again,
 * when Content Editor closes.
 */
export const ComplianceSection = ({path, language, onClose, onReopen, onCount}) => {
    const {t} = useGovernanceTranslation('compliance');
    const client = useApolloClient();
    const uiLanguage = useSelector(state => state.uilang);
    const site = useSelector(state => state.site);
    const [result, setResult] = useState({loading: true});
    const runId = useRef(0);

    const run = useCallback(() => {
        const id = ++runId.current;
        const renderedPaths = collectRenderedPaths(path);
        setResult({loading: true});
        client.query({
            query: SCAN_PAGE,
            variables: {path, language, uiLanguage, renderedPaths: renderedPaths || []},
            fetchPolicy: 'network-only'
        }).then(({data}) => {
            if (id === runId.current) {
                setResult({report: data.complianceScan.scanPage, framed: renderedPaths !== null});
            }
        }).catch(error => {
            if (id === runId.current) {
                console.error('[content-governance] compliance scan failed', error);
                setResult({error});
            }
        });
    }, [client, path, language, uiLanguage]);

    useEffect(() => {
        run();
        return () => {
            runId.current++;
        };
    }, [run]);

    useEffect(() => {
        onCount(result.report ? result.report.issues.length : 0);
    }, [onCount, result.report]);

    // Content Editor opens over jContent: close the drawer, and scan again once the editor closes.
    const edit = useCallback(uuid => {
        if (!window.CE_API?.edit) {
            return;
        }
        onClose();
        window.CE_API.edit({uuid, site, lang: language, isFullscreen: false, onClosedCallback: onReopen});
    }, [onClose, onReopen, site, language]);

    return (
        <>
            <div className={styles.toolbar}>
                <Button size="big"
                        color="accent"
                        icon={<Reload/>}
                        label={t('dialog.rescan')}
                        isDisabled={Boolean(result.loading)}
                        onClick={run}/>
            </div>
            <div className={styles.body} aria-live="polite" aria-busy={Boolean(result.loading)}>
                {result.loading && (
                    <div className={styles.loading}>
                        <Loader size="big"/>
                        <Typography>{t('dialog.running')}</Typography>
                    </div>
                )}
                {result.error && (
                    <Banner variant="danger" title={t('dialog.error.title')}>
                        <Typography>{t('dialog.error.message')}</Typography>
                    </Banner>
                )}
                {result.report && <Report report={result.report} framed={result.framed} uiLanguage={uiLanguage} onEdit={edit}/>}
            </div>
        </>
    );
};

ComplianceSection.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    onClose: PropTypes.func.isRequired,
    onReopen: PropTypes.func.isRequired,
    onCount: PropTypes.func.isRequired
};
