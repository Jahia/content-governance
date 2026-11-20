import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {Recommendations} from './Recommendations';
import styles from './Tabs.module.css';

// A link holds the path of its page in its address, as /cms/render/default/en/sites/digitall/home.html
const selectorFor = path => {
    const escaped = path.replaceAll('\\', '\\\\').replaceAll('"', String.raw`\"`);
    return `a[href*="${escaped}"]`;
};

export function LinksTab({result, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div>
            <Recommendations items={result.recommendations} ns="links" tab="links"/>

            {result.brokenRead === false && <p className={styles.warn}>{t('links.internalUnavailable')}</p>}

            <h4 className={styles.sectionTitle}>{t('links.summary')}</h4>
            <ul className={styles.statList}>
                <li>{t('links.total')}: <strong>{result.total}</strong></li>
                <li>{t('links.internal')}: <strong>{result.internal}</strong></li>
                <li>{t('links.external')}: <strong>{result.external}</strong> · <span className={styles.note}>{t('links.externalNote')}</span></li>
            </ul>

            {result.broken.length > 0 && (
                <>
                    <h4 className={styles.sectionTitle}>{t('links.broken')}</h4>
                    {result.broken.map(link => (
                        <div key={link.path} className={styles.rec}>
                            <span className={`${styles.impact} ${styles.impact_critical}`}>
                                {t(`links.reasons.${link.reason}`)}
                            </span>
                            <div className={styles.recBody}>
                                <div className={styles.recTitle}>{link.path}</div>
                                <Button size="small" variant="outlined" label={t('a11y.highlight')} onClick={() => onHighlight(selectorFor(link.path))}/>
                            </div>
                        </div>
                    ))}
                </>
            )}

        </div>
    );
}

LinksTab.propTypes = {
    result: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired
};
