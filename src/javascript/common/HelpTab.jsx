import React from 'react';
import PropTypes from 'prop-types';
import {Tooltip} from '@jahia/moonstone';
import styles from './HelpTab.module.css';

/**
 * A TabItem, or the header of a section, with a Moonstone tooltip saying what it is for, followed by
 * a note when there is one (why the tab cannot be used, how many issues its badge counts). The
 * tooltip opens on hover and on keyboard focus, and the tab is described by it. The selected tab or
 * the open section shows none: the pointer is still on it, and its tooltip would cover the content
 * the editor came to read.
 */
export const HelpTab = ({help, note, isSelected, className, children}) => (
    <Tooltip className={`${styles.helpTab} ${isSelected ? styles.selected : ''} ${className || ''}`} label={note ? `${help} ${note}` : help}>
        {children}
    </Tooltip>
);

HelpTab.propTypes = {
    help: PropTypes.string.isRequired,
    note: PropTypes.string,
    // The tab is the one shown, or the section is open: no tooltip
    isSelected: PropTypes.bool,
    // Added to the anchor of the tooltip, such as a full width for the header of a section
    className: PropTypes.string,
    children: PropTypes.element.isRequired
};
