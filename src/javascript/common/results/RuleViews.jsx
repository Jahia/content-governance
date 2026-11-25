import React from 'react';
import PropTypes from 'prop-types';
import {FactBlock} from './FactBlock';
import {ScoreBlock} from './ScoreBlock';

/**
 * The results of the rules that show their values (audit/ruleViews.js): a FactBlock for `view: fact`, and a
 * ScoreBlock for `view: score`, each titled by its rule. A rule that also failed shows its result card too, among
 * the recommendations.
 */
export const RuleViews = ({views}) => views.map(view => (view.view === 'score' ?
    <ScoreBlock key={view.key} title={view.title} items={view.items}/> :
    <FactBlock key={view.key} title={view.title} rows={view.items}/>));

RuleViews.propTypes = {
    views: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        view: PropTypes.oneOf(['fact', 'score']).isRequired,
        title: PropTypes.string,
        items: PropTypes.arrayOf(PropTypes.shape({
            key: PropTypes.string.isRequired,
            label: PropTypes.string.isRequired,
            value: PropTypes.string.isRequired,
            band: PropTypes.string
        })).isRequired
    })).isRequired
};
