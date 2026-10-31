import React from 'react';
import PropTypes from 'prop-types';
import {Banner, Button, Reload, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import styles from './Tabs.module.css';

/**
 * In place of a tab whose check did not finish: why, and a re-run. The other tabs of the audit are
 * shown as measured.
 */
export function IncompleteStep({outcome, onRerun}) {
    const {t} = useGovernanceTranslation('audit');
    const timedOut = outcome.incomplete === 'timedOut';

    return (
        <Banner variant="warning" title={t(timedOut ? 'incomplete.timedOutTitle' : 'incomplete.failedTitle')}>
            <Typography>
                {timedOut ? t('incomplete.timedOut', {seconds: outcome.seconds}) : t('incomplete.failed', {message: outcome.message})}
            </Typography>
            <div className={styles.incompleteAction}>
                <Button size="small" variant="outlined" icon={<Reload/>} label={t('drawer.rerun')} onClick={onRerun}/>
            </div>
        </Banner>
    );
}

IncompleteStep.propTypes = {
    // {incomplete: 'timedOut', seconds} or {incomplete: 'failed', message} (runStep)
    outcome: PropTypes.shape({
        incomplete: PropTypes.oneOf(['timedOut', 'failed']).isRequired,
        seconds: PropTypes.number,
        message: PropTypes.string
    }).isRequired,
    onRerun: PropTypes.func.isRequired
};
