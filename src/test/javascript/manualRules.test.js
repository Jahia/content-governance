import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {ManualRules} from '../../javascript/audit/ManualRules.jsx';

const h = React.createElement;

const manualOf = state => ({of: () => state, run: () => undefined});
const render = state => renderToStaticMarkup(h(ManualRules, {manual: manualOf(state), id: 'tone', rules: null, language: 'fr'}));

describe('ManualRules', () => {
    it('shows Run as an outlined button of the default size, as the other run buttons of the drawer', () => {
        const markup = render({status: 'idle', run: null});

        expect(markup).toContain('data-variant="outlined"');
        expect(markup).not.toContain('data-size');
        expect(markup).not.toContain('<svg');
    });

    it('shows Run again with the icon of Re-run', () => {
        const markup = render({status: 'idle', run: {at: Date.UTC(2026, 10, 25, 13, 2, 52), by: 'root', results: []}});

        expect(markup).toContain('data-variant="outlined"');
        expect(markup).not.toContain('data-size');
        expect(markup).toContain('<svg');
    });
});
