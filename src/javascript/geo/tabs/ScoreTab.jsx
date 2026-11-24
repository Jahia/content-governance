import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useGovernanceTranslation} from '../../common/i18n';
import {fetchRules} from '../../audit/rules';
import {passedOf} from '../../audit/ruleText';
import {FactBlock} from '../../common/results/FactBlock';
import {PassedFold, ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {LEVEL_RANK, levelOf} from '../../common/results/levels';
import results from '../../common/results/Results.module.css';
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

/**
 * A failed check of the GEO score as a result card: its level from its severity, its text, its rule, its
 * group and the value it read, and why the failure comes from the template of the page.
 */
function CheckCard({check, fromTemplate}) {
    const {t} = useGovernanceTranslation('geo');
    const hasValue = check.value !== null && check.value !== undefined && check.value !== '';
    return (
        <ResultCard level={levelOf(check.severity)}
                    chips={(
                        <>
                            {check.group && <span className={results.chip}>{t(`score.group.${check.group}`)}</span>}
                            {hasValue && <span className={results.chip}>{String(check.value)}</span>}
                        </>
                    )}
                    title={check.label}
                    detail={check.fix}
                    rule={check.rule}
        >
            {fromTemplate && <p className={styles.cardNote}>{fromTemplate}</p>}
        </ResultCard>
    );
}

CheckCard.propTypes = {
    check: PropTypes.shape({
        passed: PropTypes.bool,
        label: PropTypes.string,
        fix: PropTypes.string,
        rule: PropTypes.string,
        group: PropTypes.string,
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
    return checks.map(check => <CheckCard key={check.id} check={check}/>);
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
    let band = 'good';
    if (criticalFailed > 0) {
        band = 'poor';
    } else if (importantFailed > 0) {
        band = 'average';
    }
    const hint = [
        criticalFailed > 0 && t('score.criticalFailed', {count: criticalFailed}),
        importantFailed > 0 && t('score.importantFailed', {count: importantFailed})
    ].filter(Boolean).join(' · ') || t('score.allGood');
    const order = check => GROUPS.indexOf(check.group);
    const failed = checks.filter(check => !check.passed).sort((a, b) => (LEVEL_RANK[levelOf(a.severity)] - LEVEL_RANK[levelOf(b.severity)]) || (order(a) - order(b)));

    /*
     * A page score with no reference point is not information: nobody knows whether sixteen of eighteen is
     * good on this site. The site average and this page's own section say whether the page is the problem
     * or the site is. Facts, not findings.
     */
    const context = [
        report.context && report.context.sitePercent !== undefined && {
            key: 'site',
            label: t('context.site', {percent: report.context.sitePercent}),
            value: report.context.section ? t('context.section', {section: report.context.section, percent: report.context.sectionPercent}) : null
        },
        report.links && {key: 'links', label: t('context.links', {nav: report.links.nav, content: report.links.content})},
        report.context && report.context.llms && {key: 'llms', label: t(llmsKey(report.context.llms))}
    ].filter(Boolean);

    return (
        <div>
            <ScoreBlock items={[{key: 'score', label: t('score.tab'), value: `${passed}/${total}`, hint, band}]}/>
            {failed.map(c => (
                <CheckCard key={c.id}
                           check={c}
                           fromTemplate={shared.has(c.id) ? t('score.fromTemplate', {template: rollup.template, count: rollup.pages}) : undefined}/>
            ))}
            <PassedFold items={checks.filter(c => c.passed).map(c => ({key: c.id, title: c.label, rule: c.rule}))}/>
            <FactBlock rows={context}/>
            <ResultBanner variant="info">{t('score.rubric')}</ResultBanner>
        </div>
    );
};

ScoreTab.propTypes = {
    report: PropTypes.object.isRequired,
    // The page, whose site gives the rules
    path: PropTypes.string
};
