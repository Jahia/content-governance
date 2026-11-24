import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {PassedFold, ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import results from '../../common/results/Results.module.css';
import {fetchErrorHint} from '../util/fetchError';

/** A crawler that gets far less text than a browser is usually looking at a JS-only page. */
const THIN_RATIO = 0.5;

/**
 * Below this many words the initial HTML is an empty shell, whoever asks for it.
 * A page whose content only arrives through JavaScript looks like this to every
 * agent, control included, so the ratio test above would never fire.
 */
const MIN_CONTROL_WORDS = 50;

function verdictOf(report, t) {
    if (!report.published) {
        return {tone: 'warn', text: t('crawler.verdict.unpublished')};
    }
    // No answer at all is a connection problem on the server, not a refusal by the site.
    if (report.unreachableCount > 0 && !report.blockedCount) {
        return {
            tone: 'bad',
            text: `${t('crawler.verdict.unreachable', {count: report.unreachableCount})} ${fetchErrorHint(t, report.unreachableError)}`.trim()
        };
    }
    if (report.blockedCount > 0) {
        return {tone: 'bad', text: t('crawler.verdict.blocked', {count: report.blockedCount})};
    }
    const control = report.controlWords || 0;
    if (control < MIN_CONTROL_WORDS) {
        return {tone: 'bad', text: t('crawler.verdict.empty', {count: control})};
    }
    const thin = (report.agents || []).some(
        a => a.html && a.html.words < control * THIN_RATIO
    );
    if (thin) {
        return {tone: 'warn', text: t('crawler.verdict.thin')};
    }
    return {tone: 'good', text: t('crawler.verdict.ok')};
}

const LEVEL_OF_TONE = {bad: 'serious', warn: 'moderate'};

/** What one crawler found in the page it got, as a short line: "H1 · canonical · 42 links". */
function foundOf(agent, t) {
    if (!agent.html) {
        return agent.error ? t('crawler.noResponse') : '—';
    }
    return [
        agent.html.h1Count && t('crawler.h1'),
        agent.html.metaDescription && t('crawler.metaDescription'),
        agent.html.canonical && t('crawler.canonical'),
        agent.html.jsonLd && t('crawler.jsonLd'),
        `${agent.html.links} ${t('crawler.links')}`
    ].filter(Boolean).join(' · ');
}

/**
 * The crawlers of the GEO check: the verdict as a result card (or a passed line), each crawler that
 * failed as an occurrence of it, then every crawler and what it read as a fact block.
 */
export const CrawlerTab = ({report}) => {
    const {t} = useGovernanceTranslation('geo');
    const verdict = verdictOf(report, t);
    const control = report.controlWords || 0;
    const agents = report.agents || [];
    const controlAgent = agents.find(a => a.status === 200 && a.html);
    const metaRefresh = controlAgent && controlAgent.html.metaRefresh;

    if (!report.published) {
        return <ResultBanner variant="warning">{verdict.text}</ResultBanner>;
    }

    const isThin = a => a.status === 200 && control > 0 && (a.html ? a.html.words : 0) < control * THIN_RATIO;
    const explanations = [
        report.blockedButAllowedCount > 0 && t('crawler.explain.blockedButAllowed', {count: report.blockedButAllowedCount}),
        report.reachableButDisallowedCount > 0 && t('crawler.explain.reachableButDisallowed', {count: report.reachableButDisallowedCount}),
        agents.some(a => a.status === 403) && t('crawler.explain.403'),
        control < MIN_CONTROL_WORDS && t('crawler.explain.empty'),
        control >= MIN_CONTROL_WORDS && agents.some(a => a.html && a.html.words < control * THIN_RATIO) && t('crawler.explain.jsOnly')
    ].filter(Boolean);
    const failing = agents.filter(a => a.status !== 200 || isThin(a)).map(a => ({
        key: a.name,
        content: (
            <>
                <strong>{a.name}</strong>
                <span className={results.muted}> · {a.status === null || a.status === undefined ? '—' : a.status}
                    {isThin(a) ? ` · ${t('crawler.thinMark')}` : ''} · {foundOf(a, t)}</span>
            </>
        )
    }));

    return (
        <div>
            {verdict.tone === 'good' ?
                <PassedFold items={[{key: 'verdict', title: verdict.text}]}/> :
                <ResultCard level={LEVEL_OF_TONE[verdict.tone]}
                            title={verdict.text}
                            detail={explanations.join(' ')}
                            occurrences={failing}/>}

            <FactBlock rows={[
                {key: 'url', label: t('drawer.url'), value: <a href={report.url} target="_blank" rel="noopener noreferrer">{report.url}</a>},
                metaRefresh && {key: 'refresh', label: t('crawler.metaRefresh', {url: metaRefresh})}
            ].filter(Boolean)}/>

            <FactBlock rows={agents.map(a => ({
                key: a.name,
                label: (
                    <>
                        {a.name}
                        <span className={results.muted}> · {foundOf(a, t)}</span>
                    </>
                ),
                value: `${a.status === null || a.status === undefined ? '—' : a.status} · ${a.ms}ms · ${t('crawler.wordCount', {count: a.html ? a.html.words : 0})}`
            }))}/>
        </div>
    );
};

CrawlerTab.propTypes = {
    report: PropTypes.object.isRequired
};
