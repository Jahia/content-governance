import React, {useMemo} from 'react';
import PropTypes from 'prop-types';
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
import {useGovernanceTranslation} from '../common/i18n';
import {issuesIn} from './complianceState';
import styles from './ComplianceParts.module.css';
import {languageDisplayName} from '../common/locale';

/**
 * What the compliance scan shows in the drawer: its toolbar and what holds for the whole page, once per domain
 * that reads it, and the terms of each subdomain, grouped by content (compliance/complianceState.js). The state
 * comes from `useComplianceScan`.
 */

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
                        isDisabled={!globalThis.CE_API?.edit}
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
                            <dt>{t('issue.rule')}</dt>
                            <dd>{issue.ruleId}</dd>
                            {issue.folder && <><dt>{t('issue.glossary')}</dt><dd>{glossaryTitles.get(issue.folder) || issue.folder}</dd></>}
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

/** The uses of terms, grouped by the content that holds them. */
const IssueGroups = ({report, issues, onEdit}) => {
    const {t} = useGovernanceTranslation('compliance');
    const groups = useMemo(() => groupByContent(issues), [issues]);
    const glossaryTitles = useMemo(() => new Map(report.glossaries.map(g => [g.path, g.title])), [report.glossaries]);
    return groups.length > 0 ? (
        <ul className={styles.groups} aria-label={t('issue.listLabel')}>
            {groups.map(group => <IssueGroup key={group.nodeUuid} group={group} glossaryTitles={glossaryTitles} onEdit={onEdit}/>)}
        </ul>
    ) : null;
};

IssueGroups.propTypes = {
    report: PropTypes.object.isRequired,
    issues: PropTypes.array.isRequired,
    onEdit: PropTypes.func.isRequired
};

/** "Scan again". */
export function ComplianceToolbar({scan}) {
    const {t} = useGovernanceTranslation('compliance');
    return (
        <Button variant="outlined"
                icon={<Reload/>}
                label={t('dialog.rescan')}
                isDisabled={Boolean(scan.result.loading)}
                onClick={scan.run}/>
    );
}

ComplianceToolbar.propTypes = {
    scan: PropTypes.object.isRequired
};

/**
 * The scan under way, why it failed, and what holds for the whole page: terms to fix before publishing, no
 * glossary for the language, a page that the scan could not frame, a report cut short, and the glossaries that
 * apply.
 */
export function ComplianceFacts({scan, onEdit}) {
    const {t} = useGovernanceTranslation('compliance');
    const {result, uiLanguage} = scan;
    const report = result.report;
    const language = report ? languageDisplayName(report.language, uiLanguage) : '';
    return (
        <div aria-live="polite" aria-busy={Boolean(result.loading)}>
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
            {report?.status === 'ISSUES' && (
                <Banner variant="warning" title={t('banner.beforePublishing.title')}>
                    <Typography>{t('banner.beforePublishing.message')}</Typography>
                </Banner>
            )}
            {report?.status === 'NO_GLOSSARY' && (
                <Banner variant="info" title={t('banner.noGlossary.title', {language})}>
                    <Typography>{t('banner.noGlossary.message', {language})}</Typography>
                </Banner>
            )}
            {report && !result.framed && report.status !== 'NO_GLOSSARY' && (
                <Banner variant="info" title={t('banner.notFramed.title')}>
                    <Typography>{t('banner.notFramed.message')}</Typography>
                </Banner>
            )}
            {report?.truncated && (
                <Banner variant="warning" title={t('banner.truncated.title')}>
                    <Typography>{t('banner.truncated.message')}</Typography>
                </Banner>
            )}
            {report?.glossaries.length > 0 && (
                <div className={styles.glossaries}>
                    <Typography variant="caption" weight="bold">{t('glossaries.applied')}</Typography>
                    {report.glossaries.map(glossary => (
                        <Button key={glossary.path}
                                variant="ghost"
                                size="small"
                                label={t('glossaries.item', {title: glossary.title, count: glossary.termCount})}
                                isDisabled={!globalThis.CE_API?.edit || !glossary.uuid}
                                onClick={() => onEdit(glossary.uuid)}/>
                    ))}
                </div>
            )}
        </div>
    );
}

ComplianceFacts.propTypes = {
    scan: PropTypes.object.isRequired,
    // Opens a content in Content Editor, by its id
    onEdit: PropTypes.func.isRequired
};

/**
 * The terms that a subdomain shows: its status and a sentence for a subdomain of terms (`dedicated`), and the
 * uses of the terms of its rules, grouped by content. Another subdomain shows the uses only, when it has some.
 */
export function ComplianceSubdomainView({scan, id, subdomainOf, dedicated, onEdit}) {
    const {t} = useGovernanceTranslation('compliance');
    const {result, uiLanguage} = scan;
    const report = result.report;
    const issues = useMemo(() => issuesIn(report, id, subdomainOf), [report, id, subdomainOf]);
    if (!report || (!dedicated && issues.length === 0)) {
        return null;
    }
    const language = languageDisplayName(report.language, uiLanguage);
    let status = report.status;
    if (status !== 'NO_GLOSSARY') {
        status = issues.length > 0 ? 'ISSUES' : 'SAFE';
    }
    let summary;
    if (status === 'SAFE') {
        summary = t('summary.safe', {count: report.scannedContents, language});
    } else if (status === 'ISSUES') {
        summary = t('summary.issues', {count: issues.length, contents: groupByContent(issues).length, language});
    } else {
        summary = t('summary.noGlossary', {language});
    }
    return (
        <>
            {dedicated && (
                <div className={styles.summary}>
                    <StatusChip status={status}/>
                    <Typography>{summary}</Typography>
                </div>
            )}
            <IssueGroups report={report} issues={issues} onEdit={onEdit}/>
        </>
    );
}

ComplianceSubdomainView.propTypes = {
    scan: PropTypes.object.isRequired,
    // The subdomain
    id: PropTypes.string.isRequired,
    // The subdomain of a rule on the site
    subdomainOf: PropTypes.func.isRequired,
    // True for a subdomain of terms, whose view is the scan
    dedicated: PropTypes.bool.isRequired,
    // Opens a content in Content Editor, by its id
    onEdit: PropTypes.func.isRequired
};
