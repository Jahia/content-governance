import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Button, Loader, Typography} from '@jahia/moonstone';
import {FactBlock} from '../../common/results/FactBlock';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {BarList, Histogram} from '../charts/Charts';
import styles from './Tabs.module.css';
import cit from './CitationsTab.module.css';
import {toLocaleTag} from '../../common/locale';

// An unparsable value is shown as BotRank sent it; Date never throws, it is only invalid.
const formatDate = iso => {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
};

const formatDay = day => {
    const d = new Date(`${day}T12:00:00Z`);
    return Number.isNaN(d.getTime()) ? day : d.toLocaleDateString();
};

// A position as BotRank sends it (two decimals), shown with one.
const formatRank = (rank, lang) => Number(rank).toLocaleString(toLocaleTag(lang), {maximumFractionDigits: 1});

/** The theme's sub line: how many questions, and its own change when BotRank gave one. */
const tagSub = (tag, t) => {
    const parts = [t('citations.tagPrompts', {count: tag.promptCount})];
    if (typeof tag.variation === 'number') {
        parts.push(variationText(tag.variation, t));
    }

    return parts.join(' · ');
};

const variationText = (value, t) => {
    if (value === 0) {
        return t('citations.variationSame');
    }

    return t(value > 0 ? 'citations.variationUp' : 'citations.variationDown', {count: Math.abs(value)});
};

/** Host and path of a cited URL, shown apart: the host says whose page it is. Anything else stays whole. */
const URL_PARTS = /^[a-z][a-z0-9+.-]*:\/\/([^/?#]+)([^#]*)/i;
const splitUrl = url => {
    const m = URL_PARTS.exec(url || '');
    return m ? {host: m[1].replace(/^www\./i, ''), path: m[2]} : {host: url, path: ''};
};

/** The name of an AI engine, as BotRank names it when the interface has no text for it. */
const surfaceName = (surface, t) => t('citations.surface.' + surface.aiSurface, surface.aiSurface);

/**
 * One question that cites the page, as a fact row: its text, then its share of the page's citations
 * over the same 30 days, its market, when it last cited the page and by which engines.
 */
const promptRow = (prompt, pageTotal, t) => {
    const share = pageTotal > 0 ? Math.min(1, prompt.citationCount / pageTotal) : 0;
    const meta = [
        prompt.market && prompt.market.toUpperCase(),
        t('citations.shareOfPage', {percent: Math.round(share * 100)}),
        prompt.lastCitationDay && t('citations.lastCited', {day: formatDay(prompt.lastCitationDay)}),
        typeof prompt.variation === 'number' && variationText(prompt.variation, t),
        ...(prompt.aiSurfaces || []).map(surface => `${surfaceName(surface, t)} ${surface.citationCount}`)
    ].filter(Boolean);
    return {
        key: prompt.id,
        label: (
            <>
                {prompt.text}
                <span className={cit.rowMeta}>{meta.join(' · ')}</span>
            </>
        ),
        value: `${prompt.citationCount} ${t('citations.citationsUnit', {count: prompt.citationCount})}`
    };
};

/**
 * A page cited in the same answers, as a fact row: whose it is, in how many answers the two meet, in how
 * many of them it comes before this page, and the average position of each among the sources (1 is the
 * first source).
 */
const coCitedRow = (page, t, lang) => {
    const {host, path} = splitUrl(page.url);
    const shared = page.sharedAnswerCount || 0;
    const ahead = Math.min(page.outrankingAnswerCount || 0, shared);
    const theirs = page.averageRank;
    const ours = page.requestedPageAverageRank;
    const ranked = typeof theirs === 'number' && typeof ours === 'number';
    const meta = [
        shared > 0 && t('citations.aheadOf', {ahead, count: shared}),
        ranked && `${t('citations.positionsLabel')} ${formatRank(theirs, lang)} · ${t('citations.positionsYours')} ${formatRank(ours, lang)}`,
        !ranked && typeof theirs === 'number' && t('citations.coCitedRankOnly', {theirs: formatRank(theirs, lang)}),
        t('citations.coCitedTotal', {count: page.citationCount}),
        typeof page.variation === 'number' && variationText(page.variation, t)
    ].filter(Boolean);
    return {
        key: page.url,
        label: (
            <>
                <span className={cit.host}>{host}</span>
                {path && path !== '/' && <span className={cit.path}>{path}</span>}
                <span className={cit.rowMeta}>{meta.join(' · ')}</span>
            </>
        ),
        value: shared > 0 ? `${ahead}/${shared} ${t('citations.aheadUnit')}` : null
    };
};



/** The first few co-cited pages, the rest one click away: ten rows in a drawer is a wall. */
const CO_CITED_SHOWN = 5;
const CoCitedList = ({pages, t}) => {
    // The UI language, so a position reads 6,5 in French: the browser's own locale may differ.
    const {i18n} = useGovernanceTranslation('geo');
    const [all, setAll] = useState(false);
    const shown = all ? pages : pages.slice(0, CO_CITED_SHOWN);
    return (
        <>
            <FactBlock title={t('citations.coCited')} note={t('citations.coCitedIntro')} rows={shown.map(page => coCitedRow(page, t, i18n?.language))}/>
            {pages.length > CO_CITED_SHOWN && (
                <Button
                    className={cit.more}
                    variant="ghost"
                    label={all ? t('citations.showFewer') : t('citations.showAll', {count: pages.length - CO_CITED_SHOWN})}
                    onClick={() => setAll(!all)}
                />
            )}
        </>
    );
};

CoCitedList.propTypes = {pages: PropTypes.array.isRequired, t: PropTypes.func.isRequired};

/** The headline numbers of the citations of a page: how often, its change over 30 days, its rank in the site. */
const citationScores = (page, t) => [
    {key: 'citations', label: t('citations.tab'), value: page.citations, hint: t('citations.verdict', {count: page.citations})},
    ...(typeof page.variation === 'number' ? [{key: 'variation', label: t('citations.variationLabel'), value: variationText(page.variation, t), band: page.variation >= 0 ? 'good' : 'average'}] : []),
    ...(page.rank ? [{key: 'rank', label: t('citations.rankLabel'), value: `${page.rank}/${page.rankOf}`, hint: t('citations.rank', {rank: page.rank, of: page.rankOf})}] : [])
];

/** What frames the numbers: citations since tracking began, several addresses, a mapping, a withheld change. */
const citationFacts = (page, t) => [
    page.everCitations > page.citations && {key: 'ever', label: t('citations.everCitations', {count: page.everCitations})},
    (page.urls || []).length > 1 && {key: 'urls', label: t('citations.severalUrls', {count: page.urls.length})},
    page.via === 'mapping' && {key: 'mapped', label: t('citations.mapped')},
    page.variation === null && page.previousRunDays === 0 && {key: 'withheld', label: t('citations.variationWithheld')}
].filter(Boolean);

/**
 * GEO-4: whether AI answers cite this page, from the last BotRank sync of the site.
 *
 * Everything shown comes from BotRank, a partner system: prompt texts, tag names and URLs are
 * rendered as text, never as markup.
 */
export const CitationsTab = ({data, failed}) => {
    const {t} = useGovernanceTranslation('geo');

    if (failed) {
        return <ResultBanner variant="danger" title={t('citations.loadErrorTitle')}>{t('citations.loadError')}</ResultBanner>;
    }

    if (!data) {
        return <Loader size="big"/>;
    }

    const page = data.page;
    const asOf = (
        <Typography variant="caption" className={styles.explain}>
            {data.latestAnswerAt ? t('citations.asOf', {date: formatDate(data.latestAnswerAt)}) : t('citations.asOfUnknown')}
            {data.syncedAt ? ` ${t('citations.syncedAt', {date: formatDate(data.syncedAt)})}` : ''}
            {data.status === 'running' ? ` ${t('citations.running')}` : ''}
        </Typography>
    );

    if (!data.syncedAt) {
        return <ResultBanner variant="info" title={t('citations.notSyncedTitle')}>{t('citations.notSynced')}</ResultBanner>;
    }

    if (!data.published) {
        return (
            <div>
                {asOf}
                <ResultBanner variant="info" title={t('citations.notPublishedTitle')}>{t('citations.notPublished')}</ResultBanner>
            </div>
        );
    }

    if (!page) {
        return (
            <div>
                {asOf}
                <ResultBanner variant="info" title={t('citations.neverTitle')}>{t('citations.never')}</ResultBanner>
            </div>
        );
    }

    if (!page.recent) {
        return (
            <div>
                {asOf}
                <ResultBanner variant="info" title={t('citations.notRecentTitle')}>
                    {t('citations.notRecent', {count: page.everCitations})}
                </ResultBanner>
            </div>
        );
    }

    const surfaces = page.aiSurfaces || [];
    const history = page.history || [];
    const prompts = page.prompts || [];
    const tags = page.tags || [];
    const coCited = page.coCited || [];

    return (
        <div>
            {asOf}
            <ScoreBlock items={citationScores(page, t)}/>
            <FactBlock rows={citationFacts(page, t)}/>
            {page.detailsMissing && (
                <ResultBanner variant="warning" title={t('citations.detailsMissingTitle')}>{t('citations.detailsMissing')}</ResultBanner>
            )}

            {surfaces.length > 0 && (
                <>
                    <Typography variant="heading" component="h3" className={styles.sectionTitle}>{t('citations.engines')}</Typography>
                    <BarList rows={surfaces.map(s => ({key: s.aiSurface, label: surfaceName(s, t), value: s.citationCount}))}/>
                </>
            )}

            {history.length > 0 && (
                <>
                    <Typography variant="heading" component="h3" className={styles.sectionTitle}>{t('citations.history')}</Typography>
                    <Histogram
                        rows={history.map(d => ({key: d.date, label: '', value: d.citationCount, day: d.date}))}
                        format={String}
                        tipFor={r => formatDay(r.day)}
                        axisLeft={formatDay(history[0].date)}
                        axisRight={formatDay(history.at(-1).date)}
                    />
                    <Typography variant="caption" className={styles.explain}>{t('citations.historyNote')}</Typography>
                </>
            )}

            <FactBlock title={t('citations.prompts')} rows={prompts.map(p => promptRow(p, page.citations, t))}/>

            {tags.length > 0 && (
                <section className={cit.section}>
                    <Typography variant="heading" component="h3" className={styles.sectionTitle}>{t('citations.tags')}</Typography>
                    <Typography variant="caption" className={styles.explain}>{t('citations.tagsIntro')}</Typography>
                    <BarList rows={tags.map(tag => ({
                        key: tag.id,
                        label: tag.name,
                        sublabel: tagSub(tag, t),
                        value: tag.citationCount
                    }))}
                    />
                </section>
            )}

            {coCited.length > 0 && <CoCitedList pages={coCited} t={t}/>}

            <Typography variant="caption" className={styles.explain}>{t('citations.source')}</Typography>
        </div>
    );
};

CitationsTab.propTypes = {
    data: PropTypes.object,
    failed: PropTypes.bool
};

CitationsTab.defaultProps = {data: null, failed: false};
