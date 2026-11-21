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

/**
 * The score is a count of checks, not a rating out of 100. Every row names the
 * fact it read, so an editor can disagree with a specific line rather than with
 * a number they cannot see inside. Each row is the pass/fail result of a rule, worded by that rule, and
 * the counts come from the rows: a rule of the domain geo of another module counts the same way.
 */
export const ScoreTab = ({report, path}) => {
    const {t, i18n} = useGovernanceTranslation('geo');
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
                                <li key={c.id} className={styles.checkItem}>
                                    <span className={styles.checkMark}>
                                        {/* A bare glyph is not a word: a screen reader either skips it as
                                          * punctuation or reads "check mark", neither of which says
                                          * pass or fail. */}
                                        <Chip
                                            label={c.passed ? t('score.passed') : t('score.failed')}
                                            color={c.passed ? 'success' : 'danger'}
                                        />
                                    </span>
                                    <span className={styles.checkText}>
                                        <Typography variant="body" className={styles.checkLabel}>
                                            {c.label}
                                        </Typography>
                                        {c.fix && (
                                            <Typography variant="caption" className={styles.checkFix}>
                                                {c.fix}
                                            </Typography>
                                        )}
                                        {c.rule && (
                                            <code className={styles.checkRule}>{c.rule}</code>
                                        )}
                                        {!c.passed && shared.has(c.id) && (
                                            <Typography variant="caption" className={styles.fromTemplate}>
                                                {t('score.fromTemplate', {
                                                    template: rollup.template,
                                                    count: rollup.pages
                                                })}
                                            </Typography>
                                        )}
                                    </span>
                                    <span className={styles.checkMeta}>
                                        {c.value !== null && c.value !== undefined && c.value !== '' && (
                                            <Typography variant="caption" className={styles.checkValue}>
                                                {String(c.value)}
                                            </Typography>
                                        )}
                                        <Chip
                                            label={t(`score.severity.${c.severity}`)}
                                            color={c.severity === 'critical' ? 'warning' : 'default'}
                                        />
                                    </span>
                                </li>
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
