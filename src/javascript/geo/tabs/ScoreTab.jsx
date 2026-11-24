import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useGovernanceTranslation} from '../../common/i18n';
import {Chip, Typography} from '@jahia/moonstone';
import {fetchRules} from '../../audit/rules';
import {passedOf} from '../../audit/ruleText';
import styles from './Tabs.module.css';

const GROUPS = ['access', 'content', 'files'];

/** Whether the site's llms.txt lists this page. Not being listed is the author's choice. */
function llmsKey(llms) {
    return llms.listed ? 'context.llms.listed' : 'context.llms.notListed';
}

/**
 * The texts of a GEO check, from the rule it reports, in the editor's interface language: the neutral
 * label of the rule, and for a failed check the title and the advice of its finding. Without the rules
 * (they are loading, or could not be read), the texts of the interface stand in.
 */
function wordCheck(check, rules, t) {
    const rule = check.rule && rules?.find(check.rule);
    if (!rule) {
        return {label: t(`score.check.${check.id}.label`), fix: check.passed ? null : t(`score.check.${check.id}.fix`, {min: check.min, max: check.max})};
    }
    // A check that measured nothing, such as the length of a description the page lacks, measured 0
    const value = check.value === null || check.value === undefined || check.value === '' ? 0 : check.value;
    const worded = rules.word(check.rule, {count: Number(value) || 1, value, min: check.min, max: check.max});
    if (check.passed) {
        return {label: worded.label, fix: null};
    }
    const end = /[.!?]$/.test(worded.title) ? ' ' : '. ';
    return {label: worded.label, fix: worded.detail ? `${worded.title}${end}${worded.detail}` : worded.title};
}

/** The rules of the site of a page, with their texts in the editor's interface language, or null while they load. */
function useRuleBook(path) {
    const {i18n} = useGovernanceTranslation('geo');
    const client = useApolloClient();
    const [rules, setRules] = useState(null);
    const uiLanguage = i18n?.language || 'en';
    useEffect(() => {
        let live = true;
        if (path) {
            fetchRules(client, path, uiLanguage).then(book => live && setRules(book)).catch(() => live && setRules(null));
        }
        return () => {
            live = false;
        };
    }, [client, path, uiLanguage]);
    return rules;
}

/** One check of the GEO score: passed or failed, its text, its rule, its value and its severity. */
function CheckRow({check, fromTemplate}) {
    const {t} = useGovernanceTranslation('geo');
    return (
        <li className={styles.checkItem}>
            <span className={styles.checkMark}>
                {/* A bare glyph is not a word: a screen reader either skips it as
                  * punctuation or reads "check mark", neither of which says
                  * pass or fail. */}
                <Chip
                    label={check.passed ? t('score.passed') : t('score.failed')}
                    color={check.passed ? 'success' : 'danger'}
                />
            </span>
            <span className={styles.checkText}>
                <Typography variant="body" className={styles.checkLabel}>
                    {check.label}
                </Typography>
                {check.fix && (
                    <Typography variant="caption" className={styles.checkFix}>
                        {check.fix}
                    </Typography>
                )}
                {check.rule && (
                    <code className={styles.checkRule}>{check.rule}</code>
                )}
                {fromTemplate && (
                    <Typography variant="caption" className={styles.fromTemplate}>
                        {fromTemplate}
                    </Typography>
                )}
            </span>
            <span className={styles.checkMeta}>
                {check.value !== null && check.value !== undefined && check.value !== '' && (
                    <Typography variant="caption" className={styles.checkValue}>
                        {String(check.value)}
                    </Typography>
                )}
                <Chip
                    label={t(`score.severity.${check.severity}`)}
                    color={check.severity === 'critical' ? 'warning' : 'default'}
                />
            </span>
        </li>
    );
}

CheckRow.propTypes = {
    check: PropTypes.shape({
        passed: PropTypes.bool,
        label: PropTypes.string,
        fix: PropTypes.string,
        rule: PropTypes.string,
        value: PropTypes.any,
        severity: PropTypes.string
    }).isRequired,
    // Why the failure comes from the template of the page, or nothing
    fromTemplate: PropTypes.string
};

/**
 * The checks of the GEO score that failed on rules of a list, such as the rules that a site places in the
 * subdomain `geo.content`. Nothing when none failed.
 *
 * @param {{report: object, path: string, ruleIds: Set<string>}} props the report of the GEO check, the page,
 *     and the ids of the rules
 */
export const FailedChecks = ({report, path, ruleIds}) => {
    const {t} = useGovernanceTranslation('geo');
    const rules = useRuleBook(path);
    const checks = (report?.score?.checks || []).filter(check => !check.passed && ruleIds.has(check.rule))
        .map(check => ({...check, ...wordCheck(check, rules, t)}));
    if (checks.length === 0) {
        return null;
    }
    return (
        <ul className={styles.checkList}>
            {checks.map(check => <CheckRow key={check.id} check={check}/>)}
        </ul>
    );
};

FailedChecks.propTypes = {
    report: PropTypes.object,
    path: PropTypes.string.isRequired,
    ruleIds: PropTypes.instanceOf(Set).isRequired
};

/**
 * The score is a count of checks, not a rating out of 100. Every row names the
 * fact it read, so an editor can disagree with a specific line rather than with
 * a number they cannot see inside. Each row is the pass/fail result of a rule, worded by that rule, and
 * the counts come from the rows: a rule of the domain geo of another module counts the same way.
 */
export const ScoreTab = ({report, path}) => {
    const {t} = useGovernanceTranslation('geo');
    const rules = useRuleBook(path);
    const score = report.score;

    if (!score) {
        return null;
    }

    const checks = (score.checks || []).map(check => ({...check, ...wordCheck(check, rules, t)}));
    const {passed, total} = passedOf(checks);
    const criticalFailed = checks.filter(check => !check.passed && check.severity === 'critical').length;
    const importantFailed = checks.filter(check => !check.passed && check.severity === 'important').length;
    // GEO-18. Checks the last site scan blames on this page's template, so an
    // author is not sent to fix something that is not theirs to fix.
    const rollup = report.templateRollup || {};
    const shared = new Set(rollup.sharedChecks || []);
    let tone = 'good';
    if (criticalFailed > 0) {
        tone = 'bad';
    } else if (importantFailed > 0) {
        tone = 'warn';
    }

    return (
        <div>
            <div className={`${styles.verdict} ${styles[tone]}`}>
                <span className={styles.scoreHeadline}>{t('score.headline', {passed, total})}</span>
                {criticalFailed > 0 && (
                    <span className={styles.scoreCount}>{t('score.criticalFailed', {count: criticalFailed})}</span>
                )}
                {importantFailed > 0 && (
                    <span className={styles.scoreCount}>{t('score.importantFailed', {count: importantFailed})}</span>
                )}
                {criticalFailed === 0 && importantFailed === 0 && (
                    <span className={styles.scoreCount}>{t('score.allGood')}</span>
                )}
            </div>

            {/*
              * A page score with no reference point is not information: nobody
              * knows whether sixteen of eighteen is good on this site. The site
              * average and this page's own section say whether the page is the
              * problem or the site is. Facts, not findings, so lines rather than
              * banners.
              */}
            {(report.context || report.links) && (
                <ul className={styles.contextList}>
                    {report.context && report.context.sitePercent !== undefined && (
                        <li>
                            {t('context.site', {percent: report.context.sitePercent})}
                            {report.context.section ? ` · ${t('context.section', {
                                section: report.context.section,
                                percent: report.context.sectionPercent
                            })}` : ''}
                        </li>
                    )}
                    {report.links && (
                        <li>
                            {t('context.links', {
                                nav: report.links.nav,
                                content: report.links.content
                            })}
                        </li>
                    )}
                    {report.context && report.context.llms && (
                        <li>
                            {t(llmsKey(report.context.llms))}
                        </li>
                    )}
                </ul>
            )}

            {GROUPS.map(group => {
                const rows = checks.filter(c => c.group === group);
                if (rows.length === 0) {
                    return null;
                }

                return (
                    <div key={group}>
                        <Typography variant="subheading" component="h3" className={styles.sectionTitle}>
                            {t(`score.group.${group}`)}
                        </Typography>
                        <ul className={styles.checkList}>
                            {rows.map(c => (
                                <CheckRow key={c.id}
                                          check={c}
                                          fromTemplate={!c.passed && shared.has(c.id) ?
                                              t('score.fromTemplate', {template: rollup.template, count: rollup.pages}) :
                                              undefined}/>
                            ))}
                        </ul>
                    </div>
                );
            })}

            <p className={styles.explain}>{t('score.rubric')}</p>
        </div>
    );
};

ScoreTab.propTypes = {
    report: PropTypes.object.isRequired,
    // The page, whose site gives the rules
    path: PropTypes.string
};
