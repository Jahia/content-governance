import React, {Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {useSiteSettings} from '../common/useSiteSettings';
import {GOVERNANCE_PANEL, MODULE} from '../common/module';
import {drawerPanels, PANEL_NODE_TYPES} from './entries';
import {onPanelRequest} from './drawerApi';
import {openWith, pressButton} from './drawerState';
import {rememberPanel, rememberedState} from './drawerMemory';

// The drawer loads apart from jContent's start: fetched when the browser is idle once a button
// shows, and at the latest on the first click. Each panel and tab loads the first time the drawer
// shows it.
const loadDrawer = () => import('./GovernanceDrawer');
const GovernanceDrawer = lazy(() => loadDrawer().then(module => ({default: module.GovernanceDrawer})));

function whenIdle(callback) {
    if (globalThis.requestIdleCallback) {
        const id = globalThis.requestIdleCallback(callback, {timeout: 5000});
        return () => globalThis.cancelIdleCallback(id);
    }
    const timer = setTimeout(callback, 2000);
    return () => clearTimeout(timer);
}

/** A panel whose entry sets no condition of its own is available wherever it shows. */
const useAlwaysAvailable = () => true;

/**
 * The button of one panel, where the page can show the panel: on its node types, and when its own
 * condition (`useAvailable` of its entry) says so. It tells the action whether the panel is
 * available, so the drawer lists the same panels as the buttons. The buttons are the switch
 * between the panels: the button of the panel in view is pressed, in the accent colour.
 */
const PanelButton = ({entry, path, language, siteKey, settings, isOn, isActive, render: Render, renderProps, onPress, onAvailable}) => {
    // The entry of a button never changes: the hook is the same one on every render
    const useAvailable = entry.useAvailable || useAlwaysAvailable;
    const {checksResult: onType} = useNodeChecks({path}, {showOnNodeTypes: entry.showOnNodeTypes || PANEL_NODE_TYPES});
    const allowed = useAvailable({path, language, siteKey, settings, isOn});
    const available = Boolean(onType) && allowed === true;

    useEffect(() => {
        onAvailable(entry.key, available);
        return () => onAvailable(entry.key, false);
    }, [entry.key, available, onAvailable]);

    if (!available) {
        return null;
    }
    // jContent gives a button the key of its action as data-sel-role: the governance panel keeps it
    const role = entry.key === GOVERNANCE_PANEL ? undefined : `contentGovernancePanel-${entry.key}`;
    // jContent's renderer gives buttonProps to the Moonstone button
    const buttonProps = {...renderProps.buttonProps, 'aria-pressed': isActive, ...(isActive ? {color: 'accent'} : {})};
    return (
        <Render {...renderProps}
                buttonProps={buttonProps}
                buttonLabel={entry.buttonLabel}
                buttonIcon={entry.buttonIcon}
                dataSelRole={role}
                onClick={() => onPress(entry.key)}/>
    );
};

PanelButton.propTypes = {
    // A registry entry of drawerPanels (drawer/entries.js)
    entry: PropTypes.object.isRequired,
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    siteKey: PropTypes.string,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    // The drawer is open on this panel
    isActive: PropTypes.bool.isRequired,
    render: PropTypes.elementType.isRequired,
    // What jContent gives the action, for its button renderer
    renderProps: PropTypes.object.isRequired,
    // (key): the button was pressed
    onPress: PropTypes.func.isRequired,
    // (key, available): whether the page can show the panel
    onAvailable: PropTypes.func.isRequired
};

/**
 * The header buttons of Page Builder and jContent, one per panel of the drawer, on a site that
 * enables the module and keeps the panel switched on. A button opens the one drawer on its panel.
 * Code opens it too, through `openPanel` (drawer/drawerApi.js). The drawer stays open or closed
 * from one page to the next (drawer/drawerMemory.js).
 */
export const GovernanceAction = ({path, language, render: Render, ...rest}) => {
    const [state, setState] = useState(rememberedState);
    const [available, setAvailable] = useState({});
    const reduxLanguage = useSelector(s => s.language);
    const siteKey = useSelector(s => s.site);
    const jcontentPath = useSelector(s => s.jcontent?.path || null);
    const pagePath = path || jcontentPath;
    const lang = language || reduxLanguage || 'en';

    const {checksResult: moduleOn} = useNodeChecks({path: pagePath}, {requireModuleInstalledOnSite: [MODULE]});
    const {settings, isOn, isPanelOn} = useSiteSettings(moduleOn ? siteKey : null);
    // Read once per mount: modules add their panels when jContent starts
    const panels = useMemo(() => drawerPanels(), []);

    const onAvailable = useCallback((id, value) => {
        setAvailable(a => (Boolean(a[id]) === value ? a : {...a, [id]: value}));
    }, []);
    const close = useCallback(() => setState(null), []);
    const press = useCallback(id => setState(s => pressButton(s, id)), []);

    const panelInState = state ? state.panel : null;
    useEffect(() => rememberPanel(panelInState), [panelInState]);

    const shown = Boolean(pagePath && moduleOn && settings);
    const withButton = shown ? panels.filter(entry => isPanelOn(entry.key)) : [];
    const inView = withButton.filter(entry => available[entry.key]);
    // The panels a request may open, read by the listener when the request comes
    const openable = useRef([]);
    openable.current = inView.map(entry => entry.key);

    useEffect(() => onPanelRequest(({panel, context, path: wanted}) => {
        if ((wanted && wanted !== pagePath) || !openable.current.includes(panel)) {
            return false;
        }
        setState(s => openWith(s, panel, context));
        return true;
    }), [pagePath]);

    const anyButton = inView.length > 0;
    useEffect(() => (anyButton ? whenIdle(() => {
        loadDrawer().catch(() => {});
    }) : undefined), [anyButton]);

    if (!shown) {
        return null;
    }

    // A panel the page can no longer show leaves the drawer closed until it can
    const active = state && openable.current.includes(state.panel) ? state.panel : null;
    return (
        <>
            {withButton.map(entry => (
                <PanelButton key={entry.key}
                             entry={entry}
                             path={pagePath}
                             language={lang}
                             siteKey={siteKey}
                             settings={settings}
                             isOn={isOn}
                             isActive={entry.key === active}
                             render={Render}
                             renderProps={rest}
                             onPress={press}
                             onAvailable={onAvailable}/>
            ))}
            {active && ReactDOM.createPortal(
                <Suspense fallback={null}>
                    <GovernanceDrawer path={pagePath}
                                      language={lang}
                                      panels={inView}
                                      active={active}
                                      contexts={state.contexts}
                                      settings={settings}
                                      isOn={isOn}
                                      onClose={close}/>
                </Suspense>,
                document.body
            )}
        </>
    );
};

GovernanceAction.propTypes = {
    path: PropTypes.string,
    language: PropTypes.string,
    render: PropTypes.elementType.isRequired
};
