import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Recommendations} from './Recommendations';
import {SimplifyAssist} from './SimplifyAssist';
import styles from './Tabs.module.css';

/** The page language's name in the editor's language, else its code. */
function languageName(lang, uiLanguage) {
    try {
        return new Intl.DisplayNames([uiLanguage || 'en'], {type: 'language'}).of(lang) || lang.toUpperCase();
    } catch {
        return lang.toUpperCase();
    }
}

export function ReadabilityTab({result, assist}) {
    const {t, i18n} = useGovernanceTranslation('audit');

    if (result.empty) {
        return <p className={styles.note}>{t('readability.noText', {count: result.words || 0})}</p>;
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

            <Recommendations items={result.recommendations} ns="readability"/>

            <SimplifyAssist {...assist}/>

            <h4 className={styles.sectionTitle}>{t('readability.textStats')}</h4>
            <ul className={styles.statList}>
                {result.spaced ? (
                    <>
                        <li>{t('readability.words')}: <strong>{result.words}</strong></li>
                        <li>{t('readability.sentences')}: <strong>{result.sentences}</strong></li>
                        <li>{t('readability.avgSentenceLength')}: <strong>{result.avgSentenceLength}</strong></li>
                        <li>{t('readability.longSentences')}: <strong>{result.longSentences}</strong></li>
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

            <h4 className={styles.sectionTitle}>{t('readability.structure')}</h4>
            <ul className={styles.statList}>
                <li>{t('readability.h1Count')}: <strong>{result.h1Count}</strong></li>
                <li>{t('readability.headingSkips')}: <strong>{result.headingSkips}</strong></li>
            </ul>
        </div>
    );
}

ReadabilityTab.propTypes = {
    result: PropTypes.object.isRequired,
    // Props forwarded to <SimplifyAssist/> (AI status, rewrites, phase, callbacks)
    assist: PropTypes.object.isRequired
};
