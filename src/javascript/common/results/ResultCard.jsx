import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../i18n';
import {LEVELS} from './levels';
import styles from './Results.module.css';

/**
 * One finding of one rule: its level as a left border and a word, what is wrong and how to fix it,
 * the id of its rule, its occurrences, and its action on the right.
 *
 * The action follows the occurrences. A rule with one occurrence, or with none (a rule of the whole
 * page), shows that occurrence's action on the card. A rule with several occurrences shows each
 * action on its own row. The `menu` (the ⋯ of the actions of other modules) always stays on the card.
 *
 * Example: "Images must have alternative text", Serious, 3 occurrences: each image is a row with its
 * own Highlight, and the card keeps only ⋯ with the assistant's Fix.
 */
export const ResultCard = ({level, title, detail, rule, chips, occurrences = [], occurrencesLabel, action, menu, children, isCompact}) => {
    const {t} = useGovernanceTranslation('common');
    const single = occurrences.length === 1 ? occurrences[0] : null;
    const cardAction = single ? single.action : action;

    return (
        <article className={`${styles.card} ${styles['level_' + level]} ${isCompact ? styles.compact : ''}`}>
            <div className={styles.cardHead}>
                <div className={styles.cardBody}>
                    <span className={styles.levelWord}>{t(`results.levels.${level}`)}</span>
                    {chips && <span className={styles.chips}>{chips}</span>}
                    <div className={styles.cardTitle}>{title}</div>
                    {detail && <div className={styles.cardDetail}>{detail}</div>}
                    {rule && <code className={styles.rule}>{rule}</code>}
                </div>
                {(cardAction || menu) && (
                    <div className={styles.cardActions}>
                        {cardAction}
                        {menu}
                    </div>
                )}
            </div>
            {children}
            {single && single.content && (
                <div className={styles.single}>{single.content}</div>
            )}
            {occurrences.length > 1 && (
                <details className={styles.occurrences}>
                    <summary>{occurrencesLabel || t('results.occurrences', {count: occurrences.length})}</summary>
                    <ul>
                        {occurrences.map(occurrence => (
                            <li key={occurrence.key} className={styles.row}>
                                <div className={styles.rowContent}>{occurrence.content}</div>
                                {occurrence.action && <div className={styles.rowAction}>{occurrence.action}</div>}
                            </li>
                        ))}
                    </ul>
                </details>
            )}
        </article>
    );
};

export const occurrenceShape = PropTypes.shape({
    key: PropTypes.string.isRequired,
    // What and where: an element, a content, a value
    content: PropTypes.node,
    // Its one action, such as Highlight or Edit
    action: PropTypes.node
});

ResultCard.propTypes = {
    level: PropTypes.oneOf(LEVELS).isRequired,
    title: PropTypes.node.isRequired,
    // What to do, in one or two sentences
    detail: PropTypes.node,
    // The id of the rule, as the catalog names it
    rule: PropTypes.string,
    // Extra marks beside the level, such as a WCAG level
    chips: PropTypes.node,
    occurrences: PropTypes.arrayOf(occurrenceShape),
    // The label of the folded occurrences, "N items" when omitted
    occurrencesLabel: PropTypes.string,
    // The action of a rule with no occurrence
    action: PropTypes.node,
    // The ⋯ of the actions of other modules
    menu: PropTypes.node,
    // More about the finding, between the head and the occurrences
    children: PropTypes.node,
    // A passed check in a "passed" list: one line, no border
    isCompact: PropTypes.bool
};

/**
 * The checks that passed in a section, folded in one line that opens to their compact cards.
 *
 * @param {{items: Array<{key: string, title: string, detail: ?string, rule: ?string}>}} props the passed checks
 */
export const PassedFold = ({items}) => {
    const {t} = useGovernanceTranslation('common');
    if (items.length === 0) {
        return null;
    }
    return (
        <details className={styles.passed}>
            <summary>{t('results.passed', {count: items.length})}</summary>
            {items.map(item => (
                <ResultCard key={item.key} isCompact level="passed" title={item.title} detail={item.detail} rule={item.rule}/>
            ))}
        </details>
    );
};

PassedFold.propTypes = {
    items: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        title: PropTypes.node.isRequired,
        detail: PropTypes.node,
        rule: PropTypes.string
    })).isRequired
};
