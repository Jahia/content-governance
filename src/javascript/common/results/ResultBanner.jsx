import React from 'react';
import PropTypes from 'prop-types';
import {Banner, Check, Information, Warning} from '@jahia/moonstone';
import styles from './Results.module.css';

const ICON = {neutral: <Information/>, info: <Information/>, warning: <Warning/>, danger: <Warning/>, success: <Check/>};

/**
 * A line about a section rather than a finding: the state of a run (running, failed, not configured),
 * what a check covers, or "No issues". It holds no finding: a finding is a result card.
 *
 * Example: "The accessibility step did not finish in 30 seconds", with Re-run.
 */
export const ResultBanner = ({variant = 'neutral', title, children, action}) => (
    <Banner className={styles.banner}
            variant={variant === 'success' ? 'neutral' : variant}
            title={title}
            iconStart={ICON[variant]}
    >
        {children || action ? (
            <>
                {children}
                {action && <div className={styles.bannerAction}>{action}</div>}
            </>
        ) : null}
    </Banner>
);

ResultBanner.propTypes = {
    // success is a neutral banner with a check mark, such as "No issues"
    variant: PropTypes.oneOf(['neutral', 'info', 'warning', 'danger', 'success']),
    title: PropTypes.string.isRequired,
    children: PropTypes.node,
    action: PropTypes.node
};
