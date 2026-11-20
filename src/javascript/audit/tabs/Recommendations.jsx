import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {auditFindingKey, recommendationId, recommendationText} from '../../drawer/findings';
import {FindingActions} from '../../drawer/FindingActions';
import styles from './Tabs.module.css';
import {impactClass} from './impactClass';

/**
 * Shared recommendations block: severity chip + what to fix + how, the id of the rule, each finding of
 * the rule, and the actions other modules add to the finding.
 * Items come from the check of the server or the analyzers as {key, rule, severity, params, items?,
 * advice?}; text lives in the locale bundles under <ns>.recs.<key>.title / .detail, and a rule no tab
 * words itself shows its id and its advice. An audit stored before the catalog has no rule.
 */
export function Recommendations({items, ns, tab, title}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div className={styles.recList}>
            <h4 className={styles.sectionTitle}>{title || t('recs.title')}</h4>
            {items.length === 0 && (
                <div className={styles.allGood}>{t('recs.none')}</div>
            )}
            {items.map(rec => {
                const text = recommendationText(rec, ns, t);
                return (
                    <div key={recommendationId(rec)} className={styles.rec}>
                        <span className={impactClass(rec.severity)}>
                            {t(`a11y.impacts.${rec.severity}`)}
                        </span>
                        <div className={styles.recBody}>
                            <div className={styles.recTitle}>{text.title}</div>
                            <div className={styles.recDetail}>{text.detail}</div>
                            {rec.rule && <code className={styles.recRule}>{rec.rule}</code>}
                            {rec.items?.length > 0 && (
                                <details className={styles.recItems}>
                                    <summary>{t('recs.items', {count: rec.items.length})}</summary>
                                    <ul>
                                        {rec.items.map((item, index) => (
                                            /* eslint-disable-next-line react/no-array-index-key */
                                            <li key={index}>
                                                <code>{item.text}</code>
                                                {item.context && <span className={styles.recDetail}> {item.context}</span>}
                                            </li>
                                        ))}
                                    </ul>
                                </details>
                            )}
                            <FindingActions findingKey={auditFindingKey(tab, recommendationId(rec))}/>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

Recommendations.propTypes = {
    items: PropTypes.array.isRequired,
    ns: PropTypes.string.isRequired,
    // The tab's key in the results (`ecodesign` for the namespace `eco`)
    tab: PropTypes.string.isRequired,
    // The heading of the block, "Recommendations" when omitted
    title: PropTypes.string
};
