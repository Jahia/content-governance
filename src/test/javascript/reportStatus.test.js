import {describe, expect, it} from 'vitest';
import {panelStateAfter, tabOfferedAfter} from '../../javascript/geo/util/reportStatus.js';

describe('the tab Report after a failed status call', () => {
    it('says that it could not reach the server, never that no model is available', () => {
        for (const code of [503, 502, 'refused', undefined]) {
            expect(panelStateAfter({code})).toEqual({status: null, phase: 'unreachable'});
            expect(tabOfferedAfter({code})).toBe(true);
        }
        expect(panelStateAfter(new TypeError('Failed to fetch'))).toEqual({status: null, phase: 'unreachable'});
    });

    it('shows no access, and no tab, when the server answers 403', () => {
        expect(panelStateAfter({code: 403})).toEqual({status: {enabled: false, reason: 'noAccess'}, phase: 'idle'});
        expect(tabOfferedAfter({code: 403})).toBe(false);
    });
});
