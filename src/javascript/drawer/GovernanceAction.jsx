import React, {Suspense, lazy, useCallback, useEffect, useState} from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {useSiteSettings} from '../common/useSiteSettings';
import {MODULE, OPEN_DRAWER_EVENT} from '../common/module';
import {drawerTabs} from './entries';

// The drawer loads apart from jContent's start: fetched when the browser is idle once the button
// shows, and at the latest on the first click. Each tab loads the first time the drawer shows it.
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

/**
 * Why the page cannot use a tab, or undefined when it can: a tab may need a page (not another main
 * resource) and write access to it.
 */
function unavailable(entry, isPage, canWrite) {
    if (entry.requirePage && !isPage) {
        return 'notPage';
    }
    if (entry.requireWrite && !canWrite) {
        return 'noWrite';
    }
    return undefined;
}

/**
 * The header button of Page Builder and jContent, on a page or a main resource of a site that
 * enables the module and keeps at least one tab of the drawer switched on.
 */
export const GovernanceAction = ({path, language, render: Render, ...rest}) => {
    const [open, setOpen] = useState(null);
    const reduxLanguage = useSelector(state => state.language);
    const siteKey = useSelector(state => state.site);
    const jcontentPath = useSelector(state => state.jcontent?.path || null);
    const pagePath = path || jcontentPath;
    const lang = language || reduxLanguage || 'en';

    const {checksResult} = useNodeChecks(
        {path: pagePath},
        {showOnNodeTypes: ['jnt:page', 'jmix:mainResource'], requireModuleInstalledOnSite: [MODULE]}
    );
    // A tab may need a page, and write access to it (drawer/entries.js)
    const {checksResult: isPage} = useNodeChecks({path: pagePath}, {showOnNodeTypes: ['jnt:page']});
    const {checksResult: canWrite} = useNodeChecks({path: pagePath}, {requiredPermission: ['jcr:write']});
    const {settings, isOn} = useSiteSettings(checksResult ? siteKey : null);

    const close = useCallback(() => setOpen(null), []);
    // The panel docks beside the page and leaves this button in view: a second press closes it.
    const toggle = useCallback(() => setOpen(o => (o ? null : {section: null})), []);
    const reopen = useCallback(section => setOpen({section}), []);

    // The Publish menu's check opens this drawer on the section to review.
    useEffect(() => {
        const onOpen = event => {
            if (event.detail?.path === pagePath) {
                reopen(event.detail.section || null);
            }
        };
        globalThis.addEventListener(OPEN_DRAWER_EVENT, onOpen);
        return () => globalThis.removeEventListener(OPEN_DRAWER_EVENT, onOpen);
    }, [pagePath, reopen]);

    const shown = Boolean(pagePath && checksResult && settings);
    useEffect(() => (shown ? whenIdle(() => {
        loadDrawer().catch(() => {});
    }) : undefined), [shown]);

    if (!shown) {
        return null;
    }

    // Every tab of the registry that the site keeps on, in the order of their priority
    const tabs = drawerTabs().filter(entry => isOn(entry.key))
        .map(entry => ({id: entry.key, entry, unavailable: unavailable(entry, isPage, canWrite)}));
    if (tabs.length === 0) {
        return null;
    }

    return (
        <>
            <Render {...rest} onClick={toggle}/>
            {open && ReactDOM.createPortal(
                <Suspense fallback={null}>
                    <GovernanceDrawer path={pagePath}
                                      language={lang}
                                      tabs={tabs}
                                      initialSection={open.section}
                                      settings={settings}
                                      isOn={isOn}
                                      onClose={close}
                                      onReopen={reopen}/>
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
