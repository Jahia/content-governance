import React from 'react';
import PropTypes from 'prop-types';
import {Button, Reload} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {ResultBanner} from '../../common/results/ResultBanner';

/**
 * In place of a tab whose check did not finish: why, and a re-run. The other tabs of the audit are
 * shown as measured.
 */
export function IncompleteStep({outcome, onRerun}) {
    const {t} = useGovernanceTranslation('audit');
    const timedOut = outcome.incomplete === 'timedOut';

    return (
        <ResultBanner variant="warning"
                      title={t(timedOut ? 'incomplete.timedOutTitle' : 'incomplete.failedTitle')}
                      action={<Button size="small" variant="outlined" icon={<Reload/>} label={t('drawer.rerun')} onClick={onRerun}/>}
        >
            {timedOut ? t('incomplete.timedOut', {seconds: outcome.seconds}) : t('incomplete.failed', {message: outcome.message})}
        </ResultBanner>
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
