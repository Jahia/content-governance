import React, {useCallback, useEffect, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Check, Close, Input, Loader, Reload, Textarea, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import results from '../common/results/Results.module.css';
import styles from './GovernanceDrawer.module.css';

/**
 * The fixers of a finding (`contentGovernanceFixer`, drawer/entries.js). Each occurrence runs on its own, and a
 * finding with no occurrence runs as the target `page`. A run is `running`, `waiting` (in Generate all), `ready`
 * with its proposals, `error`, or `applied`. With no run, the target shows the button of its fixer.
 *
 * Example: "Generate alt" on the row of `bike.jpg` shows a loader with Cancel, then the proposals. The editor picks
 * one, edits it, and Apply hands the edited text to the fixer's `apply`.
 */

/** The target of a finding with no occurrence. */
export const PAGE = 'page';

/** How many occurrences Generate all runs at a time. */
const PARALLEL = 3;

const textOf = (proposals, at) => proposals[at]?.text ?? '';

/**
 * The runs of the fixers of one finding, one per target.
 *
 * @param {{finding: object, path: string, language: string, previewDocument: function(): ?Document}} props what a
 *     fixer receives
 * @returns {object} `{runs, start, cancel, discard, choose, edit, apply, generateAll, cancelAll, applyAll,
 *     discardAll, closeAll}`
 */
export function useFixRuns({finding, path, language, previewDocument}) {
    const [runs, setRuns] = useState({});
    const controllers = useRef(new Map());
    const bulk = useRef(null);
    const latest = useRef(runs);
    latest.current = runs;

    const put = useCallback((target, change) => setRuns(all => {
        const next = {...all};
        if (change === null) {
            delete next[target];
        } else {
            next[target] = {...all[target], ...change};
        }
        return next;
    }), []);

    const abort = useCallback(target => {
        controllers.current.get(target)?.abort();
        controllers.current.delete(target);
    }, []);

    // The end of the card ends every run under way
    useEffect(() => () => {
        bulk.current?.abort();
        controllers.current.forEach(controller => controller.abort());
        controllers.current.clear();
    }, []);

    const occurrenceOf = useCallback(target => (target === PAGE ? null : finding.items[target]), [finding]);

    /** Runs the fixer on a target, and resolves once it answered, failed or was cancelled. */
    const run = useCallback((fixer, target, isBulk) => {
        abort(target);
        const own = new AbortController();
        controllers.current.set(target, own);
        put(target, {fixer, isBulk, status: 'running', proposals: [], note: null, error: null, chosen: 0, text: '', applyError: null});
        return Promise.resolve()
            .then(() => fixer.run({finding, occurrence: occurrenceOf(target), path, language, signal: own.signal, document: previewDocument?.() || null}))
            .then(answer => {
                if (own.signal.aborted) {
                    return;
                }
                const proposals = Array.isArray(answer?.proposals) ? answer.proposals.filter(proposal => typeof proposal?.text === 'string') : [];
                const note = typeof answer?.note === 'string' ? answer.note : null;
                put(target, proposals.length > 0 ?
                    {status: 'ready', proposals, note, chosen: 0, text: textOf(proposals, 0)} :
                    {status: 'error', error: null, note});
            })
            .catch(error => {
                if (!own.signal.aborted) {
                    console.warn(`[content-governance] the fixer "${fixer.key}" failed`, error);
                    put(target, {status: 'error', error: error?.message || String(error)});
                }
            })
            .finally(() => {
                if (controllers.current.get(target) === own) {
                    controllers.current.delete(target);
                }
            });
    }, [abort, put, finding, occurrenceOf, path, language, previewDocument]);

    const start = useCallback((fixer, target) => {
        run(fixer, target, false);
    }, [run]);

    const cancel = useCallback(target => {
        abort(target);
        put(target, null);
    }, [abort, put]);

    const choose = useCallback((target, at) => setRuns(all => ({
        ...all,
        [target]: {...all[target], chosen: at, text: textOf(all[target].proposals, at)}
    })), []);

    const edit = useCallback((target, text) => put(target, {text}), [put]);

    /** Hands the chosen proposal, with the text as the editor left it, to the fixer's `apply`. */
    const apply = useCallback(target => {
        const current = latest.current[target];
        if (!current || current.status !== 'ready' || typeof current.fixer.apply !== 'function') {
            return Promise.resolve();
        }
        const proposal = {...current.proposals[current.chosen], text: current.text};
        put(target, {isApplying: true, applyError: null});
        return Promise.resolve()
            .then(() => current.fixer.apply({proposal, finding, occurrence: occurrenceOf(target), path, language}))
            .then(() => put(target, {status: 'applied', isApplying: false}))
            .catch(error => {
                console.warn(`[content-governance] the fixer "${current.fixer.key}" could not apply a proposal`, error);
                put(target, {isApplying: false, applyError: error?.message || String(error)});
            });
    }, [put, finding, occurrenceOf, path, language]);

    /** Runs the fixer on every occurrence that is not applied, a few at a time. */
    const generateAll = useCallback(fixer => {
        bulk.current?.abort();
        const own = new AbortController();
        bulk.current = own;
        const queue = finding.items.map((item, index) => index).filter(index => latest.current[index]?.status !== 'applied');
        queue.forEach(index => abort(index));
        setRuns(all => {
            const next = {...all};
            queue.forEach(index => {
                next[index] = {fixer, isBulk: true, status: 'waiting', proposals: [], note: null, error: null, chosen: 0, text: '', applyError: null};
            });
            return next;
        });
        const worker = async () => {
            while (queue.length > 0 && !own.signal.aborted) {
                // eslint-disable-next-line no-await-in-loop
                await run(fixer, queue.shift(), true);
            }
        };
        return Promise.all(Array.from({length: Math.min(PARALLEL, queue.length)}, worker));
    }, [abort, run, finding]);

    /** Stops Generate all: the waiting and running occurrences go back to their button, the proposals stay. */
    const cancelAll = useCallback(() => {
        bulk.current?.abort();
        Object.entries(latest.current).forEach(([target, current]) => {
            if (current.status === 'running' || current.status === 'waiting') {
                cancel(target === PAGE ? PAGE : Number(target));
            }
        });
    }, [cancel]);

    const applyAll = useCallback(() => Promise.all(Object.keys(latest.current)
        .filter(target => latest.current[target].status === 'ready')
        .map(target => apply(Number(target)))), [apply]);

    const discardAll = useCallback(() => setRuns(all => Object.fromEntries(Object.entries(all)
        .filter(([, current]) => current.status === 'applied'))), []);

    /** Hides the summary of Generate all: each occurrence keeps its state. */
    const closeAll = useCallback(() => setRuns(all => Object.fromEntries(Object.entries(all)
        .map(([target, current]) => [target, {...current, isBulk: false}]))), []);

    return {runs, start, cancel, discard: cancel, choose, edit, apply, generateAll, cancelAll, applyAll, discardAll, closeAll};
}

/** The button of a fixer, labelled by the translation of its label, such as `ai-assistant:fixers.alt.label`. */
export const FixerButton = ({fixer, onRun, isOnMenu}) => {
    const {t} = useGovernanceTranslation();
    return (
        <Button size="small"
                variant="ghost"
                label={t(fixer.label)}
                data-fixer={fixer.key}
                className={isOnMenu ? styles.menuFixer : undefined}
                onClick={onRun}/>
    );
};

FixerButton.propTypes = {
    fixer: PropTypes.shape({key: PropTypes.string.isRequired, label: PropTypes.string.isRequired}).isRequired,
    onRun: PropTypes.func.isRequired,
    // In the ⋮ of the card rather than on a row
    isOnMenu: PropTypes.bool
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

/** One line that says what went wrong, with an action such as Retry. */
const ErrorLine = ({children, action}) => (
    <div className={styles.fixError} role="alert">
        <span>{children}</span>
        {action}
    </div>
);

ErrorLine.propTypes = {
    children: PropTypes.node.isRequired,
    action: PropTypes.node
};

/**
 * The proposals of a run: the text it replaces, a choice when there are several, the chosen one in a text field the
 * editor may change, the note of the fixer, then Apply, Copy, Regenerate and Discard.
 */
const ProposalBlock = ({current, target, fixes}) => {
    const {t} = useGovernanceTranslation('common.results.fixers');
    const {t: tAll} = useGovernanceTranslation();
    const [copied, setCopied] = useState(false);
    const proposal = current.proposals[current.chosen];
    const name = `cg-fix-${target}-${current.fixer.key}`;
    return (
        <section className={styles.proposalBlock} aria-label={t('proposal', {fixer: tAll(current.fixer.label)})}>
            <Typography variant="caption" weight="bold">{t('proposal', {fixer: tAll(current.fixer.label)})}</Typography>
            {typeof proposal.original === 'string' && proposal.original !== '' && (
                <Typography variant="caption" className={results.muted}>{t('before')} <del>{proposal.original}</del></Typography>
            )}
            {current.proposals.length > 1 && (
                <fieldset className={styles.proposalChoice}>
                    <legend className={results.srOnly}>{t('choose')}</legend>
                    {current.proposals.map((item, at) => (
                        // eslint-disable-next-line react/no-array-index-key
                        <label key={at} className={styles.proposalOption}>
                            <input type="radio" name={name} checked={current.chosen === at} onChange={() => fixes.choose(target, at)}/>
                            <span>{item.text}</span>
                        </label>
                    ))}
                </fieldset>
            )}
            <Textarea value={current.text}
                      aria-label={t('edit')}
                      rows={2}
                      onChange={event => fixes.edit(target, event.target.value)}/>
            {typeof proposal.reason === 'string' && proposal.reason !== '' && (
                <Typography variant="caption" className={results.muted}>{proposal.reason}</Typography>
            )}
            {current.note && <Typography variant="caption" className={results.muted}>{current.note}</Typography>}
            {current.applyError && <ErrorLine>{t('applyFailed', {error: current.applyError})}</ErrorLine>}
            <span className={styles.proposalActions}>
                {typeof current.fixer.apply === 'function' && (
                    <Button size="small"
                            variant="primary"
                            label={t('apply')}
                            isDisabled={current.isApplying || current.text.trim() === ''}
                            onClick={() => fixes.apply(target)}/>
                )}
                <Button size="small" variant="ghost" label={copied ? t('copied') : t('copy')} onClick={() => copy(current.text).then(setCopied)}/>
                <Button size="small" variant="ghost" icon={<Reload/>} label={t('regenerate')} onClick={() => fixes.start(current.fixer, target)}/>
                <span className={styles.grow}/>
                <Button size="small" variant="ghost" label={t('discard')} onClick={() => fixes.discard(target)}/>
            </span>
        </section>
    );
};

ProposalBlock.propTypes = {
    current: PropTypes.object.isRequired,
    target: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
    fixes: PropTypes.object.isRequired
};

/** A proposal of Generate all: one text field, with Regenerate and Discard. */
const CompactProposal = ({current, target, fixes}) => {
    const {t} = useGovernanceTranslation('common.results.fixers');
    return (
        <div className={styles.compactProposal}>
            <Input value={current.text} aria-label={t('edit')} onChange={event => fixes.edit(target, event.target.value)}/>
            <Button size="small" variant="ghost" icon={<Reload/>} aria-label={t('regenerate')} onClick={() => fixes.start(current.fixer, target)}/>
            <Button size="small" variant="ghost" icon={<Close/>} aria-label={t('discard')} onClick={() => fixes.discard(target)}/>
        </div>
    );
};

CompactProposal.propTypes = {
    current: PropTypes.object.isRequired,
    target: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
    fixes: PropTypes.object.isRequired
};

/** What the run of a target shows: nothing with no run, else its state. */
export const FixerState = ({fixes, target}) => {
    const {t} = useGovernanceTranslation('common.results.fixers');
    const current = fixes.runs[target];
    if (!current) {
        return null;
    }
    switch (current.status) {
        case 'waiting':
            return <Typography variant="caption" className={`${styles.fixLine} ${results.muted}`}>{t('waiting')}</Typography>;
        case 'running':
            return (
                <div className={styles.fixLine} aria-live="polite">
                    <Loader size="small"/>
                    <Typography variant="caption">{t('running')}</Typography>
                    <Button size="small" variant="ghost" label={t('cancel')} onClick={() => fixes.cancel(target)}/>
                </div>
            );
        case 'error':
            return (
                <ErrorLine action={<Button size="small" variant="ghost" label={t('retry')} onClick={() => fixes.start(current.fixer, target)}/>}>
                    {current.error ? t('failed', {error: current.error}) : t('none')}
                </ErrorLine>
            );
        case 'applied':
            return (
                <output className={styles.fixApplied}>
                    <Check/>
                    <span>
                        {t('applied')} <q>{current.text}</q>
                        <Typography variant="caption" className={results.muted}>{t('nextAudit')}</Typography>
                    </span>
                </output>
            );
        default:
            return current.isBulk ?
                <CompactProposal current={current} target={target} fixes={fixes}/> :
                <ProposalBlock current={current} target={target} fixes={fixes}/>;
    }
};

FixerState.propTypes = {
    fixes: PropTypes.shape({runs: PropTypes.object.isRequired, start: PropTypes.func.isRequired, cancel: PropTypes.func.isRequired}).isRequired,
    target: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired
};

/**
 * The summary of Generate all, at the top of the occurrences: its progress with Cancel all, then the proposals with
 * Apply all (confirmed on the same line) and Discard all, then what was applied.
 */
export const FixAllBar = ({fixes}) => {
    const {t} = useGovernanceTranslation('common.results.fixers.all');
    const [isConfirming, setConfirming] = useState(false);
    const bulk = Object.values(fixes.runs).filter(current => current.isBulk);
    if (bulk.length === 0) {
        return null;
    }
    const count = status => bulk.filter(current => current.status === status).length;
    const pending = count('waiting') + count('running');
    const ready = count('ready');
    // Only a fixer with apply writes a proposal: the others are copied one by one
    const writable = bulk.filter(current => current.status === 'ready' && typeof current.fixer.apply === 'function').length;
    const failed = count('error');
    const applied = count('applied');
    const errors = failed > 0 ? <span className={styles.fixErrorText}> · {t('errors', {count: failed})}</span> : null;

    if (pending > 0) {
        return (
            <div className={styles.fixAll} data-fix-all-bar="running" aria-live="polite">
                <Loader size="small"/>
                <Typography variant="caption" className={styles.grow}>{t('running', {done: bulk.length - pending, total: bulk.length})}</Typography>
                <Button size="small" variant="ghost" label={t('cancel')} onClick={fixes.cancelAll}/>
            </div>
        );
    }
    if (ready > 0) {
        return (
            <div className={styles.fixAll} data-fix-all-bar="ready">
                <Typography variant="caption" className={styles.grow}>
                    {isConfirming ? t('confirm', {count: writable}) : <>{t('ready', {count: ready})}{errors}</>}
                </Typography>
                {writable > 0 && (
                    <Button size="small"
                            variant="primary"
                            label={t('apply', {count: writable})}
                            onClick={() => {
                                if (isConfirming) {
                                    setConfirming(false);
                                    fixes.applyAll();
                                } else {
                                    setConfirming(true);
                                }
                            }}/>
                )}
                <Button size="small"
                        variant="ghost"
                        label={isConfirming ? t('back') : t('discard')}
                        onClick={() => (isConfirming ? setConfirming(false) : fixes.discardAll())}/>
            </div>
        );
    }
    return (
        <output className={`${styles.fixAll} ${applied > 0 ? styles.fixAllDone : ''}`} data-fix-all-bar="done">
            {applied > 0 && <Check/>}
            <span className={styles.grow}>
                <Typography variant="caption">
                    {applied > 0 ? <>{t('done', {count: applied})}{errors}</> : <span className={styles.fixErrorText}>{t('errors', {count: failed})}</span>}
                </Typography>
                {applied > 0 && <Typography variant="caption" className={results.muted}>{t('nextAudit')}</Typography>}
            </span>
            <Button size="small" variant="ghost" icon={<Close/>} aria-label={t('close')} onClick={fixes.closeAll}/>
        </output>
    );
};

FixAllBar.propTypes = {
    fixes: PropTypes.shape({
        runs: PropTypes.object.isRequired,
        cancelAll: PropTypes.func.isRequired,
        applyAll: PropTypes.func.isRequired,
        discardAll: PropTypes.func.isRequired,
        closeAll: PropTypes.func.isRequired
    }).isRequired
};
