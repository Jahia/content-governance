import React, {useMemo} from 'react';
import PropTypes from 'prop-types';
import {Button, Edit, Loader, Reload, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {FindingMenu} from '../drawer/FindingActions';
import {ResultCard} from '../common/results/ResultCard';
import {ResultBanner} from '../common/results/ResultBanner';
import {levelOf} from '../common/results/levels';
import results from '../common/results/Results.module.css';
import {issuesIn} from './complianceState';
import styles from './ComplianceParts.module.css';
import {languageDisplayName} from '../common/locale';

/**
 * What the compliance scan shows in the drawer: its toolbar and what holds for the whole page, once per domain
 * that reads it, and the terms of each subdomain, one result card per rule (compliance/complianceState.js). The state
 * comes from `useComplianceScan`.
 */

/** Issues grouped by rule, in the order the scan found them: one result card each. */
const groupByRule = issues => {
    const groups = new Map();
    issues.forEach(issue => {
        if (!groups.has(issue.ruleId)) {
            groups.set(issue.ruleId, {...issue, items: []});
        }
        groups.get(issue.ruleId).items.push(issue);
    });
    return [...groups.values()];
};

/** The number of contents that hold the issues. */
const contentCount = issues => new Set(issues.map(issue => issue.nodeUuid)).size;

/** Where a use of a term sits: its content, its property and its words, with where a shared content comes from. */
const IssueOccurrence = ({issue}) => {
    const {t} = useGovernanceTranslation('compliance');
    return (
        <>
            <strong>{issue.nodeName || issue.nodePath}</strong>
            <span className={styles.muted}> · {issue.propertyLabel || issue.propertyName}</span>
            {issue.origin === 'REFERENCE' && <span className={styles.muted}> · {t('origin.referenceHint', {path: issue.originPath})}</span>}
            {issue.origin === 'SHARED' && (
                <span className={styles.muted}> · {issue.originPath ? t('origin.sharedAreaHint', {path: issue.originPath}) : t('origin.sharedHint')}</span>
            )}
            <span className={styles.excerpt}>
                {issue.before}<mark className={styles.mark}>{issue.matchedText}</mark>{issue.after}
            </span>
        </>
    );
};

IssueOccurrence.propTypes = {issue: PropTypes.object.isRequired};

/** The uses of a term rule as a result card: the term, why and what to use instead, each use with its Edit. */
const RuleGroup = ({group, glossaryTitles, onEdit}) => {
    const {t} = useGovernanceTranslation('compliance');
    const detail = [group.reason, group.alternative && `${t('issue.alternative')}: ${group.alternative}`].filter(Boolean).join(' · ');
    return (
        <ResultCard level={levelOf(group.severity || 'serious')}
                    chips={group.folder ? <span className={results.chip}>{glossaryTitles.get(group.folder) || group.folder}</span> : null}
                    title={group.term}
                    detail={detail}
                    rule={group.ruleId}
                    occurrences={group.items.map((issue, index) => ({
                        key: `${issue.nodeUuid}-${issue.propertyName}-${index}`,
                        content: <IssueOccurrence issue={issue}/>,
                        action: (
                            <Button variant="outlined"
                                    size="small"
                                    icon={<Edit/>}
                                    label={t('issue.edit')}
                                    aria-label={t('issue.editLabel', {name: issue.nodeName || issue.nodePath})}
                                    isDisabled={!globalThis.CE_API?.edit}
                                    onClick={() => onEdit(issue.nodeUuid)}/>
                        )
                    }))}
                    menu={<FindingMenu findingKey={group.ruleId}/>}/>
    );
};

RuleGroup.propTypes = {
    group: PropTypes.object.isRequired,
    glossaryTitles: PropTypes.instanceOf(Map).isRequired,
    onEdit: PropTypes.func.isRequired
};

/** The uses of terms, one result card per rule. */
const RuleGroups = ({report, issues, onEdit}) => {
    const groups = useMemo(() => groupByRule(issues), [issues]);
    const glossaryTitles = useMemo(() => new Map(report.glossaries.map(g => [g.path, g.title])), [report.glossaries]);
    return groups.map(group => <RuleGroup key={group.ruleId} group={group} glossaryTitles={glossaryTitles} onEdit={onEdit}/>);
};

RuleGroups.propTypes = {
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
 * The scan under way, why it failed, and what holds for the whole page: no glossary for the language, a page
 * that the scan could not frame, a report cut short, and the glossaries that apply. The terms to fix are the
 * result cards of their subdomains.
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
                <ResultBanner variant="danger" title={t('dialog.error.title')}>{t('dialog.error.message')}</ResultBanner>
            )}
            {report?.status === 'NO_GLOSSARY' && (
                <ResultBanner variant="info" title={t('banner.noGlossary.title', {language})}>{t('banner.noGlossary.message', {language})}</ResultBanner>
            )}
            {report && !result.framed && report.status !== 'NO_GLOSSARY' && (
                <ResultBanner variant="info" title={t('banner.notFramed.title')}>{t('banner.notFramed.message')}</ResultBanner>
            )}
            {report?.truncated && (
                <ResultBanner variant="warning" title={t('banner.truncated.title')}>{t('banner.truncated.message')}</ResultBanner>
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
 * The terms that a subdomain shows: a sentence for a subdomain of terms (`dedicated`), and one result card per
 * rule with each use of its term. Another subdomain shows the cards only, when it has some.
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
    return (
        <>
            {dedicated && report.status === 'NO_GLOSSARY' && <ResultBanner variant="info" title={t('summary.noGlossary', {language})}/>}
            {dedicated && report.status !== 'NO_GLOSSARY' && issues.length === 0 && (
                <ResultBanner variant="success" title={t('summary.safe', {count: report.scannedContents, language})}/>
            )}
            {dedicated && issues.length > 0 && (
                <p className={styles.summaryLine}>{t('summary.issues', {count: issues.length, contents: contentCount(issues), language})}</p>
            )}
            <RuleGroups report={report} issues={issues} onEdit={onEdit}/>
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
