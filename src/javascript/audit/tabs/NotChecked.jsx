import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {ResultBanner} from '../../common/results/ResultBanner';
import results from '../../common/results/Results.module.css';

/**
 * The rules of a tab that run in the browser and that no check gave a result for: no module of this Jahia
 * registers a check of their evaluator, or the check left them out. They are neither passed nor failed.
 *
 * Example: a site keeps the rule `acme.brandContrast` of a module that is not installed any more.
 */
export function NotChecked({rules}) {
    const {t} = useGovernanceTranslation('audit');

    return (
        <ResultBanner title={t('notChecked.title', {count: rules.length})}>
            {t('notChecked.note')}
            <ul className={results.bannerList}>
                {rules.map(rule => (
                    <li key={rule.rule}>
                        {rule.label} <code className={results.rule}>{rule.rule}</code>
                    </li>
                ))}
            </ul>
        </ResultBanner>
    );
}

NotChecked.propTypes = {
    // {rule, label}: the id of each rule and its label in the editor's interface language
    rules: PropTypes.arrayOf(PropTypes.shape({
        rule: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired
    })).isRequired
};
