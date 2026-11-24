import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {useDrawerDisplay} from '../../common/useDrawerDisplay';
import {FactBlock} from '../../common/results/FactBlock';
import {Recommendations} from './Recommendations';

/**
 * Jahia health: the page summary, which the check read of the page, where the cfg of the server shows it,
 * then one result card per rule (unpublished changes, missing translations, raw keys, placeholders), each
 * content or text an occurrence.
 */
export function JahiaTab({result, onHighlightText}) {
    const {t} = useGovernanceTranslation('audit');
    const {pageSummary} = useDrawerDisplay();
    const highlight = text => <Button size="small" variant="outlined" label={t('a11y.highlight')} onClick={() => onHighlightText(text)}/>;
    // The server gives no locator for a text of the page: a raw key is found by itself, a placeholder by its text
    const itemAction = (rec, item) => {
        if (rec.rule === 'health.rawI18nKeys') {
            return highlight(item.text);
        }
        if (rec.rule === 'health.placeholderText') {
            return highlight((item.context || item.text).slice(0, 40));
        }
        return null;
    };

    return (
        <div>
            {pageSummary && (
                <FactBlock title={t('jahia.summary')}
                           rows={[
                               result.pageStatus && {key: 'status', label: t('jahia.pageStatus'), value: t(`jahia.statuses.${result.pageStatus}`, result.pageStatus)},
                               result.scanned !== null && {key: 'scanned', label: t('jahia.scanned', {count: result.scanned})},
                               result.activeLanguages.length > 0 && {key: 'languages', label: t('jahia.languages'), value: result.activeLanguages.join(', ')}
                           ].filter(Boolean)}/>
            )}
            <Recommendations items={result.recommendations} ns="jahia" tab="jahia" itemAction={itemAction}/>
        </div>
    );
}

JahiaTab.propTypes = {
    result: PropTypes.object.isRequired,
    onHighlightText: PropTypes.func.isRequired
};
