import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {MANUAL_CHECKLIST} from './a11yChecklist';
import {Recommendations} from './Recommendations';
import {auditFindingKey} from '../../drawer/findings';
import {FindingMenu} from '../../drawer/FindingActions';
import {ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {levelOf} from '../../common/results/levels';
import results from '../../common/results/Results.module.css';
import styles from './Tabs.module.css';

const LEVELS = ['A', 'AA', 'AAA', 'BP'];

/**
 * Per-level scorecard: share of APPLICABLE automated rules that passed.
 * Rules axe could not decide ("to verify") are left out of the ratio - they are
 * neither passed nor failed. Floored so a single failing rule never displays
 * as 100%. Deliberately says "automated rules passed", never "compliant": axe
 * covers a subset of WCAG and the manual checklist covers the rest.
 */
function levelScore(level, summary, violations) {
    const s = summary[level];
    const applicable = s.passes + s.violations;
    const nodes = violations
        .filter(v => v.level === level)
        .reduce((sum, v) => sum + (v.totalNodes || 0), 0);
    return {
        ...s,
        applicable,
        nodes,
        percent: applicable > 0 ? Math.floor((s.passes / applicable) * 100) : null
    };
}

/** The occurrences of an axe-core rule: each element it names, its HTML, why it fails, and Highlight. */
const nodesOf = (rule, onHighlight, label) => rule.nodes.map((node, i) => ({
    key: `${i}`,
    content: (
        <>
            <code className={results.code}>{node.html}</code>
            {node.failureSummary && <p className={styles.failure}>{node.failureSummary}</p>}
        </>
    ),
    action: <Button size="small" variant="outlined" label={label} onClick={() => onHighlight(node.target)}/>
}));

/**
 * An axe-core rule as a result card: the severity that the site gives the rule (axe-core's impact for a
 * rule the catalog does not hold), its WCAG level, and one occurrence per element.
 */
function RuleCard({rule, level, onHighlight, findingKey}) {
    const {t} = useGovernanceTranslation('audit');
    const more = rule.totalNodes - rule.nodes.length;
    return (
        <ResultCard level={level}
                    chips={<span className={results.chip}>{rule.level}</span>}
                    title={rule.title || rule.help}
                    detail={<>{rule.description} (<a href={rule.helpUrl} target="_blank" rel="noreferrer">{rule.id}</a>)</>}
                    rule={rule.rule}
                    occurrences={nodesOf(rule, onHighlight, t('a11y.highlight'))}
                    occurrencesLabel={t('recs.items', {count: rule.totalNodes})}
                    menu={findingKey ? <FindingMenu findingKey={findingKey}/> : null}
        >
            {more > 0 && <p className={styles.moreNodes}>{t('a11y.moreElements', {count: more})}</p>}
        </ResultCard>
    );
}

RuleCard.propTypes = {
    rule: PropTypes.object.isRequired,
    level: PropTypes.string.isRequired,
    onHighlight: PropTypes.func.isRequired,
    // The key of the finding of a violation, for the actions other modules add to it
    findingKey: PropTypes.string
};

/** The tile of a WCAG level: the share of its automated rules that passed, never a fake 100%. */
function levelTile(level, result, t) {
    const s = levelScore(level, result.summary, result.violations);
    const toVerify = s.incomplete > 0 ? ` · ${t('a11y.incomplete', {count: s.incomplete})}` : '';
    if (s.percent === null) {
        return {key: level, label: t(`a11y.levels.${level}`), value: '-', hint: `${t('a11y.score.notApplicable')}${toVerify}`};
    }
    const ok = s.violations === 0;
    return {
        key: level,
        label: t(`a11y.levels.${level}`),
        value: `${s.percent}%`,
        caption: t('a11y.score.label'),
        hint: `${ok ? t('a11y.score.rules', {passed: s.passes, total: s.applicable}) : t('a11y.score.violations', {count: s.violations, nodes: s.nodes})}${toVerify}`,
        band: ok ? 'good' : 'poor'
    };
}

export function AccessibilityTab({result, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div>
            <ScoreBlock items={LEVELS.map(level => levelTile(level, result, t))}/>

            <ResultBanner variant="info" title={t('a11y.machineNote')}>
                {t('a11y.engineInfo', {engine: result.engine, rules: result.rulesRun, passes: result.passCount})}
            </ResultBanner>

            {result.violations.length === 0 && (
                <ResultBanner variant="success" title={t('a11y.noViolations')}/>
            )}

            {result.violations.map(v => (
                <RuleCard key={v.id}
                          rule={v}
                          level={levelOf(v.severity || v.impact)}
                          onHighlight={onHighlight}
                          findingKey={auditFindingKey('a11y', v.id)}/>
            ))}

            {result.recommendations?.length > 0 && (
                <Recommendations items={result.recommendations} ns="a11y" tab="a11y" title={t('a11y.otherRules')}/>
            )}

            {result.incomplete.length > 0 && (
                <>
                    <h4 className={styles.sectionTitle}>{t('a11y.incompleteTitle')}</h4>
                    <ResultBanner variant="info" title={t('a11y.incompleteNote')}/>
                    {result.incomplete.map(rule => (
                        <RuleCard key={rule.id} rule={rule} level="review" onHighlight={onHighlight}/>
                    ))}
                </>
            )}

            <h4 className={styles.sectionTitle}>{t('a11y.checklist.title')}</h4>
            <p className={styles.note}>{t('a11y.checklist.intro')}</p>
            {MANUAL_CHECKLIST.map(group => (
                <details key={group.level} className={styles.violation}>
                    <summary className={styles.violationSummary}>
                        <span className={styles.levelChip}>{group.level}</span>
                        <span className={styles.violationHelp}>{t(`a11y.levels.${group.level}`)}</span>
                        <span className={styles.nodeCount}>{group.items.length}</span>
                    </summary>
                    <div className={styles.violationBody}>
                        {group.items.map(key => (
                            <div key={key} className={styles.node}>
                                <div className={styles.recTitle}>{t(`a11y.checklist.items.${key}.title`)}</div>
                                <div className={styles.recDetail}>{t(`a11y.checklist.items.${key}.detail`)}</div>
                            </div>
                        ))}
                    </div>
                </details>
            ))}
        </div>
    );
}

AccessibilityTab.propTypes = {
    result: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired
};
