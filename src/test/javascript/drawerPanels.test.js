import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {registry} from '@jahia/ui-extender';
import {drawerPanels, drawerTabs, PANEL_TYPE, TAB_TARGET, TAB_TYPE} from '../../javascript/drawer/entries.js';
import {registerOwnPanel, registerOwnTabs} from '../../javascript/drawer/ownTabs.js';
import {API_TYPE, DRAWER_API_KEY, onPanelRequest, openPanel, registerDrawerApi} from '../../javascript/drawer/drawerApi.js';
import {openWith, pressButton, showPanel} from '../../javascript/drawer/drawerState.js';
import {siteSwitches} from '../../javascript/common/useSiteSettings.js';
import {GovernanceDrawer} from '../../javascript/drawer/GovernanceDrawer.jsx';

const h = React.createElement;

const component = () => null;
const panel = (key, extra = {}) => ({key, label: `${key}.label`, buttonLabel: `${key}.button`, component, ...extra});

afterEach(() => {
    registry.clear();
    vi.restoreAllMocks();
});

describe('drawerPanels', () => {
    it('finds no panel when no module registers one', () => {
        expect(drawerPanels()).toEqual([]);
    });

    it('orders the panels by their order, then as they were added, and leaves out a panel it cannot show or store', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        registry.add(PANEL_TYPE, 'chat', {label: 'a', buttonLabel: 'a', order: 20, component});
        registry.add(PANEL_TYPE, 'review', {label: 'b', buttonLabel: 'b', order: 10, component});
        registry.add(PANEL_TYPE, 'noOrder', {label: 'c', buttonLabel: 'c', component});
        registry.add(PANEL_TYPE, 'sameOrder', {label: 'd', buttonLabel: 'd', order: 10, component});
        registry.add(PANEL_TYPE, 'noComponent', {label: 'e', buttonLabel: 'e'});
        registry.add(PANEL_TYPE, 'noLabel', {buttonLabel: 'f', component});
        registry.add(PANEL_TYPE, 'noButton', {label: 'g', component});
        registry.add(PANEL_TYPE, 'my panel', {label: 'h', buttonLabel: 'h', component});

        expect(drawerPanels().map(entry => entry.key)).toEqual(['noOrder', 'review', 'sameOrder', 'chat']);
        expect(console.warn).toHaveBeenCalledTimes(1);
    });

    it('lists the governance panel of the module first, with its button', () => {
        const icon = h('svg');
        registry.add(PANEL_TYPE, 'chat', {label: 'ai-assistant:chat.label', buttonLabel: 'ai-assistant:chat.open', order: 10, component});
        registerOwnPanel(icon);

        const [governance, chat] = drawerPanels();
        expect([governance.key, chat.key]).toEqual(['governance', 'chat']);
        expect(governance.label).toBe('content-governance:drawer.title');
        expect(governance.buttonLabel).toBe('content-governance:drawer.action');
        expect(governance.buttonIcon).toBe(icon);
    });

    it('makes the governance panel available while the site keeps one of its tabs on', () => {
        registerOwnPanel(null);
        registerOwnTabs();
        const [governance] = drawerPanels();

        expect(governance.useAvailable({isOn: () => true})).toBe(true);
        expect(governance.useAvailable({isOn: tab => tab === 'compliance'})).toBe(true);
        expect(governance.useAvailable({isOn: () => false})).toBe(false);
    });

    it('leaves out a tab whose key is the key of a panel: the site settings store both in one list', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        registry.add(PANEL_TYPE, 'chat', {label: 'a', buttonLabel: 'a', component});
        registry.add(TAB_TYPE, 'chat', {targets: [`${TAB_TARGET}:10`], label: 'b', component});
        registry.add(TAB_TYPE, 'review', {targets: [`${TAB_TARGET}:20`], label: 'c', component});

        expect(drawerTabs().map(entry => entry.key)).toEqual(['review']);
        expect(console.warn).toHaveBeenCalledTimes(1);
    });
});

describe('drawerState', () => {
    it('opens the drawer on the panel of a button, switches to another, and closes on a second press', () => {
        const open = pressButton(null, 'governance');
        expect(open).toEqual({panel: 'governance', contexts: {}});
        const chat = pressButton(open, 'chat');
        expect(chat).toEqual({panel: 'chat', contexts: {}});
        expect(pressButton(chat, 'chat')).toBeNull();
    });

    it('keeps the context of each panel when the switch shows another', () => {
        const finding = {rule: 'seo.title'};
        const chat = openWith(null, 'chat', {finding});
        expect(chat).toEqual({panel: 'chat', contexts: {chat: {finding}}});
        const governance = showPanel(chat, 'governance');
        expect(governance.panel).toBe('governance');
        expect(showPanel(governance, 'chat').contexts.chat).toEqual({finding});
        expect(pressButton(governance, 'chat').contexts.chat).toEqual({finding});
    });

    it('gives a panel a new context object on each request, and keeps its context on a request with none', () => {
        const context = {finding: {rule: 'seo.title'}};
        const first = openWith(null, 'chat', context);
        const second = openWith(first, 'chat', context);
        expect(second.contexts.chat).toEqual(context);
        expect(second.contexts.chat).not.toBe(first.contexts.chat);
        expect(second.contexts.chat).not.toBe(context);
        expect(openWith(second, 'chat').contexts.chat).toBe(second.contexts.chat);
        expect(openWith(second, 'governance', {section: 'geo'}).contexts).toEqual({chat: context, governance: {section: 'geo'}});
    });
});

describe('openPanel', () => {
    it('opens nothing while no drawer listens', () => {
        expect(openPanel('chat', {finding: {}})).toBe(false);
    });

    it('asks every drawer in turn, and says whether one opened the panel', () => {
        const refusing = vi.fn(() => false);
        const opening = vi.fn(() => true);
        const stops = [onPanelRequest(refusing), onPanelRequest(opening)];

        expect(openPanel('chat', {finding: {rule: 'seo.title'}}, '/sites/digitall/home')).toBe(true);
        expect(refusing).toHaveBeenCalledWith({panel: 'chat', context: {finding: {rule: 'seo.title'}}, path: '/sites/digitall/home'});
        expect(opening).toHaveBeenCalledWith({panel: 'chat', context: {finding: {rule: 'seo.title'}}, path: '/sites/digitall/home'});

        stops.forEach(stop => stop());
        expect(openPanel('chat')).toBe(false);
    });

    it('gives other modules openPanel(id, context) through the registry, for the drawer of any page', () => {
        const requests = [];
        const stop = onPanelRequest(request => requests.push(request) > 0);
        registerDrawerApi();

        const context = {finding: {rule: 'seo.title'}, path: '/sites/digitall/home/news', language: 'fr'};
        expect(registry.get(API_TYPE, DRAWER_API_KEY).openPanel('chat', context)).toBe(true);
        stop();
        expect(requests).toEqual([{panel: 'chat', context, path: null}]);
    });
});

describe('siteSwitches', () => {
    it('keeps every panel and tab on until the site switches it off', () => {
        const {isOn, isPanelOn} = siteSwitches({moduleEnabled: true, disabled: []});
        expect(isPanelOn('governance') && isPanelOn('chat') && isOn('geo') && isOn('geo', 'citations')).toBe(true);
    });

    it('switches every tab off with the governance panel, and a panel of another module alone', () => {
        const governanceOff = siteSwitches({moduleEnabled: true, disabled: ['governance']});
        expect(governanceOff.isOn('geo')).toBe(false);
        expect(governanceOff.isOn('geo', 'citations')).toBe(false);
        expect(governanceOff.isPanelOn('chat')).toBe(true);

        const chatOff = siteSwitches({moduleEnabled: true, disabled: ['chat', 'geo.citations']});
        expect(chatOff.isPanelOn('chat')).toBe(false);
        expect(chatOff.isOn('geo')).toBe(true);
        expect(chatOff.isOn('geo', 'citations')).toBe(false);
    });

    it('keeps everything off where the site does not enable the module, or while the settings load', () => {
        expect(siteSwitches({moduleEnabled: false, disabled: []}).isPanelOn('chat')).toBe(false);
        expect(siteSwitches(null).isOn('geo')).toBe(false);
    });
});

describe('GovernanceDrawer', () => {
    const drawerProps = {
        path: '/sites/digitall/home',
        language: 'en',
        contexts: {},
        settings: {disabled: []},
        isOn: () => true,
        onShow: () => {},
        onClose: () => {}
    };
    const switchTabs = markup => [...markup.matchAll(/id="content-governance-drawer-switch-([^"]+)"/g)].map(match => match[1]);
    const shownPanels = markup => [...markup.matchAll(/id="content-governance-drawer-panel-([^"]+)"/g)].map(match => match[1]);

    it('shows the panel in view under its label, with no switch for a single panel', () => {
        const markup = renderToStaticMarkup(h(GovernanceDrawer, {
            ...drawerProps,
            panels: [panel('governance', {component: () => h('p', null, 'the governance panel')})],
            active: 'governance'
        }));
        expect(markup).toContain('governance.label');
        expect(markup).toContain('the governance panel');
        expect(switchTabs(markup)).toEqual([]);
        expect(markup).not.toContain('role="tablist"');
    });

    it('switches between several panels, and mounts the panel in view only until another is shown', () => {
        const markup = renderToStaticMarkup(h(GovernanceDrawer, {
            ...drawerProps,
            panels: [panel('governance'), panel('chat', {component: () => h('p', null, 'the chat')})],
            active: 'chat'
        }));
        expect(switchTabs(markup)).toEqual(['governance', 'chat']);
        expect(markup).toContain('aria-selected="true" aria-controls="content-governance-drawer-panel-chat"');
        expect(shownPanels(markup)).toEqual(['chat']);
        expect(markup).toContain('the chat');
        expect(markup).toMatch(/id="content-governance-drawer-title"[^>]*>chat\.label</);
    });

    it('gives a panel the page, the language, its context and a way to close the drawer', () => {
        const received = [];
        const context = {finding: {rule: 'seo.title'}};
        const onClose = vi.fn();
        renderToStaticMarkup(h(GovernanceDrawer, {
            ...drawerProps,
            onClose,
            contexts: {chat: context},
            panels: [panel('governance'), panel('chat', {
                component: props => {
                    received.push(props);
                    return null;
                }
            })],
            active: 'chat'
        }));
        const [props] = received;
        expect(props.path).toBe('/sites/digitall/home');
        expect(props.language).toBe('en');
        expect(props.context).toBe(context);
        expect(props.settings).toBe(drawerProps.settings);
        props.onClose();
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('gives a panel opened by its button no context', () => {
        const received = [];
        renderToStaticMarkup(h(GovernanceDrawer, {
            ...drawerProps,
            panels: [panel('chat', {
                component: props => {
                    received.push(props);
                    return null;
                }
            })],
            active: 'chat'
        }));
        expect(received[0].context).toBeNull();
    });
});
