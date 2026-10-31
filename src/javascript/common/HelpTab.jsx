import React from 'react';
import PropTypes from 'prop-types';
import {Tooltip} from '@jahia/moonstone';
import styles from './HelpTab.module.css';

/**
 * A TabItem with a Moonstone tooltip saying what the tab is for, followed by a note when there is
 * one (why the tab cannot be used, how many issues its badge counts). The tooltip opens on hover
 * and on keyboard focus, and the tab is described by it. The selected tab shows none: the pointer is
 * still on a tab just clicked, and its tooltip would cover the content the editor came to read.
 */
export const HelpTab = ({help, note, isSelected, children}) => (
    <Tooltip className={`${styles.helpTab} ${isSelected ? styles.selected : ''}`} label={note ? `${help} ${note}` : help}>
        {children}
    </Tooltip>
);

HelpTab.propTypes = {
    help: PropTypes.string.isRequired,
    note: PropTypes.string,
    // The tab is the one shown: no tooltip
    isSelected: PropTypes.bool,
    children: PropTypes.element.isRequired
};
