import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {ResultBanner} from '../../javascript/common/results/ResultBanner.jsx';
import styles from '../../javascript/common/results/Results.module.css';

const h = React.createElement;

describe('ResultBanner', () => {
    it('shows a sentence as its text, never in bold, when it has no title', () => {
        const markup = renderToStaticMarkup(h(ResultBanner, {variant: 'info'}, 'Automated checks cover part of WCAG.'));

        expect(markup).not.toContain('<strong');
        expect(markup).toContain(styles.bannerText);
    });

    it('shows a title of a few words in bold, above its text', () => {
        const markup = renderToStaticMarkup(h(ResultBanner, {variant: 'warning', title: 'This check failed'}, 'It stopped on an error.'));

        expect(markup).toContain('<strong>This check failed</strong>It stopped on an error.');
        expect(markup).not.toContain(styles.bannerText);
    });
});
