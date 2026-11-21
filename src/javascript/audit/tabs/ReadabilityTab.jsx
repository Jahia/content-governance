import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Recommendations} from './Recommendations';
import {HeadingOutline} from './HeadingOutline';
import styles from './Tabs.module.css';
import {languageDisplayName} from '../../common/locale';

/** The page language's name in the editor's language, else its code. */
function languageName(lang, uiLanguage) {
    return languageDisplayName(lang, uiLanguage, lang.toUpperCase());
}

/** The page's heading counts and its outline; an audit stored before 1.10 on a short page has neither. */
function Structure({result, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');
    return (
        <>
            {result.h1Count !== undefined && (
                <>
                    <h4 className={styles.sectionTitle}>{t('readability.structure')}</h4>
                    <ul className={styles.statList}>
                        <li>{t('readability.h1Count')}: <strong>{result.h1Count}</strong></li>
                        <li>{t('readability.headingSkips')}: <strong>{result.headingSkips}</strong></li>
                    </ul>
                </>
            )}
            <h4 className={styles.sectionTitle}>{t('readability.outline.title')}</h4>
            <HeadingOutline outline={result.outline} total={result.headingCount} onHighlight={onHighlight}/>
        </>
    );
}

Structure.propTypes = {
    result: PropTypes.object.isRequired,
    onHighlight: PropTypes.func.isRequired
};

/** The statistics that the rule readability.statistics gives, and the headings that readability.headings gives. */
export function ReadabilityTab({result, onHighlight}) {
    const {t, i18n} = useGovernanceTranslation('audit');
    // A site that turns one of the two rules off hides what it gives
    const structure = (result.outline !== undefined || result.h1Count !== undefined) && <Structure result={result} onHighlight={onHighlight}/>;

    if (result.spaced === undefined) {
        return (
            <div>
                <Recommendations items={result.recommendations} ns="readability" tab="readability"/>
                {structure}
            </div>
        );
    }

    if (result.empty) {
        return (
            <div>
                <p className={styles.note}>{t('readability.noText', {count: result.words || 0})}</p>
                {structure}
            </div>
        );
    }

    // By band: a LIX score reads the other way round from a Flesch one
    let scoreBandClass = styles.cardGood;
    if (result.band === 'veryDifficult') {
        scoreBandClass = styles.cardBad;
    } else if (result.band === 'difficult') {
        scoreBandClass = styles.band_ni;
    }

    return (
        <div>
            {!result.scored && (
                <p className={styles.note}>
                    {t(result.spaced ? 'readability.noFormula' : 'readability.noWords', {language: languageName(result.lang, i18n.language)})}
                </p>
            )}
            {(result.scored || result.readingMinutes !== null) && (
                <div className={styles.cards}>
                    {result.scored && (
                        <div className={`${styles.card} ${scoreBandClass}`}>
                            <span className={styles.cardTitle}>
                                {t('readability.score')} ({result.formula})
                            </span>
                            <span className={styles.cardValue}>{result.score}</span>
                            <span className={styles.cardHint}>{t(`readability.bands.${result.band}`)}</span>
                        </div>
                    )}
                    {result.gradeLevel !== null && (
                        <div className={styles.card}>
                            <span className={styles.cardTitle}>{t('readability.gradeLevel')}</span>
                            <span className={styles.cardValue}>{result.gradeLevel}</span>
                            <span className={styles.cardHint}>Flesch-Kincaid</span>
                        </div>
                    )}
                    {result.readingMinutes !== null && (
                        <div className={styles.card}>
                            <span className={styles.cardTitle}>{t('readability.readingTime')}</span>
                            <span className={styles.cardValue}>{result.readingMinutes}</span>
                            <span className={styles.cardHint}>{t('readability.minutes')}</span>
                        </div>
                    )}
                </div>
            )}

            <Recommendations items={result.recommendations} ns="readability" tab="readability"/>

            <h4 className={styles.sectionTitle}>{t('readability.textStats')}</h4>
            <ul className={styles.statList}>
                {result.spaced ? (
                    <>
                        <li>{t('readability.words')}: <strong>{result.words}</strong></li>
                        <li>{t('readability.sentences')}: <strong>{result.sentences}</strong></li>
                        <li>{t('readability.avgSentenceLength')}: <strong>{result.avgSentenceLength}</strong></li>
                        <li>{t('readability.longSentences', {max: result.longSentenceWords})}: <strong>{result.longSentences}</strong></li>
                        <li>{t('readability.paragraphs')}: <strong>{result.paragraphs}</strong>
                            {result.avgWordsPerParagraph !== null && ` (${t('readability.avgWordsPerParagraph')}: ${result.avgWordsPerParagraph})`}
                        </li>
                    </>
                ) : (
                    <>
                        <li>{t('readability.characters')}: <strong>{result.characters}</strong></li>
                        <li>{t('readability.sentences')}: <strong>{result.sentences}</strong></li>
                        <li>{t('readability.paragraphs')}: <strong>{result.paragraphs}</strong></li>
                    </>
                )}
            </ul>

            {structure}
        </div>
    );
}

ReadabilityTab.propTypes = {
    result: PropTypes.object.isRequired,
    // Shows an element of the page from its selector (a heading of the outline)
    onHighlight: PropTypes.func.isRequired
};
