/**
 * Runs one audit step with a time limit, so a step that never ends (a request that never answers,
 * a script that never loads) costs its own tab only: the audit goes on and shows the other tabs.
 * The step itself is not cancelled; its late answer is ignored.
 *
 * @param {Function} start returns the step's result or a promise of it
 * @param {number} limitMs the time the step may take, in milliseconds
 * @returns {Promise<{value: *}|{incomplete: 'timedOut', seconds: number}|{incomplete: 'failed', message: string}>}
 *     never rejects
 */
export function runStep(start, limitMs) {
    let timer;
    const limit = new Promise(resolve => {
        timer = setTimeout(() => resolve({incomplete: 'timedOut', seconds: Math.round(limitMs / 1000)}), limitMs);
    });
    const step = Promise.resolve()
        .then(start)
        .then(value => ({value}), error => ({incomplete: 'failed', message: (error && error.message) || String(error)}));
    return Promise.race([step, limit]).finally(() => clearTimeout(timer));
}
