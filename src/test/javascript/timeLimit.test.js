import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {runStep} from '../../javascript/audit/analyzers/timeLimit.js';

const after = (ms, value) => new Promise(resolve => {
    setTimeout(() => resolve(value), ms);
});
const never = () => new Promise(() => {});

describe('runStep', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('returns the value of a step that ends in time', async () => {
        const outcome = runStep(() => after(1000, 42), 5000);
        await vi.advanceTimersByTimeAsync(1000);
        await expect(outcome).resolves.toEqual({value: 42});
    });

    it('accepts a step that is not asynchronous', async () => {
        await expect(runStep(() => 'done', 5000)).resolves.toEqual({value: 'done'});
    });

    it('gives up on a step that never ends, at the limit', async () => {
        const outcome = runStep(never, 15_000);
        await vi.advanceTimersByTimeAsync(14_999);
        let settled = false;
        outcome.then(() => {
            settled = true;
        });
        await Promise.resolve();
        expect(settled).toBe(false);
        await vi.advanceTimersByTimeAsync(1);
        await expect(outcome).resolves.toEqual({incomplete: 'timedOut', seconds: 15});
    });

    it('reports a step that fails, without rejecting', async () => {
        await expect(runStep(() => Promise.reject(new Error('axe-core did not load')), 5000))
            .resolves.toEqual({incomplete: 'failed', message: 'axe-core did not load'});
        await expect(runStep(() => {
            throw new Error('boom');
        }, 5000)).resolves.toEqual({incomplete: 'failed', message: 'boom'});
    });

    it('clears its timer once the step ended', async () => {
        await runStep(() => 1, 5000);
        expect(vi.getTimerCount()).toBe(0);
    });
});
