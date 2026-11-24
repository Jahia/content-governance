import React from 'react';
import PropTypes from 'prop-types';
import {Button, Chip, Visibility} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {FactBlock} from '../../common/results/FactBlock';
import {ResultBanner} from '../../common/results/ResultBanner';

/**
 * The page's headings as a fact block: one row per heading of the main content, in page order,
 * indented by level, with a button that shows it on the page. A level skipped or a heading with no
 * text is a finding of its rule (html.headingLevelSkipped, html.headingEmpty): a result card, not a
 * mark here. An audit stored before the outline existed has none: it says to re-run.
 */
export function HeadingOutline({outline, total, onHighlight}) {
    const {t} = useGovernanceTranslation('audit');

    if (!outline) {
        return <ResultBanner>{t('readability.outline.rerun')}</ResultBanner>;
    }
    if (outline.length === 0) {
        return <ResultBanner>{t('readability.outline.none')}</ResultBanner>;
    }

    // A selector names one element, unless the page repeats an id: the rank keeps the keys apart
    const seen = new Map();
    const rows = outline.map(heading => {
        const rank = seen.get(heading.selector) || 0;
        seen.set(heading.selector, rank + 1);
        return {
            key: `${heading.selector}#${rank}`,
            indent: heading.level - 1,
            label: (
                <>
                    <Chip label={`H${heading.level}`} color={heading.level === 1 ? 'accent' : 'default'}/>
                    {' '}{heading.empty ? t('readability.outline.noText') : heading.text}
                </>
            ),
            action: (
                <Button variant="ghost"
                        size="small"
                        icon={<Visibility/>}
                        aria-label={`${t('a11y.highlight')}: H${heading.level} ${heading.text}`}
                        title={t('a11y.highlight')}
                        onClick={() => onHighlight(heading.selector)}/>
            )
        };
    });

    return (
        <FactBlock title={t('readability.outline.title')}
                   rows={rows}
                   note={total > outline.length ? t('readability.outline.more', {shown: outline.length, count: total}) : null}/>
    );
}

HeadingOutline.propTypes = {
    // [{level, text, selector, skippedFrom?, empty?}], absent on an audit stored before 1.10
    outline: PropTypes.arrayOf(PropTypes.shape({
        level: PropTypes.number.isRequired,
        text: PropTypes.string,
        selector: PropTypes.string,
        skippedFrom: PropTypes.number,
        empty: PropTypes.bool
    })),
    // Headings of the main content, which may be more than the outline lists
    total: PropTypes.number,
    onHighlight: PropTypes.func.isRequired
};
