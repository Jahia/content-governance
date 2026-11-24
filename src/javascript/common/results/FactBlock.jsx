import React from 'react';
import PropTypes from 'prop-types';
import styles from './Results.module.css';

/**
 * What a check measured, with no judgement: label and value lines, under an optional heading. A
 * problem among them is a result card of its rule, never a mark in this block.
 *
 * Example: the 16 AI crawlers, each with the words it read and what it found; the card "2 AI
 * crawlers cannot read this page" sits above it.
 */
export const FactBlock = ({title, rows, note}) => {
    if (rows.length === 0 && !note) {
        return null;
    }
    return (
        <section className={styles.facts}>
            {title && <h4 className={styles.blockTitle}>{title}</h4>}
            {rows.length > 0 && (
                <ul className={styles.factList}>
                    {rows.map(row => (
                        <li key={row.key} className={styles.fact} style={row.indent ? {paddingLeft: `${row.indent * 16}px`} : undefined}>
                            <span className={styles.factLabel}>{row.label}</span>
                            {row.value !== undefined && row.value !== null && <span className={styles.factValue}>{row.value}</span>}
                            {row.action && <span className={styles.factAction}>{row.action}</span>}
                        </li>
                    ))}
                </ul>
            )}
            {note && <p className={styles.factNote}>{note}</p>}
        </section>
    );
};

FactBlock.propTypes = {
    title: PropTypes.string,
    rows: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        label: PropTypes.node.isRequired,
        value: PropTypes.node,
        // One action on the row, such as the eye that shows a heading
        action: PropTypes.node,
        // Steps of indentation, such as the level of a heading
        indent: PropTypes.number
    })).isRequired,
    // One line under the rows, such as "20 of 34 shown"
    note: PropTypes.node
};
