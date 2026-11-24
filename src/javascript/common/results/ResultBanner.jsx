import React from 'react';
import PropTypes from 'prop-types';
import {Banner, Check, Information, Warning} from '@jahia/moonstone';
import styles from './Results.module.css';

const ICON = {neutral: <Information/>, info: <Information/>, warning: <Warning/>, danger: <Warning/>, success: <Check/>};

/**
 * A line about a section rather than a finding: the state of a run (running, failed, not configured),
 * what a check covers, or "No issues". It holds no finding: a finding is a result card.
 *
 * The title is a few words in bold, above the text. A sentence is the text, never the title: a banner
 * with no title shows its icon beside the text, in regular weight.
 *
 * Example: the title "This check did not finish", the text "It stopped after 30 seconds", and Re-run.
 */
export const ResultBanner = ({variant = 'neutral', title, children, action}) => (
    <Banner className={`${styles.banner} ${title ? '' : styles.bannerText}`}
            variant={variant === 'success' ? 'neutral' : variant}
            title={title}
            iconStart={ICON[variant]}
    >
        {children}
        {action && <div className={styles.bannerAction}>{action}</div>}
    </Banner>
);

ResultBanner.propTypes = {
    // success is a neutral banner with a check mark, such as "No issues"
    variant: PropTypes.oneOf(['neutral', 'info', 'warning', 'danger', 'success']),
    // A few words, such as "Not published yet"
    title: PropTypes.string,
    children: PropTypes.node.isRequired,
    action: PropTypes.node
};
