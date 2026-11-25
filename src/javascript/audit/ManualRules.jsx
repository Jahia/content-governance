import React from 'react';
import PropTypes from 'prop-types';
import {Button, Loader} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {toLocaleTag} from '../common/locale';
import {ResultBanner} from '../common/results/ResultBanner';
import {RuleViews} from '../common/results/RuleViews';
import {Recommendations} from './tabs/Recommendations';
import {manualKey, shownRun} from './manualRuns';

/**
 * The rules on demand of a subdomain (audit/manualRuns.js): a banner with the date of the last run, and who ran it,
 * or "Not run yet", with Run; then what the last run found, as the cards, facts and scores of any rule.
 */
export function ManualRules({manual, id, rules, language}) {
    const {t} = useGovernanceTranslation('audit.manual');
    const {status, run, error} = manual.of(id);
    const running = status === 'running';
    const idle = run ? t('runAgain') : t('run');
    const action = (
        <Button size="small"
                variant="outlined"
                label={running ? t('running') : idle}
                icon={running ? <Loader size="small"/> : undefined}
                isDisabled={running || status === 'loading'}
                onClick={() => manual.run(id)}/>
    );
    let banner;
    if (run) {
        const date = new Date(run.at).toLocaleString(toLocaleTag(language));
        banner = <ResultBanner title={t('lastRunTitle')} action={action}>{run.by ? t('lastRunBy', {date, user: run.by}) : t('lastRun', {date})}</ResultBanner>;
    } else {
        banner = <ResultBanner title={t('notRunTitle')} action={action}>{t('notRun')}</ResultBanner>;
    }
    const shown = run && rules ? shownRun(run.results, rules) : null;
    return (
        <>
            {banner}
            {status === 'error' && <ResultBanner variant="danger" title={t('failed')}>{error}</ResultBanner>}
            {shown && <RuleViews views={shown.views}/>}
            {shown && <Recommendations items={shown.recommendations} ns="recs" tab={manualKey(id)} isQuietWhenEmpty={shown.views.length > 0}/>}
        </>
    );
}

ManualRules.propTypes = {
    // The state of useManualRuns
    manual: PropTypes.shape({of: PropTypes.func.isRequired, run: PropTypes.func.isRequired}).isRequired,
    // The subdomain
    id: PropTypes.string.isRequired,
    // The rules of the site, which word the results; null while they load
    rules: PropTypes.object,
    // The language of the dates: the editor's interface language
    language: PropTypes.string.isRequired
};
