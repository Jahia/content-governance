import {describe, expect, it} from 'vitest';
import {levelOf} from '../../javascript/common/results/levels';
import {geoStateOf, pageFindingsOf} from '../../javascript/geo/geoState';

describe('levelOf', () => {
    it('folds the four severities of a rule into three levels', () => {
        expect(['critical', 'serious', 'moderate', 'minor'].map(levelOf)).toEqual(['serious', 'serious', 'moderate', 'minor']);
    });

    it('maps the GEO scale onto the same levels', () => {
        expect(['critical', 'important', 'advisory'].map(levelOf)).toEqual(['serious', 'moderate', 'minor']);
    });

    it('treats an unknown severity as minor', () => {
        expect(levelOf(undefined)).toBe('minor');
        expect(levelOf('whatever')).toBe('minor');
    });
});

describe('pageFindingsOf', () => {
    const report = {
        published: true,
        visibility: {guestReadable: false, noindex: true},
        sitemap: {missing: true, checkedAt: '2026-11-24T10:00:00Z'},
        links: {content: 0, nav: 0},
        languages: {missing: ['de'], notPublished: ['fr']}
    };

    it('places each fact of the page that is a finding in its subdomain', () => {
        expect(pageFindingsOf(report)).toEqual([
            {id: 'gated', place: 'geo.crawlers', level: 'serious'},
            {id: 'noindex', place: 'geo.crawlers', level: 'minor'},
            {id: 'notListed', place: 'geo.files', level: 'moderate'},
            {id: 'orphan', place: 'geo.content', level: 'moderate'},
            {id: 'languagesMissing', place: 'geo.content', level: 'minor'},
            {id: 'languagesUnpublished', place: 'geo.content', level: 'moderate'}
        ]);
    });

    it('finds nothing without a report', () => {
        expect(pageFindingsOf(null)).toEqual([]);
    });

    it('counts them in the section of their subdomain', () => {
        const geo = {report};
        expect(geoStateOf(geo, 'geo.crawlers', new Set())).toEqual({count: 2, serious: true, unavailable: false});
        expect(geoStateOf(geo, 'geo.content', new Set())).toEqual({count: 3, serious: false, unavailable: false});
    });
});
