import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {drawerTabs, FINDING_ACTIONS_TARGET, findingActions, isTabId, TAB_TARGET, TAB_TYPE, tabParts} from '../../javascript/drawer/entries.js';
import {registerOwnTabs} from '../../javascript/drawer/ownTabs.js';
import {entryFindings} from '../../javascript/drawer/findings.js';
import {DrawerEntryContext} from '../../javascript/drawer/DrawerEntryContext.js';
import {FindingActions} from '../../javascript/drawer/FindingActions.jsx';
import {GovernanceDrawer} from '../../javascript/drawer/GovernanceDrawer.jsx';

const h = React.createElement;

const seoFinding = {key: 'audit.seo.shortTitle', section: 'audit', tab: 'seo', severity: 'moderate', title: 'Title too short'};
const termFinding = {key: 'compliance.click here', section: 'compliance', tab: null, severity: 'serious', title: 'click here'};

const drawerProps = {
    path: '/sites/digitall/home',
    language: 'en',
    settings: {ai: {}},
    isOn: () => true,
    onClose: () => {},
    onReopen: () => {}
};

// The section bar of the drawer: the id of each tab, in order
const sectionTabs = markup => [...markup.matchAll(/id="content-governance-section-([^"]+)"/g)].map(match => match[1]);

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
});

describe('drawerTabs', () => {
    it('finds no tab when no module registers one', () => {
        expect(drawerTabs()).toEqual([]);
    });

    it('orders the tabs by priority, and leaves out a tab with no component, no label or a key the settings cannot store', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        const component = () => null;
        registry.add(TAB_TYPE, 'second', {targets: [`${TAB_TARGET}:20`], label: 'b', component});
        registry.add(TAB_TYPE, 'first', {targets: [`${TAB_TARGET}:10`], label: 'a', component});
        registry.add(TAB_TYPE, 'noComponent', {targets: [`${TAB_TARGET}:5`], label: 'c'});
        registry.add(TAB_TYPE, 'noLabel', {targets: [`${TAB_TARGET}:6`], component});
        registry.add(TAB_TYPE, 'my module:tab', {targets: [`${TAB_TARGET}:1`], label: 'd', component});
        registry.add(TAB_TYPE, 'elsewhere', {targets: ['somewhereElse:1'], label: 'e', component});

        expect(drawerTabs().map(entry => entry.key)).toEqual(['first', 'second']);
        expect(console.warn).toHaveBeenCalledTimes(1);
    });

    it('lists the tabs of the module first, then those of other modules', () => {
        registerOwnTabs();
        registry.add(TAB_TYPE, 'aiAssistantReview', {targets: [`${TAB_TARGET}:10`], label: 'ai-assistant:review.tab', component: () => null});

        expect(drawerTabs().map(entry => entry.key)).toEqual(['audit', 'geo', 'compliance', 'aiAssistantReview']);
        const [audit, geo, compliance] = drawerTabs();
        expect(tabParts(audit).map(part => part.id)).toEqual(['accessibility', 'seo', 'vitals', 'readability', 'ecodesign', 'links', 'jahia', 'ai']);
        expect(tabParts(audit).find(part => part.id === 'ai').needs).toBe('ai');
        expect(tabParts(geo).map(part => part.id)).toEqual(['score', 'crawler', 'files', 'schema', 'citations']);
        expect(tabParts(compliance)).toEqual([]);
        expect(compliance.requirePage && compliance.requireWrite).toBe(true);
    });
});

describe('tabParts', () => {
    it('keeps the parts with a label and an id the settings can store', () => {
        const entry = {tabs: [{id: 'one', label: 'a'}, {id: 'two.three', label: 'b'}, {id: 'four'}, null, {id: 'five', label: 'c'}]};
        expect(tabParts(entry).map(part => part.id)).toEqual(['one', 'five']);
        expect(tabParts({})).toEqual([]);
    });

    it('accepts the ids that the server stores', () => {
        expect(isTabId('aiAssistantReview')).toBe(true);
        expect(isTabId('my-module_tab2')).toBe(true);
        expect(isTabId('a'.repeat(65))).toBe(false);
        expect(isTabId('2tab')).toBe(false);
        expect(isTabId(undefined)).toBe(false);
    });
});

describe('findingActions', () => {
    it('lists the actions of the finding target in the order of their priority', () => {
        expect(findingActions()).toEqual([]);
        registry.add('action', 'second', {targets: [`${FINDING_ACTIONS_TARGET}:20`], buttonLabel: 'b'});
        registry.add('action', 'first', {targets: [`${FINDING_ACTIONS_TARGET}:10`], buttonLabel: 'a'});
        registry.add('action', 'elsewhere', {targets: ['headerPrimaryActions:10'], buttonLabel: 'c'});
        expect(findingActions().map(action => action.key)).toEqual(['first', 'second']);
    });
});

describe('entryFindings', () => {
    it('lists the findings of the given sections in their order, each with the id of its rule', () => {
        const findings = entryFindings({compliance: [termFinding], geo: [{key: 'geo.crawler'}], audit: [seoFinding]}, ['audit', 'compliance']);
        expect(findings).toEqual([
            {...seoFinding, rule: 'audit.seo.shortTitle'},
            {...termFinding, rule: 'compliance.click here'}
        ]);
    });

    it('has nothing for a section that reported nothing', () => {
        expect(entryFindings({}, ['audit'])).toEqual([]);
    });
});

describe('FindingActions', () => {
    const findings = entryFindings({audit: [seoFinding], compliance: [termFinding]}, ['audit', 'compliance']);
    // The drawer reads the actions when it opens: here, at each render
    const render = findingKey => renderToStaticMarkup(
        h(DrawerEntryContext.Provider, {value: {path: '/sites/digitall/home', language: 'fr', findings, actions: findingActions()}},
            h(FindingActions, {findingKey}))
    );

    it('renders nothing when no module adds an action', () => {
        expect(render('audit.seo.shortTitle')).toBe('');
    });

    it('gives an action the page, the language, the finding and every finding', () => {
        const received = [];
        registry.add('action', 'fix', {
            targets: [`${FINDING_ACTIONS_TARGET}:10`],
            buttonLabel: 'my-module:fix',
            component: props => {
                received.push(props);
                return h('button', {type: 'button'}, `fix ${props.finding.rule}`);
            }
        });

        expect(render('audit.seo.shortTitle')).toContain('fix audit.seo.shortTitle');
        const [props] = received;
        expect(props.path).toBe('/sites/digitall/home');
        expect(props.language).toBe('fr');
        expect(props.finding).toEqual({...seoFinding, rule: 'audit.seo.shortTitle'});
        expect(props.findings.map(finding => finding.rule)).toEqual(['audit.seo.shortTitle', 'compliance.click here']);
    });

    it('draws an action with no component as a button, unless it says it is not visible', () => {
        registry.add('action', 'fix', {targets: [`${FINDING_ACTIONS_TARGET}:10`], buttonLabel: 'Fix with the assistant', onClick: () => {}});
        registry.add('action', 'hidden', {targets: [`${FINDING_ACTIONS_TARGET}:20`], buttonLabel: 'Hidden', isVisible: false});

        const markup = render('compliance.click here');
        expect(markup).toContain('Fix with the assistant');
        expect(markup).toContain('data-registry-key="action:fix"');
        expect(markup).not.toContain('Hidden');
    });

    it('renders nothing for a finding the drawer does not hold', () => {
        registry.add('action', 'fix', {targets: [`${FINDING_ACTIONS_TARGET}:10`], buttonLabel: 'fix', onClick: () => {}});
        expect(render('audit.seo.unknown')).toBe('');
    });
});

describe('GovernanceDrawer', () => {
    const tab = (id, component = () => null) => ({id, entry: {key: id, label: id, component}});

    it('shows the tabs it is given, in their order', () => {
        const markup = renderToStaticMarkup(h(GovernanceDrawer, {...drawerProps, tabs: [tab('geo'), tab('compliance')], initialSection: 'compliance'}));
        expect(sectionTabs(markup)).toEqual(['geo', 'compliance']);
    });

    it('gives every tab the page, the language, its site switch and its history', () => {
        const received = [];
        const isOn = vi.fn(() => true);
        const review = tab('aiReview', props => {
            received.push(props);
            return h('p', null, 'the AI review');
        });

        const markup = renderToStaticMarkup(h(GovernanceDrawer, {...drawerProps, isOn, tabs: [tab('geo'), review], initialSection: 'aiReview'}));

        expect(sectionTabs(markup)).toEqual(['geo', 'aiReview']);
        expect(markup).toContain('the AI review');
        const [props] = received;
        expect(props.path).toBe('/sites/digitall/home');
        expect(props.language).toBe('en');
        expect(props.findings).toEqual([]);
        expect(props.settings).toBe(drawerProps.settings);
        expect(props.requestedTab).toBeNull();
        ['onCount', 'onFindings', 'saveFindings', 'fetchHistory', 'onClose', 'onReopen'].forEach(name => expect(typeof props[name]).toBe('function'));
        props.isOn('summary');
        expect(isOn).toHaveBeenCalledWith('aiReview', 'summary');
    });

    it('greys out a tab the page cannot use', () => {
        const markup = renderToStaticMarkup(h(GovernanceDrawer, {...drawerProps, tabs: [tab('geo'), {...tab('compliance'), unavailable: 'notPage'}]}));
        expect(markup).toContain('disabled="" id="content-governance-section-compliance"');
    });
});
