import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {PassedFold, ResultCard} from '../../common/results/ResultCard';
import {ResultBanner} from '../../common/results/ResultBanner';
import results from '../../common/results/Results.module.css';
import {fetchErrorHint} from '../util/fetchError';

/**
 * What the robots.txt check found: a result card when a crawler policy is missing, a passed line when
 * it names AI crawlers, or a banner when the fetch failed. A failed fetch is not an absent file:
 * "everything is allowed" would be a finding we never made.
 */
function RobotsVerdict({robots, t}) {
    const named = robots.namedAiBotCount || 0;
    const disallowed = robots.disallowedAiBotCount || 0;
    const disallowedText = disallowed > 0 ? t('files.robots.someDisallowed', {count: disallowed}) : null;
    if (!robots.present && robots.error) {
        return <ResultBanner variant="warning" title={`${t('files.robots.unreachable')} ${fetchErrorHint(t, robots.error)}`.trim()}/>;
    }
    if (!robots.present) {
        return <ResultCard level="moderate" title={t('files.robots.absent')}/>;
    }
    if (named === 0) {
        return <ResultCard level="moderate" title={t('files.robots.noneNamed')} detail={disallowedText}/>;
    }
    return <PassedFold items={[{key: 'robots', title: t('files.robots.someNamed', {count: named}), detail: disallowedText}]}/>;
}

RobotsVerdict.propTypes = {robots: PropTypes.object.isRequired, t: PropTypes.func.isRequired};

/**
 * What the llms.txt check found. A fetch that failed is told apart from a file that is not there:
 * the first is a connection problem, a banner, the second a finding about the site, a card.
 */
function LlmsVerdict({llms, t}) {
    if (llms.servedHtmlInstead) {
        return <ResultCard level="serious" title={t('files.llms.servedHtml')}/>;
    }
    if (llms.present) {
        return <PassedFold items={[{key: 'llms', title: t('files.llms.present')}]}/>;
    }
    if (llms.error && llms.status === null) {
        return <ResultBanner variant="warning" title={`${t('files.llms.unreachable')} ${fetchErrorHint(t, llms.error)}`.trim()}/>;
    }
    return <ResultCard level="moderate" title={t('files.llms.absent', {status: llms.status ?? '—'})}/>;
}

LlmsVerdict.propTypes = {llms: PropTypes.object.isRequired, t: PropTypes.func.isRequired};

/** The files of the site that AI crawlers read: robots.txt and llms.txt, each a verdict and its facts. */
export const SiteFilesTab = ({report}) => {
    const {t} = useGovernanceTranslation('geo');
    const files = report.siteFiles || {};
    const robots = files.robots || {};
    const llms = files.llms || {};
    const llmsFull = files.llmsFull || {};
    const sitemaps = robots.sitemaps || [];

    return (
        <div>
            <RobotsVerdict robots={robots} t={t}/>
            {robots.present && (
                <FactBlock title={t('files.robots.title')}
                           rows={(robots.agents || []).map(a => ({
                               key: a.name,
                               label: (
                                   <>
                                       {a.name}
                                       <span className={results.muted}> · {a.namedExplicitly ? t('files.robots.byName') : t('files.robots.byWildcard')}</span>
                                   </>
                               ),
                               value: `${a.allowed ? t('files.robots.allowed') : t('files.robots.disallowed')} · ${a.rule || t('files.robots.noRule')}`
                           }))}
                           note={sitemaps.length > 0 ? `${t('files.robots.sitemaps', {count: sitemaps.length})} ${sitemaps.join(', ')}` : null}/>
            )}

            <LlmsVerdict llms={llms} t={t}/>
            <FactBlock title={t('files.llms.title')}
                       rows={[
                           ...(llms.present ? [
                               {key: 'h1', label: t('files.llms.h1'), value: llms.h1 || t('crawler.missing')},
                               {key: 'summary', label: t('files.llms.summary'), value: llms.hasSummary ? t('crawler.present') : t('crawler.missing')},
                               {key: 'sections', label: t('files.llms.sections'), value: llms.sections ?? 0},
                               {key: 'links', label: t('files.llms.links'), value: llms.links ?? 0},
                               {key: 'type', label: t('files.llms.contentType'), value: llms.contentType || '—'}
                           ] : []),
                           {key: 'full', label: t('files.llmsFull.label'), value: `${llmsFull.present ? t('crawler.present') : t('crawler.missing')} (${llmsFull.status ?? '—'})`}
                       ]}/>

            <ResultBanner variant="info" title={t('files.editedInSettings')}/>
        </div>
    );
};

SiteFilesTab.propTypes = {
    report: PropTypes.object.isRequired
};
