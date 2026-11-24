import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {Recommendations} from './Recommendations';
import {HeadingOutline} from './HeadingOutline';
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
                <FactBlock title={t('readability.structure')}
                           rows={[
                               {key: 'h1', label: t('readability.h1Count'), value: result.h1Count},
                               {key: 'skips', label: t('readability.headingSkips'), value: result.headingSkips}
                           ]}/>
            )}
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
                <ResultBanner title={t('readability.noText', {count: result.words || 0})}/>
                {structure}
            </div>
        );
    }

    // By band: a LIX score reads the other way round from a Flesch one
    let band = 'good';
    if (result.band === 'veryDifficult') {
        band = 'poor';
    } else if (result.band === 'difficult') {
        band = 'average';
    }
    const scores = [
        result.scored && {key: 'score', label: `${t('readability.score')} (${result.formula})`, value: result.score, hint: t(`readability.bands.${result.band}`), band},
        result.gradeLevel !== null && {key: 'grade', label: t('readability.gradeLevel'), value: result.gradeLevel, hint: 'Flesch-Kincaid'},
        result.readingMinutes !== null && {key: 'reading', label: t('readability.readingTime'), value: result.readingMinutes, hint: t('readability.minutes')}
    ].filter(Boolean);
    const paragraphs = result.avgWordsPerParagraph !== null && result.avgWordsPerParagraph !== undefined ?
        `${result.paragraphs} (${t('readability.avgWordsPerParagraph')}: ${result.avgWordsPerParagraph})` :
        result.paragraphs;
    const stats = result.spaced ? [
        {key: 'words', label: t('readability.words'), value: result.words},
        {key: 'sentences', label: t('readability.sentences'), value: result.sentences},
        {key: 'avg', label: t('readability.avgSentenceLength'), value: result.avgSentenceLength},
        {key: 'long', label: t('readability.longSentences', {max: result.longSentenceWords}), value: result.longSentences},
        {key: 'paragraphs', label: t('readability.paragraphs'), value: paragraphs}
    ] : [
        {key: 'characters', label: t('readability.characters'), value: result.characters},
        {key: 'sentences', label: t('readability.sentences'), value: result.sentences},
        {key: 'paragraphs', label: t('readability.paragraphs'), value: result.paragraphs}
    ];

    return (
        <div>
            {!result.scored && (
                <ResultBanner title={t(result.spaced ? 'readability.noFormula' : 'readability.noWords', {language: languageName(result.lang, i18n.language)})}/>
            )}
            <ScoreBlock items={scores}/>
            <Recommendations items={result.recommendations} ns="readability" tab="readability"/>
            <FactBlock title={t('readability.textStats')} rows={stats}/>
            {structure}
        </div>
    );
}

ReadabilityTab.propTypes = {
    result: PropTypes.object.isRequired,
    // Shows an element of the page from its selector (a heading of the outline)
    onHighlight: PropTypes.func.isRequired
};
