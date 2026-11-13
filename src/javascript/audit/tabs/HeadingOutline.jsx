import React from 'react';
import PropTypes from 'prop-types';
import {Button, Chip, Typography, Visibility} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import styles from './Tabs.module.css';

/**
 * The page's headings as a tree: one row per heading of the main content, in page order, indented
 * by level, with what is wrong with it (a level skipped, no text) and a button that shows it on
 * the page. An audit stored before the outline existed has none: it says to re-run.
 */
export function HeadingOutline({outline, total, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');

    if (!outline) {
        return <p className={styles.note}>{t('readability.outline.rerun')}</p>;
    }
    if (outline.length === 0) {
        return <p className={styles.note}>{t('readability.outline.none')}</p>;
    }

    // A selector names one element, unless the page repeats an id: the rank keeps the keys apart
    const seen = new Map();
    const rows = outline.map(heading => {
        const rank = seen.get(heading.selector) || 0;
        seen.set(heading.selector, rank + 1);
        return {...heading, key: `${heading.selector}#${rank}`};
    });

    return (
        <>
            <p className={styles.note}>{t('readability.outline.intro')}</p>
            <ol className={styles.outline} aria-label={t('readability.outline.title')}>
                {rows.map(heading => (
                    <li key={heading.key} className={`${styles.outlineRow} ${styles[`outlineLevel${heading.level}`]}`}>
                        <Chip label={`H${heading.level}`} color={heading.level === 1 ? 'accent' : 'default'}/>
                        <div className={styles.outlineText}>
                            <Typography component="span" variant="body">
                                {heading.empty ? t('readability.outline.noText') : heading.text}
                            </Typography>
                        </div>
                        {Boolean(heading.skippedFrom) && (
                            <Chip color="warning"
                                  label={t('readability.outline.skipped', {from: heading.skippedFrom, to: heading.level})}/>
                        )}
                        {heading.empty && <Chip color="danger" label={t('readability.outline.empty')}/>}
                        <Button variant="ghost"
                                size="small"
                                icon={<Visibility/>}
                                aria-label={`${t('a11y.highlight')}: H${heading.level} ${heading.text}`}
                                title={t('a11y.highlight')}
                                onClick={() => onHighlight(heading.selector)}/>
                    </li>
                ))}
            </ol>
            {total > outline.length && (
                <p className={styles.note}>{t('readability.outline.more', {shown: outline.length, count: total})}</p>
            )}
        </>
    );
}

HeadingOutline.propTypes = {
    // [{level, text, selector, skippedFrom?, empty?}], absent on an audit stored before 1.10
    outline: PropTypes.arrayOf(PropTypes.shape({
        level: PropTypes.number.isRequired,
        text: PropTypes.string,
        selector: PropTypes.string,
        skippedFrom: PropTypes.number,
        empty: PropTypes.bool
    })),
    // Headings of the main content, which may be more than the outline lists
    total: PropTypes.number,
    onHighlight: PropTypes.func.isRequired
};
