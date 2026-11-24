import React from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {ResultBanner} from '../../common/results/ResultBanner';
import {ScoreBlock} from '../../common/results/ScoreBlock';
import {Recommendations} from './Recommendations';

function formatMs(value) {
    if (value === null || value === undefined) {
        return '-';
    }

    return value >= 1000 ? `${(value / 1000).toFixed(2)} s` : `${Math.round(value)} ms`;
}

function formatBytes(bytes) {
    if (bytes > 1024 * 1024) {
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    return `${Math.round(bytes / 1024)} kB`;
}

const METRICS = ['lcp', 'cls', 'inp', 'ttfb', 'dcl', 'load'];

// The thresholds of the Core Web Vitals (web.dev): good up to the first, poor above the second
const BANDS = {lcp: [2500, 4000], cls: [0.1, 0.25], ttfb: [800, 1800]};

const bandOf = (key, value) => {
    const limits = BANDS[key];
    if (!limits || value === null || value === undefined) {
        return undefined;
    }
    if (value <= limits[0]) {
        return 'good';
    }
    return value <= limits[1] ? 'average' : 'poor';
};

function metricTile(key, metrics, t) {
    const value = metrics[key];
    const isApprox = key === 'lcp' && metrics.lcpApprox;
    let display = formatMs(value);
    if (key === 'cls') {
        display = value === null ? '-' : value.toFixed(3);
    }
    const shown = isApprox ? `≈ ${display}` : display;
    return {
        key,
        label: t(`vitals.metrics.${key}`),
        value: key === 'inp' ? t('vitals.inpNa') : shown,
        hint: isApprox ? t('vitals.approx') : undefined,
        band: bandOf(key, value)
    };
}

/** The web vitals of the page, measured in the editor's browser: its numbers, then what the page loads. */
export function VitalsTab({result}) {
    const {t} = useGovernanceTranslation('audit');
    const {metrics, diagnostics} = result;
    const images = [
        diagnostics.images,
        diagnostics.missingDims > 0 && t('vitals.missingDims', {count: diagnostics.missingDims}),
        diagnostics.oversized > 0 && t('vitals.oversized', {count: diagnostics.oversized})
    ].filter(part => part !== false);

    return (
        <div>
            <ResultBanner variant="info">{t('vitals.labNote')}</ResultBanner>
            <ScoreBlock items={METRICS.map(key => metricTile(key, metrics, t))}/>

            {/* The findings of the vitals are rules of the subdomain ecodesign: the Ecodesign tab shows them.
                An audit stored by an older version still has them here. */}
            {result.recommendations?.length > 0 && <Recommendations items={result.recommendations} ns="vitals" tab="vitals"/>}
            {!result.recommendations?.length && <ResultBanner>{t('vitals.findingsInEcodesign')}</ResultBanner>}

            <FactBlock title={t('vitals.diagnostics')}
                       rows={[
                           {key: 'requests', label: t('vitals.requests'), value: diagnostics.requests},
                           {key: 'weight', label: t('vitals.weight'), value: formatBytes(diagnostics.totalBytes)},
                           {key: 'dom', label: t('vitals.domNodes'), value: diagnostics.domNodes},
                           {key: 'images', label: t('vitals.images'), value: images.join(' · ')}
                       ]}/>
            <FactBlock title={t('vitals.largest')}
                       rows={diagnostics.largest.map(r => ({key: r.url, label: <code title={r.url}>{r.name}</code>, value: `${r.type} · ${formatBytes(r.bytes)}`}))}/>
        </div>
    );
}

VitalsTab.propTypes = {
    result: PropTypes.object.isRequired
};
