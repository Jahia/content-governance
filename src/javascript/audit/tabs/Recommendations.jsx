import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {auditFindingKey, recText} from '../../drawer/findings';
import {FindingActions} from '../../drawer/FindingActions';
import styles from './Tabs.module.css';
import {impactClass} from './impactClass';

/**
 * Shared recommendations block: severity chip + what to fix + how, the id of the rule, and the
 * actions other modules add to the finding.
 * Items are {key, rule, severity, params}; text lives in the locale bundles under
 * <ns>.recs.<key>.title / .detail, and the id of the rule is shown as is. An item of a rule that the
 * tab does not word itself has the advice of the rule (`recText`).
 */
export function Recommendations({items, ns, tab}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div className={styles.recList}>
            <h4 className={styles.sectionTitle}>{t('recs.title')}</h4>
            {items.length === 0 && (
                <div className={styles.allGood}>{t('recs.none')}</div>
            )}
            {items.map(rec => {
                const {title, detail} = recText(rec, ns, t);
                return (
                    <div key={rec.key} className={styles.rec}>
                        <span className={impactClass(rec.severity)}>
                            {t(`a11y.impacts.${rec.severity}`)}
                        </span>
                        <div className={styles.recBody}>
                            <div className={styles.recTitle}>{title}</div>
                            <div className={styles.recDetail}>{detail}</div>
                            {rec.rule && <code className={styles.recRule}>{rec.rule}</code>}
                            <FindingActions findingKey={auditFindingKey(tab, rec.key)}/>
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
    tab: PropTypes.string.isRequired
};
