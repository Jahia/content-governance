import React from 'react';
import PropTypes from 'prop-types';
import {Badge, Chip} from '@jahia/moonstone';

/**
 * The number of findings on a tab. Red only when one of them is critical or serious; grey
 * otherwise, so a drawer full of minor advice does not read as a page full of errors.
 */
export const CountBadge = ({count, isSerious}) => (isSerious ?
    <Badge label={String(count)} color="danger"/> :
    <Chip label={String(count)} color="default"/>);

CountBadge.propTypes = {
    count: PropTypes.number.isRequired,
    // One finding at least is critical or serious
    isSerious: PropTypes.bool
};
