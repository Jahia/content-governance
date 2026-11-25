import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {Check, HelpOutline, Information, Report, Warning} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../i18n';
import {LEVELS} from './levels';
import styles from './Results.module.css';

/** The icon of each level. Each has its own shape, so that the level never rests on its colour alone (WCAG 1.4.1). */
const LEVEL_ICON = {serious: Report, moderate: Warning, minor: Information, passed: Check, review: HelpOutline};

/**
 * One finding of one rule. Its level is the colour of the left border, and the icon that opens the
 * title line, whose name and tooltip are the word of the level. The title line ends with the actions.
 * What to do follows. The last line holds the occurrences, and the id of the rule at its right end.
 *
 * The occurrences always fold under "Findings (N)", one or many, and each row holds its own action,
 * such as Highlight or Edit. Only a rule with no occurrence, a rule of the whole page, shows its action
 * on the title line. The `menu` (the ⋮ of the actions of other modules) always ends the title line.
 * An occurrence may show a line under its row (`below`), and the line of the occurrences may end with an
 * action of its own, such as Generate all.
 *
 * Example: "Open Graph tags are missing: og:image", Moderate: an orange border, a triangle, the title
 * and its ⋮, the advice, then "Findings (1)" with `seo.openGraph` at the right of the same line.
 */
export const ResultCard = ({level, title, detail, rule, chips, occurrences = [], occurrencesLabel, occurrencesAction, occurrencesHeader, isOpen, action, menu, children, isCompact}) => {
    const {t} = useGovernanceTranslation('common');
    const cardAction = occurrences.length === 0 ? action : null;
    const isFolded = occurrences.length > 0;
    const LevelIcon = LEVEL_ICON[level];
    const word = t(`results.levels.${level}`);
    const [open, setOpen] = useState(Boolean(isOpen));

    // A card that asks to show its occurrences, such as when Generate all starts, opens them
    useEffect(() => {
        if (isOpen) {
            setOpen(true);
        }
    }, [isOpen]);

    return (
        <article className={`${styles.card} ${styles['level_' + level]} ${isCompact ? styles.compact : ''}`}>
            <div className={styles.cardHead}>
                <div className={styles.titleLine}>
                    {/* The word as a tooltip, and as hidden text for a screen reader */}
                    <span className={styles.levelIcon} title={word}>
                        <LevelIcon aria-hidden="true"/>
                        <span className={styles.srOnly}>{word}</span>
                    </span>
                    <div className={styles.cardTitle}>
                        {chips && <span className={styles.chips}>{chips}</span>}
                        {title}
                    </div>
                    {(cardAction || menu) && (
                        <div className={styles.cardActions}>
                            {cardAction}
                            {menu}
                        </div>
                    )}
                </div>
                {detail && <div className={styles.cardDetail}>{detail}</div>}
            </div>
            {children}
            {(rule || isFolded) && (
                <div className={`${styles.cardFoot} ${isFolded ? styles.folded : ''}`}>
                    {/* Before the occurrences, so that it floats at the right of their first line */}
                    {rule && <code className={`${styles.rule} ${styles.footRule}`}>{rule}</code>}
                    {isFolded && (
                        <details className={styles.occurrences} open={open} onToggle={event => setOpen(event.currentTarget.open)}>
                            <summary>
                                {occurrencesLabel || t('results.occurrences', {count: occurrences.length})}
                                {occurrencesAction && <span className={styles.summaryAction}>{occurrencesAction}</span>}
                            </summary>
                            {occurrencesHeader}
                            <ul>
                                {occurrences.map(occurrence => (
                                    <li key={occurrence.key} className={styles.row}>
                                        <div className={styles.rowContent}>{occurrence.content}</div>
                                        {occurrence.action && <div className={styles.rowAction}>{occurrence.action}</div>}
                                        {occurrence.below && <div className={styles.rowBelow}>{occurrence.below}</div>}
                                    </li>
                                ))}
                            </ul>
                        </details>
                    )}
                </div>
            )}
        </article>
    );
};

export const occurrenceShape = PropTypes.shape({
    key: PropTypes.string.isRequired,
    // What and where: an element, a content, a value
    content: PropTypes.node,
    // Its one action, such as Highlight or Edit
    action: PropTypes.node,
    // What shows under the row, across its whole width, such as the proposals of a fixer
    below: PropTypes.node
});

ResultCard.propTypes = {
    level: PropTypes.oneOf(LEVELS).isRequired,
    title: PropTypes.node.isRequired,
    // What to do, in one or two sentences
    detail: PropTypes.node,
    // The id of the rule, as the catalog names it, at the bottom right
    rule: PropTypes.string,
    // Extra marks at the start of the title, such as a WCAG level
    chips: PropTypes.node,
    occurrences: PropTypes.arrayOf(occurrenceShape),
    // The label of the folded occurrences, "Findings (N)" when omitted
    occurrencesLabel: PropTypes.string,
    // An action at the end of the line of the occurrences, such as Generate all
    occurrencesAction: PropTypes.node,
    // What shows above the open occurrences, such as the progress of Generate all
    occurrencesHeader: PropTypes.node,
    // Opens the occurrences when it turns true
    isOpen: PropTypes.bool,
    // The action of a rule with no occurrence
    action: PropTypes.node,
    // The ⋮ of the actions of other modules, at the end of the title line
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
