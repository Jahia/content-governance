import {describe, expect, it} from 'vitest';
import {DOMAIN, RULE, SUBDOMAIN, draftOf, drop, move, moveOptions, sameLayout, shift, toLayout, toggle} from '../../javascript/layout/layoutModel';

const DOMAINS = [
    {id: 'quality', switchedOn: true, subdomains: [{id: 'health', switchedOn: true}, {id: 'links', switchedOn: false}]},
    {id: 'geo', switchedOn: false, subdomains: [{id: 'seo', switchedOn: true}]}
];
const RULES = [
    {id: 'seo.title', subdomain: 'seo', order: 2, switchedOn: true},
    {id: 'seo.canonical', subdomain: 'seo', order: 1, switchedOn: false},
    {id: 'links.broken', subdomain: 'links', order: 0, switchedOn: true},
    {id: 'lost.rule', subdomain: 'nowhere', order: 0, switchedOn: true}
];

const draft = () => draftOf(DOMAINS, RULES, ['chat']);

describe('the draft of the layout', () => {
    it('holds the domains, the subdomains and the rules in their order, and what is switched off', () => {
        expect(toLayout(draft())).toEqual({
            domains: [
                {id: 'quality', subdomains: [{id: 'health', rules: []}, {id: 'links', rules: ['links.broken']}]},
                {id: 'geo', subdomains: [{id: 'seo', rules: ['seo.canonical', 'seo.title']}]}
            ],
            off: ['chat', 'geo', 'links', 'seo.canonical']
        });
    });

    it('switches an item on or off', () => {
        const next = toggle(toggle(draft(), 'geo', true), 'health', false);
        expect(toLayout(next).off).toEqual(['chat', 'health', 'links', 'seo.canonical']);
        expect(sameLayout(next, draft())).toBe(false);
        expect(sameLayout(toggle(next, 'geo', false), toggle(draft(), 'health', false))).toBe(true);
    });

    it('moves a domain, a subdomain into another domain, and a rule into another subdomain', () => {
        expect(toLayout(move(draft(), DOMAIN, 'geo', {index: 0})).domains.map(d => d.id)).toEqual(['geo', 'quality']);
        const seoInQuality = move(draft(), SUBDOMAIN, 'seo', {parent: 'quality', index: 1});
        expect(toLayout(seoInQuality).domains).toEqual([
            {id: 'quality', subdomains: [{id: 'health', rules: []}, {id: 'seo', rules: ['seo.canonical', 'seo.title']}, {id: 'links', rules: ['links.broken']}]},
            {id: 'geo', subdomains: []}
        ]);
        const canonicalInLinks = move(draft(), RULE, 'seo.canonical', {parent: 'links', index: 99});
        expect(toLayout(canonicalInLinks).domains[0].subdomains[1].rules).toEqual(['links.broken', 'seo.canonical']);
        expect(toLayout(canonicalInLinks).domains[1].subdomains[0].rules).toEqual(['seo.title']);
    });

    it('leaves the draft as it is for an item or a parent that it does not hold', () => {
        const start = draft();
        expect(move(start, RULE, 'unknown', {parent: 'seo', index: 0})).toBe(start);
        expect(move(start, RULE, 'seo.title', {parent: 'unknown', index: 0})).toBe(start);
        expect(toLayout(start).domains[1].subdomains[0].rules).toEqual(['seo.canonical', 'seo.title']);
    });

    it('drops an item before another of its level, or last into a parent', () => {
        expect(toLayout(drop(draft(), {level: RULE, id: 'seo.canonical'}, {level: RULE, id: 'links.broken'})).domains[0].subdomains[1].rules)
            .toEqual(['seo.canonical', 'links.broken']);
        expect(toLayout(drop(draft(), {level: RULE, id: 'seo.canonical'}, {level: RULE, id: 'seo.title'})).domains[1].subdomains[0].rules)
            .toEqual(['seo.canonical', 'seo.title']);
        expect(toLayout(drop(draft(), {level: SUBDOMAIN, id: 'health'}, {level: SUBDOMAIN, id: 'seo'})).domains[1].subdomains.map(s => s.id))
            .toEqual(['health', 'seo']);
        expect(toLayout(drop(draft(), {level: SUBDOMAIN, id: 'health'}, {level: DOMAIN, id: 'geo'})).domains[1].subdomains.map(s => s.id))
            .toEqual(['seo', 'health']);
        expect(toLayout(drop(draft(), {level: DOMAIN, id: 'quality'}, {level: DOMAIN, id: 'geo'})).domains.map(d => d.id))
            .toEqual(['quality', 'geo']);
        const start = draft();
        expect(drop(start, {level: DOMAIN, id: 'quality'}, {level: RULE, id: 'seo.title'})).toBe(start);
    });

    it('offers the moves of the keyboard: up, down and the other parents', () => {
        expect(moveOptions(draft(), DOMAIN, 'quality')).toEqual({up: false, down: true, parents: []});
        expect(moveOptions(draft(), SUBDOMAIN, 'links')).toEqual({up: true, down: false, parents: ['geo']});
        expect(moveOptions(draft(), RULE, 'seo.title')).toEqual({up: true, down: false, parents: ['health', 'links']});
        expect(toLayout(shift(draft(), RULE, 'seo.title', -1)).domains[1].subdomains[0].rules).toEqual(['seo.title', 'seo.canonical']);
        expect(toLayout(shift(draft(), DOMAIN, 'quality', 1)).domains.map(d => d.id)).toEqual(['geo', 'quality']);
    });
});
