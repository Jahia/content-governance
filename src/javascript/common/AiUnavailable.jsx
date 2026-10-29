import React from 'react';
import PropTypes from 'prop-types';
import {Button, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from './i18n';
import styles from './AiUnavailable.module.css';

/**
 * An AI feature shown greyed out: its title, its button disabled, and why. Used wherever the
 * module would call a model while no model is configured on the server.
 */
export const AiUnavailable = ({title, actionLabel}) => {
    const {t} = useGovernanceTranslation('common');
    return (
        <div className={styles.box}>
            <Typography component="h4" variant="subheading" weight="bold" className={styles.title}>{title}</Typography>
            <div className={styles.row}>
                <Button size="small" variant="outlined" label={actionLabel || t('ai.generate')} isDisabled/>
                <Typography variant="caption" className={styles.hint}>{t('ai.notConfigured')}</Typography>
            </div>
        </div>
    );
};

AiUnavailable.propTypes = {
    title: PropTypes.string.isRequired,
    actionLabel: PropTypes.string
};
