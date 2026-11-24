import React from 'react';
import PropTypes from 'prop-types';
import styles from './Results.module.css';

/** The bands of a score: its colour, and nothing at all for a score with no band. */
export const BANDS = ['good', 'average', 'poor'];

/**
 * The headline numbers of a section, one tile each, coloured by their band.
 *
 * Example: the WCAG levels A, AA, AAA and best practices, each with the share of its automated rules
 * that passed.
 */
export const ScoreBlock = ({title, items}) => {
    if (items.length === 0) {
        return null;
    }
    return (
        <section className={styles.scores}>
            {title && <h4 className={styles.blockTitle}>{title}</h4>}
            <div className={styles.scoreGrid}>
                {items.map(item => (
                    <div key={item.key} className={`${styles.score} ${item.band ? styles['band_' + item.band] : ''}`}>
                        <span className={styles.scoreLabel}>{item.label}</span>
                        <span className={styles.scoreValue}>{item.value}</span>
                        {item.caption && <span className={styles.scoreCaption}>{item.caption}</span>}
                        {item.hint && <span className={styles.scoreHint}>{item.hint}</span>}
                    </div>
                ))}
            </div>
        </section>
    );
};

ScoreBlock.propTypes = {
    title: PropTypes.string,
    items: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        label: PropTypes.node.isRequired,
        value: PropTypes.node.isRequired,
        // What the value counts, such as "automated rules passed"
        caption: PropTypes.node,
        hint: PropTypes.node,
        band: PropTypes.oneOf(BANDS)
    })).isRequired
};
