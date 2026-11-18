import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {auditFindingKey} from '../../drawer/findings';
import {FindingActions} from '../../drawer/FindingActions';
import styles from './Tabs.module.css';
import {impactClass} from './impactClass';

/**
 * Shared recommendations block: severity chip + what to fix + how, the id of the rule, and the
 * actions other modules add to the finding.
 * Items come from the analyzers as {key, rule, severity, params}; text lives in
 * the locale bundles under <ns>.recs.<key>.title / .detail, and the id of the rule is shown as is.
 * An audit stored before the catalog has no rule.
 */
export function Recommendations({items, ns, tab}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div className={styles.recList}>
            <h4 className={styles.sectionTitle}>{t('recs.title')}</h4>
            {items.length === 0 && (
                <div className={styles.allGood}>{t('recs.none')}</div>
            )}
            {items.map(rec => (
                <div key={rec.key} className={styles.rec}>
                    <span className={impactClass(rec.severity)}>
                        {t(`a11y.impacts.${rec.severity}`)}
                    </span>
                    <div className={styles.recBody}>
                        <div className={styles.recTitle}>{t(`${ns}.recs.${rec.key}.title`, rec.params)}</div>
                        <div className={styles.recDetail}>{t(`${ns}.recs.${rec.key}.detail`, rec.params)}</div>
                        {rec.rule && <code className={styles.recRule}>{rec.rule}</code>}
                        <FindingActions findingKey={auditFindingKey(tab, rec.key)}/>
                    </div>
                </div>
            ))}
        </div>
    );
}

Recommendations.propTypes = {
    items: PropTypes.array.isRequired,
    ns: PropTypes.string.isRequired,
    // The tab's key in the results (`ecodesign` for the namespace `eco`)
    tab: PropTypes.string.isRequired
};
