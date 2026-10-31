import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Chip, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import styles from './GovernanceDrawer.module.css';

const SEVERITY_COLORS = {critical: 'danger', serious: 'danger', moderate: 'warning', minor: 'default'};

/**
 * "Fix first": the most serious findings of the sections measured so far, whatever their section,
 * folded to one line under the section bar so the preview keeps its height. Each opens the section
 * and the tab that shows it.
 */
export const FixFirst = ({findings, onGo}) => {
    const {t} = useGovernanceTranslation('drawer');
    const [open, setOpen] = useState(false);
    if (findings.length === 0) {
        return null;
    }
    const [top] = findings;

    return (
        <section className={styles.fixFirst} aria-label={t('fixFirst.title')}>
            <div className={styles.fixFirstLine}>
                <Typography weight="bold" className={styles.fixFirstTitle}>{t('fixFirst.title')}</Typography>
                {!open && <Typography className={styles.fixFirstTop} title={top.title}>{top.title}</Typography>}
                {!open && findings.length > 1 && (
                    <Typography variant="caption" className={styles.fixFirstMore}>{t('fixFirst.more', {count: findings.length - 1})}</Typography>
                )}
                <Button variant="ghost"
                        size="small"
                        className={styles.fixFirstToggle}
                        label={open ? t('fixFirst.hide') : t('fixFirst.show')}
                        aria-expanded={open}
                        aria-controls="content-governance-fix-first"
                        onClick={() => setOpen(o => !o)}/>
            </div>
            {open && (
                <ol id="content-governance-fix-first" className={styles.fixFirstList}>
                    {findings.map(finding => (
                        <li key={finding.key} className={styles.fixFirstItem}>
                            <Chip color={SEVERITY_COLORS[finding.severity] || 'default'} label={t(`fixFirst.severity.${finding.severity}`)}/>
                            <Typography className={styles.fixFirstText}>{finding.title}</Typography>
                            <Button variant="ghost"
                                    size="small"
                                    label={t('fixFirst.go')}
                                    aria-label={t('fixFirst.goLabel', {title: finding.title})}
                                    onClick={() => onGo(finding)}/>
                        </li>
                    ))}
                </ol>
            )}
        </section>
    );
};

FixFirst.propTypes = {
    // Ranked findings (drawer/findings.js), at most five
    findings: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        section: PropTypes.string.isRequired,
        tab: PropTypes.string,
        severity: PropTypes.string.isRequired,
        title: PropTypes.string.isRequired
    })).isRequired,
    // Opens the section and tab of a finding
    onGo: PropTypes.func.isRequired
};
