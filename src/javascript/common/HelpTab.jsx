import React from 'react';
import PropTypes from 'prop-types';
import {Tooltip} from '@jahia/moonstone';
import styles from './HelpTab.module.css';

/**
 * A TabItem with a Moonstone tooltip saying what the tab is for, followed by a note when there is
 * one (why the tab cannot be used, how many issues its badge counts). The tooltip opens on hover
 * and on keyboard focus, and the tab is described by it.
 */
export const HelpTab = ({help, note, children}) => (
    <Tooltip className={styles.helpTab} label={note ? `${help} ${note}` : help}>
        {children}
    </Tooltip>
);

HelpTab.propTypes = {
    help: PropTypes.string.isRequired,
    note: PropTypes.string,
    children: PropTypes.element.isRequired
};
