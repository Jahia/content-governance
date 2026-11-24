import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {Recommendations} from './Recommendations';

/**
 * Jahia health: one result card per rule (unpublished changes, missing translations, raw keys,
 * placeholders), each content or text an occurrence, then what the check read of the page.
 */
export function JahiaTab({result, onHighlightText}) {
    const {t} = useGovernanceTranslation('audit');
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
            <Recommendations items={result.recommendations} ns="jahia" tab="jahia" itemAction={itemAction}/>
            <FactBlock title={t('jahia.summary')}
                       rows={[
                           result.pageStatus && {key: 'status', label: t('jahia.pageStatus'), value: t(`jahia.statuses.${result.pageStatus}`, result.pageStatus)},
                           result.scanned !== null && {key: 'scanned', label: t('jahia.scanned', {count: result.scanned})},
                           result.activeLanguages.length > 0 && {key: 'languages', label: t('jahia.languages'), value: result.activeLanguages.join(', ')}
                       ].filter(Boolean)}/>
        </div>
    );
}

JahiaTab.propTypes = {
    result: PropTypes.object.isRequired,
    onHighlightText: PropTypes.func.isRequired
};
