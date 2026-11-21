import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import styles from './Tabs.module.css';

/**
 * The rules of a tab that run in the browser and that no check gave a result for: no module of this Jahia
 * registers a check of their evaluator, or the check left them out. They are neither passed nor failed.
 *
 * Example: a site keeps the rule `acme.brandContrast` of a module that is not installed any more.
 */
export function NotChecked({rules}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <>
            <h4 className={styles.sectionTitle}>{t('notChecked.title', {count: rules.length})}</h4>
            <p className={styles.note}>{t('notChecked.note')}</p>
            <ul className={styles.statList}>
                {rules.map(rule => (
                    <li key={rule.rule}>
                        {rule.label} <code className={styles.recRule}>{rule.rule}</code>
                    </li>
                ))}
            </ul>
        </>
    );
}

NotChecked.propTypes = {
    // {rule, label}: the id of each rule and its label in the editor's interface language
    rules: PropTypes.arrayOf(PropTypes.shape({
        rule: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired
    })).isRequired
};
