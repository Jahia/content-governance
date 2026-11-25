import React, {useCallback, useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Loader, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {ResultBanner} from '../common/results/ResultBanner';
import results from '../common/results/Results.module.css';
import styles from './GovernanceDrawer.module.css';

/**
 * The fixers of a finding (`contentGovernanceFixer`, drawer/entries.js): one button per fixer on the finding and on
 * each occurrence, and, once one runs, its proposals under it. A run shows a loader with Cancel, then each proposal
 * with Copy, Apply when the fixer applies a proposal, and Regenerate; or a banner when the fixer failed. A new run,
 * Cancel or the end of the card aborts the run under way.
 */

/**
 * The state of the fixers of one finding.
 *
 * @param {{finding: object, path: string, language: string, previewDocument: function(): ?Document}} props what a
 *     fixer receives
 * @returns {object} `{target, status, proposals, note, error, applied, start, cancel, regenerate, apply}`
 */
export function useFixRun({finding, path, language, previewDocument}) {
    const [state, setState] = useState({target: null, status: 'idle', proposals: [], note: null, error: null, applied: {}});
    const controller = useRef(null);

    const abort = useCallback(() => {
        controller.current?.abort();
        controller.current = null;
    }, []);

    // The end of the card ends the run under way
    useEffect(() => abort, [abort]);

    const start = useCallback((fixer, index) => {
        abort();
        const own = new AbortController();
        controller.current = own;
        const occurrence = index === null ? null : finding.items[index];
        setState({target: {fixer, index}, status: 'running', proposals: [], note: null, error: null, applied: {}});
        Promise.resolve()
            .then(() => fixer.run({finding, occurrence, path, language, signal: own.signal, document: previewDocument?.() || null}))
            .then(answer => {
                if (!own.signal.aborted) {
                    const proposals = Array.isArray(answer?.proposals) ? answer.proposals.filter(proposal => typeof proposal?.text === 'string') : [];
                    setState(s => ({...s, status: 'ready', proposals, note: typeof answer?.note === 'string' ? answer.note : null}));
                }
            })
            .catch(error => {
                if (!own.signal.aborted) {
                    console.warn(`[content-governance] the fixer "${fixer.key}" failed`, error);
                    setState(s => ({...s, status: 'error', error: error?.message || String(error)}));
                }
            });
    }, [abort, finding, path, language, previewDocument]);

    const cancel = useCallback(() => {
        abort();
        setState({target: null, status: 'idle', proposals: [], note: null, error: null, applied: {}});
    }, [abort]);

    const regenerate = useCallback(() => {
        if (state.target) {
            start(state.target.fixer, state.target.index);
        }
    }, [start, state.target]);

    const apply = useCallback((proposal, at) => {
        const {fixer, index} = state.target;
        const occurrence = index === null ? null : finding.items[index];
        setState(s => ({...s, applied: {...s.applied, [at]: 'applying'}}));
        Promise.resolve()
            .then(() => fixer.apply({proposal, finding, occurrence, path, language}))
            .then(() => setState(s => ({...s, applied: {...s.applied, [at]: 'applied'}})))
            .catch(error => {
                console.warn(`[content-governance] the fixer "${fixer.key}" could not apply a proposal`, error);
                setState(s => ({...s, applied: {...s.applied, [at]: 'failed'}, error: error?.message || String(error)}));
            });
    }, [state.target, finding, path, language]);

    return {...state, start, cancel, regenerate, apply};
}

/** One button per fixer, labelled by the translation of its label, such as `ai-assistant:fixers.rewrite.label`. */
export const FixerButtons = ({fixers, isDisabled, onRun}) => {
    const {t} = useGovernanceTranslation();
    return fixers.map(fixer => (
        <Button key={fixer.key}
                size="small"
                variant="ghost"
                label={t(fixer.label)}
                isDisabled={isDisabled}
                data-fixer={fixer.key}
                onClick={() => onRun(fixer)}/>
    ));
};

FixerButtons.propTypes = {
    fixers: PropTypes.arrayOf(PropTypes.shape({key: PropTypes.string.isRequired, label: PropTypes.string.isRequired})).isRequired,
    isDisabled: PropTypes.bool,
    onRun: PropTypes.func.isRequired
};

/** Copies a text, and answers whether the browser let it. */
const copy = async text => {
    try {
        await globalThis.navigator.clipboard.writeText(text);
        return true;
    } catch (error) {
        console.warn('[content-governance] the browser refused to copy the proposal', error);
        return false;
    }
};

/**
 * One proposal: what it replaces (`original`) above its text, why (`reason`) as a caption, then its actions. The
 * other fields of a proposal are for its fixer only.
 */
const Proposal = ({proposal, at, state, canApply, onApply}) => {
    const {t} = useGovernanceTranslation('common.results.fixers');
    const [copied, setCopied] = useState(false);
    const applied = state.applied[at];
    return (
        <li className={styles.proposal}>
            {typeof proposal.original === 'string' && proposal.original !== '' && (
                <del className={results.muted}>{proposal.original}</del>
            )}
            <p className={styles.proposalText}>{proposal.text}</p>
            {typeof proposal.reason === 'string' && proposal.reason !== '' && (
                <Typography variant="caption" className={results.muted}>{proposal.reason}</Typography>
            )}
            <span className={styles.proposalActions}>
                <Button size="small" variant="outlined" label={copied ? t('copied') : t('copy')} onClick={() => copy(proposal.text).then(setCopied)}/>
                {canApply && (
                    <Button size="small"
                            variant="outlined"
                            label={applied === 'applied' ? t('applied') : t('apply')}
                            isDisabled={applied === 'applying' || applied === 'applied'}
                            onClick={() => onApply(proposal, at)}/>
                )}
            </span>
        </li>
    );
};

Proposal.propTypes = {
    proposal: PropTypes.shape({text: PropTypes.string.isRequired, original: PropTypes.string, reason: PropTypes.string}).isRequired,
    at: PropTypes.number.isRequired,
    state: PropTypes.object.isRequired,
    canApply: PropTypes.bool.isRequired,
    onApply: PropTypes.func.isRequired
};

/** What the fixer under way gives: a loader with Cancel, its proposals with Regenerate, or a banner when it failed. */
export const FixerPanel = ({run}) => {
    const {t} = useGovernanceTranslation('common.results.fixers');
    const {t: tAll} = useGovernanceTranslation();
    const label = tAll(run.target.fixer.label);
    if (run.status === 'running') {
        return (
            <div className={styles.fixer} aria-live="polite">
                <Loader size="small"/>
                <Typography>{t('running', {fixer: label})}</Typography>
                <Button size="small" variant="ghost" label={t('cancel')} onClick={run.cancel}/>
            </div>
        );
    }
    const regenerate = <Button size="small" variant="outlined" label={t('regenerate')} onClick={run.regenerate}/>;
    return (
        <div className={styles.fixer} aria-live="polite">
            {run.error && <ResultBanner variant="danger" title={t('failed', {fixer: label})} action={regenerate}>{run.error}</ResultBanner>}
            {run.status === 'ready' && run.proposals.length === 0 && <ResultBanner>{t('none')}</ResultBanner>}
            {run.status === 'ready' && run.proposals.length > 0 && (
                <ul className={styles.proposals}>
                    {run.proposals.map((proposal, at) => (
                        // eslint-disable-next-line react/no-array-index-key
                        <Proposal key={at} proposal={proposal} at={at} state={run} canApply={typeof run.target.fixer.apply === 'function'} onApply={run.apply}/>
                    ))}
                </ul>
            )}
            {run.note && <Typography variant="caption" className={results.muted}>{run.note}</Typography>}
            <span className={styles.proposalActions}>
                {run.status === 'ready' && regenerate}
                <Button size="small" variant="ghost" label={t('close')} onClick={run.cancel}/>
            </span>
        </div>
    );
};

FixerPanel.propTypes = {
    run: PropTypes.shape({
        target: PropTypes.shape({fixer: PropTypes.object.isRequired}).isRequired,
        status: PropTypes.string.isRequired,
        proposals: PropTypes.array.isRequired,
        note: PropTypes.string,
        error: PropTypes.string,
        applied: PropTypes.object.isRequired,
        cancel: PropTypes.func.isRequired,
        regenerate: PropTypes.func.isRequired,
        apply: PropTypes.func.isRequired
    }).isRequired
};
