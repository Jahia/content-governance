import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {governanceDomains, rulePlaces} from '../../javascript/drawer/layout.js';
import {heldTabs, usableHeldIn, withHeldSections, withoutHeld} from '../../javascript/drawer/heldTabs.js';
import {GovernanceSections} from '../../javascript/drawer/GovernancePanel.jsx';

vi.mock('@jahia/data-helper', () => ({useNodeChecks: () => ({checksResult: true, loading: false})}));
// Every section open, so that a static render shows what a section holds
vi.mock('../../javascript/common/openSections.js', () => ({
    useOpenSections: () => ({isOpen: () => true, setOpen: () => {}}),
    withSections: open => open
}));

const h = React.createElement;

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
});

/** Compliance with the subdomain that ai-assistant ships for its Voice tab, after Writing. */
const domains = governanceDomains([
    {id: 'quality', label: 'Page quality', panel: 'governance', subdomains: [{id: 'links', label: 'Links'}]},
    {id: 'compliance', label: 'Compliance', panel: 'governance', subdomains: [
        {id: 'writing', label: 'Writing'}, {id: 'aiAssistantVoice', label: 'Voice', help: 'The voice of the brand'}]}
]);
const places = rulePlaces([{id: 'links.broken', subdomain: 'links', evaluator: 'links.broken', enabled: true}]);
const entry = (id, component = () => h('p', null, `the tab ${id}`)) => ({id, entry: {key: id, label: id, component}});

describe('the tabs that a subdomain holds', () => {
    it('finds the tabs whose key is the id of a subdomain', () => {
        const held = heldTabs(domains, [entry('aiAssistantVoice'), entry('aiReview')]);
        expect([...held.keys()]).toEqual(['aiAssistantVoice']);
    });

    it('gives the checks of the module the domain without its held subdomains', () => {
        const held = heldTabs(domains, [entry('aiAssistantVoice')]);
        expect(withoutHeld(domains[1], held).subdomains.map(s => s.id)).toEqual(['writing']);
        expect(withoutHeld(domains[0], held)).toBe(domains[0]);
    });

    it('reports the findings of a held tab that the page can use only', () => {
        const held = heldTabs(domains, [{...entry('aiAssistantVoice'), unavailable: 'notPage'}]);
        expect(usableHeldIn(domains[1], held)).toEqual([]);
        expect(usableHeldIn(domains[1], heldTabs(domains, [entry('aiAssistantVoice')]))).toEqual(['aiAssistantVoice']);
    });

    it('puts the section of a held tab in the order of the layout, with the count and the label of the subdomain', () => {
        const held = heldTabs(domains, [entry('aiAssistantVoice')]);
        const own = [{id: 'writing', label: 'Writing', count: 2, serious: false, unavailable: false}];
        const sections = withHeldSections(domains[1], own, held, {aiAssistantVoice: 3}, id => id === 'aiAssistantVoice');
        expect(sections.map(s => [s.id, s.label, s.count, s.serious])).toEqual([
            ['writing', 'Writing', 2, false],
            ['aiAssistantVoice', 'Voice', 3, true]
        ]);
        expect(sections[1].help).toBe('The voice of the brand');
        expect(sections[1].entry).toBe(held.get('aiAssistantVoice'));
    });

    it('gives a held tab no count before it reports one, and none when the page cannot use it', () => {
        const own = [{id: 'writing', label: 'Writing', count: null, serious: false, unavailable: false}];
        const free = withHeldSections(domains[1], own, heldTabs(domains, [entry('aiAssistantVoice')]), {}, () => false);
        expect(free[1].count).toBeNull();
        const blocked = heldTabs(domains, [{...entry('aiAssistantVoice'), unavailable: 'noWrite'}]);
        expect(withHeldSections(domains[1], own, blocked, {aiAssistantVoice: 4}, () => true)[1])
            .toMatchObject({id: 'aiAssistantVoice', count: null, serious: false});
    });
});

describe('GovernanceSections with a held tab', () => {
    const drawerProps = {path: '/sites/digitall/home', language: 'en', settings: {}, isOn: () => true, onClose: () => {}};
    const sectionTabs = markup => [...markup.matchAll(/id="content-governance-section-([^"]+)"/g)].map(match => match[1]);

    it('leaves the held tab out of the bar, and keeps the tabs that no subdomain holds', () => {
        const markup = renderToStaticMarkup(h(GovernanceSections, {...drawerProps, domains, places, tabs: [entry('aiAssistantVoice'), entry('aiReview')]}));
        expect(sectionTabs(markup)).toEqual(['quality', 'compliance', 'aiReview']);
    });

    it('shows the held tab in the section of its subdomain, with its site switch and no request', () => {
        const received = [];
        const isOn = vi.fn(() => true);
        const voice = entry('aiAssistantVoice', props => {
            received.push(props);
            return h('p', null, 'the voice');
        });

        const markup = renderToStaticMarkup(h(GovernanceSections, {...drawerProps, isOn, domains, places, tabs: [voice], request: {section: 'aiAssistantVoice'}}));

        expect(markup).toContain('id="content-governance-section-compliance" role="tab" aria-selected="true"');
        expect(markup).toContain('id="content-governance-subdomain-aiAssistantVoice"');
        expect(markup).toContain('the voice');
        const [props] = received;
        expect(props.path).toBe('/sites/digitall/home');
        expect(props.requestedTab).toBeNull();
        props.isOn('summary');
        expect(isOn).toHaveBeenCalledWith('aiAssistantVoice', 'summary');
    });

    it('says why the page cannot use a held tab, in its section', () => {
        const voice = {...entry('aiAssistantVoice'), unavailable: 'notPage'};
        const markup = renderToStaticMarkup(h(GovernanceSections, {...drawerProps, domains, places, tabs: [voice], request: {section: 'compliance'}}));
        expect(markup).toContain('unavailable.notPage');
        expect(markup).not.toContain('the tab aiAssistantVoice');
    });
});
