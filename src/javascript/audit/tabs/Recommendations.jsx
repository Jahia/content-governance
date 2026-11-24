import React, {useContext, useState} from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {HighlightContext} from '../HighlightContext';
import {auditFindingKey, NS_OF, recommendationId, recommendationText} from '../../drawer/findings';
import {FindingActions} from '../../drawer/FindingActions';
import styles from './Tabs.module.css';
import {impactClass} from './impactClass';

/**
 * "Highlight on the page" for a finding whose check gave a locator, and "Item not found" when the page
 * has no such element any more, such as after an edit or on a page that a script changes.
 */
function ItemHighlight({locator}) {
    const {t} = useGovernanceTranslation('audit');
    const highlight = useContext(HighlightContext);
    const [missing, setMissing] = useState(false);
    if (!highlight) {
        return null;
    }
    return (
        <span className={styles.itemAction}>
            <Button size="small"
                    variant="outlined"
                    label={t('a11y.highlight')}
                    onClick={() => setMissing(!highlight(locator))}/>
            {missing && <output className={styles.notFound}>{t('recs.notFound')}</output>}
        </span>
    );
}

ItemHighlight.propTypes = {
    locator: PropTypes.string.isRequired
};

/**
 * Shared recommendations block: severity chip + what to fix + how, the id of the rule, each finding of
 * the rule, and the actions other modules add to the finding.
 * Items come from the check of the server or the checks of the browser as {key, rule, severity, params,
 * items?, title, detail}: the texts come from the rule, in the editor's interface language, for a built-in
 * rule and for a rule of another module alike. An audit stored by an older version has no title, and shows
 * its old text. An item with `bucket`, the key of the results that hold it (audit/subdomainResults.js), takes
 * its finding key and the namespace of its old text from that key: the section of its subdomain may be the
 * view of other results.
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
                const text = recommendationText(rec, rec.bucket ? NS_OF[rec.bucket] : ns, t);
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
                                                {item.locator && <ItemHighlight locator={item.locator}/>}
                                            </li>
                                        ))}
                                    </ul>
                                </details>
                            )}
                            <FindingActions findingKey={auditFindingKey(rec.bucket || tab, recommendationId(rec))}/>
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
