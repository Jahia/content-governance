import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {PassedFold} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {Recommendations} from './Recommendations';
import {ECO_CHECKLIST} from './ecoChecklist';
import styles from './Tabs.module.css';

/**
 * Eco-design: its score and the figures of the page, one result card per rule, the criteria that pass
 * folded below, and the checklist of what a page cannot show.
 */
export function EcodesignTab({result}) {
    const {t} = useGovernanceTranslation('audit');

    let band = 'poor';
    if (result.passed === result.total) {
        band = 'good';
    } else if (result.passed >= result.total * 0.6) {
        band = 'average';
    }
    const scores = [
        {key: 'score', label: t('eco.score'), value: `${result.passed}/${result.total}`, hint: t('eco.scoreHint'), band},
        // The figures of the measure of the web vitals: none when the site turned it off
        ...(result.stats ? [
            {key: 'weight', label: t('eco.weight'), value: (result.stats.totalBytes / 1024 / 1024).toFixed(2), hint: 'MB'},
            {key: 'requests', label: t('eco.requests'), value: result.stats.requests, hint: t('eco.thirdPartyHint', {count: result.stats.thirdParty})}
        ] : [])
    ];
    // The label of the rule; an audit stored before the checks of the browser has a key of eco.checks
    const passed = result.criteria.filter(c => c.ok).map(c => ({key: c.key, title: c.label || t(`eco.checks.${c.key}`), detail: c.value}));

    return (
        <div>
            <ScoreBlock items={scores}/>
            <ResultBanner variant="info">{t('eco.disclaimer')}</ResultBanner>
            <Recommendations items={result.recommendations} ns="eco" tab="ecodesign"/>
            <PassedFold items={passed}/>

            <h4 className={styles.sectionTitle}>{t('eco.checklist.title')}</h4>
            <p className={styles.note}>{t('eco.checklist.intro')}</p>
            {ECO_CHECKLIST.map(group => (
                <details key={group.family} className={styles.violation}>
                    <summary className={styles.violationSummary}>
                        <span className={styles.levelChip}>{t(`eco.families.${group.family}`)}</span>
                        <span className={styles.violationHelp}>{t(`eco.checklist.families.${group.family}`)}</span>
                        <span className={styles.nodeCount}>{group.items.length}</span>
                    </summary>
                    <div className={styles.violationBody}>
                        {group.items.map(key => (
                            <div key={key} className={styles.node}>
                                <div className={styles.recTitle}>{t(`eco.checklist.items.${key}.title`)}</div>
                                <div className={styles.recDetail}>{t(`eco.checklist.items.${key}.detail`)}</div>
                            </div>
                        ))}
                    </div>
                </details>
            ))}
        </div>
    );
}

EcodesignTab.propTypes = {
    result: PropTypes.object.isRequired
};
