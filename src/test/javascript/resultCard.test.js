import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {ResultCard} from '../../javascript/common/results/ResultCard.jsx';
import styles from '../../javascript/common/results/Results.module.css';

const h = React.createElement;

/** The outer HTML of the first element whose class list holds `className`, or null. */
const elementOf = (markup, className) => {
    const start = new RegExp(String.raw`<(\w+)[^>]*class="(?:[^"]* )?${className}(?: [^"]*)?"`).exec(markup);
    if (!start) {
        return null;
    }
    const tags = new RegExp(String.raw`<${start[1]}\b|</${start[1]}>`, 'g');
    tags.lastIndex = start.index + 1;
    let depth = 1;
    for (let tag = tags.exec(markup); tag; tag = tags.exec(markup)) {
        depth += tag[0].startsWith('</') ? -1 : 1;
        if (depth === 0) {
            return markup.slice(start.index, tags.lastIndex);
        }
    }
    return null;
};

const TITLE = 'Open Graph tags are missing: og:image';
const DETAIL = 'Give the page an Open Graph title and image.';
const menu = h('button', {type: 'button', 'aria-label': 'More actions'});
const occurrence = (key, text) => ({key, content: h('code', null, text)});
const card = props => renderToStaticMarkup(h(ResultCard, {level: 'moderate', title: TITLE, detail: DETAIL, menu, ...props}));

describe('ResultCard', () => {
    it('shows the level as the colour of the border and as an icon named by its word, never as visible text', () => {
        const markup = card({rule: 'seo.openGraph', occurrences: [occurrence('0', 'og:image')]});
        const icon = elementOf(markup, styles.levelIcon);

        expect(elementOf(markup, styles.card)).toContain(styles.level_moderate);
        expect(icon).toContain('<svg aria-hidden="true">');
        expect(icon).toMatch(/title="[^"]*results\.levels\.moderate"/);
        expect(elementOf(icon, styles.srOnly)).toContain('results.levels.moderate');
        expect(markup.replace(icon, '')).not.toContain('results.levels');
    });

    it('opens the title line with the icon and the chips, ends it with the actions, and leaves the advice below it', () => {
        const line = elementOf(card({rule: 'seo.openGraph', chips: h('span', null, 'WCAG-AA')}), styles.titleLine);

        expect(line.indexOf(styles.levelIcon)).toBeGreaterThan(-1);
        expect(line.indexOf('WCAG-AA')).toBeGreaterThan(line.indexOf(styles.levelIcon));
        expect(line.indexOf(TITLE)).toBeGreaterThan(line.indexOf('WCAG-AA'));
        expect(line.indexOf('More actions')).toBeGreaterThan(line.indexOf(TITLE));
        expect(line).not.toContain(DETAIL);
    });

    it('puts the rule at the right of the line of its one occurrence, below the advice', () => {
        const markup = card({rule: 'seo.openGraph', occurrences: [occurrence('0', 'og:image')]});
        const foot = elementOf(markup, styles.cardFoot);

        expect(elementOf(markup, styles.cardHead)).not.toContain('seo.openGraph');
        expect(elementOf(foot, styles.footRule)).toContain('seo.openGraph');
        expect(elementOf(foot, styles.single)).toContain('og:image');
        expect(markup.indexOf(DETAIL)).toBeLessThan(markup.indexOf('seo.openGraph'));
    });

    it('puts the rule at the right of the line of folded occurrences, outside the list they open', () => {
        const markup = card({rule: 'a11y.imageAlt', occurrences: [occurrence('0', '<img a>'), occurrence('1', '<img b>')]});
        const foot = elementOf(markup, styles.cardFoot);

        expect(foot).toContain(styles.folded);
        expect(elementOf(foot, styles.footRule)).toContain('a11y.imageAlt');
        expect(elementOf(foot, styles.occurrences)).not.toContain('a11y.imageAlt');
    });

    it('keeps the rule of a card with no occurrence at the bottom, and draws no last line with neither', () => {
        expect(elementOf(card({rule: 'geo.llmsTxt'}), styles.cardFoot)).toContain('geo.llmsTxt');
        expect(elementOf(card({}), styles.cardFoot)).toBeNull();
    });
});
