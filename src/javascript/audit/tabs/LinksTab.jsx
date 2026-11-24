import React from 'react';
import PropTypes from 'prop-types';
import {Button} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {Recommendations} from './Recommendations';

// A broken link names a path of the repository, which its address ends with (/cms/render/default/en/sites/...
// .html), with no other page whose path starts with it: /sites/x/news, and never /sites/x/news-archive
const selectorFor = path => {
    const escaped = path.replaceAll('\\', '\\\\').replaceAll('"', String.raw`\"`);
    return [`a[href$="${escaped}.html"]`, `a[href*="${escaped}.html?"]`, `a[href*="${escaped}.html#"]`, `a[href$="${escaped}"]`].join(', ');
};

/** The links of the page: one result card per rule, each broken link an occurrence, then the counts of the page. */
export function LinksTab({result, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');
    const itemAction = (rec, item) => (rec.rule === 'links.broken' ?
        <Button size="small" variant="outlined" label={t('a11y.highlight')} onClick={() => onHighlight(selectorFor(item.text))}/> :
        null);
    const internal = [
        result.checked !== null && result.checked !== undefined && t('links.checked', {count: result.checked}),
        result.skipped > 0 && t('links.skipped', {count: result.skipped})
    ].filter(Boolean);

    return (
        <div>
            <Recommendations items={result.recommendations} ns="links" tab="links" itemAction={itemAction}/>
            {result.total !== undefined && (
                <FactBlock title={t('links.summary')}
                           rows={[
                               {key: 'total', label: t('links.total'), value: result.total},
                               {key: 'internal', label: t('links.internal'), value: [result.internal, ...internal].join(' · ')},
                               {key: 'external', label: t('links.external'), value: result.external}
                           ]}
                           note={t('links.externalNote')}/>
            )}
        </div>
    );
}

LinksTab.propTypes = {
    // The values of the rule links.broken, none when the site turned it off: {total, internal, external, checked,
    // skipped, broken: [{path, reason}]}, and the recommendations
    result: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired
};
