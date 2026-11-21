import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {Recommendations} from './Recommendations';
import styles from './Tabs.module.css';

// A broken link names a path of the repository, which its address ends with (/cms/render/default/en/sites/...
// .html), with no other page whose path starts with it: /sites/x/news, and never /sites/x/news-archive
const selectorFor = path => {
    const escaped = path.replaceAll('\\', '\\\\').replaceAll('"', String.raw`\"`);
    return [`a[href$="${escaped}.html"]`, `a[href*="${escaped}.html?"]`, `a[href*="${escaped}.html#"]`, `a[href$="${escaped}"]`].join(', ');
};

/** The links of the page, as the check of the server reads them, and the internal links a visitor cannot open. */
export function LinksTab({result, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <div>
            <Recommendations items={result.recommendations} ns="links" tab="links"/>

            <h4 className={styles.sectionTitle}>{t('links.summary')}</h4>
            <ul className={styles.statList}>
                <li>{t('links.total')}: <strong>{result.total}</strong></li>
                <li>
                    {t('links.internal')}: <strong>{result.internal}</strong>
                    {result.checked !== null && <span> · {t('links.checked', {count: result.checked})}</span>}
                    {result.skipped > 0 && <span> · {t('links.skipped', {count: result.skipped})}</span>}
                </li>
                <li>{t('links.external')}: <strong>{result.external}</strong> · <span className={styles.note}>{t('links.externalNote')}</span></li>
            </ul>

            {result.broken.length > 0 && (
                <>
                    <h4 className={styles.sectionTitle}>{t('links.broken')}</h4>
                    {result.broken.map(link => (
                        <div key={link.path} className={styles.rec}>
                            <span className={`${styles.impact} ${styles.impact_critical}`}>
                                {t(`links.reasons.${link.reason}`, link.reason)}
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
    // The facts of the check of the server: {total, internal, external, checked, skipped, broken: [{path, reason}], recommendations}
    result: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired
};
