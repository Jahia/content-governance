import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {addedTabs, FINDING_ACTIONS_TARGET, findingActions, TAB_TARGET, TAB_TYPE} from '../../javascript/drawer/entries.js';
import {entryFindings} from '../../javascript/drawer/findings.js';
import {DrawerEntryContext} from '../../javascript/drawer/DrawerEntryContext.js';
import {FindingActions} from '../../javascript/drawer/FindingActions.jsx';
import {GovernanceDrawer} from '../../javascript/drawer/GovernanceDrawer.jsx';

// The drawer's own sections need jContent's store and Apollo: the drawer tests check its section bar only
vi.mock('../../javascript/audit/AuditSection.jsx', () => ({AuditSection: () => null}));
vi.mock('../../javascript/geo/GeoSection.jsx', () => ({GeoSection: () => null}));
vi.mock('../../javascript/compliance/ComplianceSection.jsx', () => ({ComplianceSection: () => null}));

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

describe('addedTabs', () => {
    it('finds no tab when no module adds one', () => {
        expect(addedTabs(['audit'])).toEqual([]);
    });

    it('orders the tabs by priority, and leaves out a tab with no component, no label or the id of a section', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        const component = () => null;
        registry.add(TAB_TYPE, 'second', {targets: [`${TAB_TARGET}:20`], label: 'b', component});
        registry.add(TAB_TYPE, 'first', {targets: [`${TAB_TARGET}:10`], label: 'a', component});
        registry.add(TAB_TYPE, 'noComponent', {targets: [`${TAB_TARGET}:5`], label: 'c'});
        registry.add(TAB_TYPE, 'noLabel', {targets: [`${TAB_TARGET}:6`], component});
        registry.add(TAB_TYPE, 'audit', {targets: [`${TAB_TARGET}:1`], label: 'd', component});
        registry.add(TAB_TYPE, 'elsewhere', {targets: ['somewhereElse:1'], label: 'e', component});

        expect(addedTabs(['audit', 'geo', 'compliance']).map(entry => entry.key)).toEqual(['first', 'second']);
        expect(console.warn).toHaveBeenCalledTimes(1);
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
    it('shows its own sections only when no module adds a tab', () => {
        const markup = renderToStaticMarkup(h(GovernanceDrawer, {...drawerProps, sections: [{id: 'geo'}, {id: 'compliance'}], initialSection: 'compliance'}));
        expect(sectionTabs(markup)).toEqual(['geo', 'compliance']);
    });

    it('adds the tab of another module after its own sections, and gives it the page and the language', () => {
        const received = [];
        registry.add(TAB_TYPE, 'aiReview', {
            targets: [`${TAB_TARGET}:10`],
            label: 'AI review',
            component: props => {
                received.push(props);
                return h('p', null, 'the AI review');
            }
        });

        const markup = renderToStaticMarkup(h(GovernanceDrawer, {...drawerProps, sections: [{id: 'geo'}, {id: 'compliance'}], initialSection: 'aiReview'}));

        expect(sectionTabs(markup)).toEqual(['geo', 'compliance', 'aiReview']);
        expect(markup).toContain('AI review');
        expect(markup).toContain('the AI review');
        const [props] = received;
        expect(props.path).toBe('/sites/digitall/home');
        expect(props.language).toBe('en');
        expect(props.findings).toEqual([]);
        expect(typeof props.onCount).toBe('function');
    });
});
