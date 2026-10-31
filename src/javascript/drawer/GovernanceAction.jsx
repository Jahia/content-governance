import React, {Suspense, lazy, useCallback, useEffect, useRef, useState} from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {useSiteSettings} from '../common/useSiteSettings';
import {MODULE, OPEN_DRAWER_EVENT} from '../common/module';

// The drawer and its three sections load apart from jContent's start: fetched when the browser is
// idle once the button shows, and at the latest on the first click.
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

const SECTION_ORDER = ['audit', 'geo', 'compliance'];

/**
 * The header button of Page Builder and jContent, on a page or a main resource of a site that
 * enables the module and keeps at least one section switched on.
 */
export const GovernanceAction = ({path, language, render: Render, ...rest}) => {
    const [open, setOpen] = useState(null);
    // A press on this button while the drawer is open is also a press outside it, which closes the
    // drawer before the button's click arrives. Without this, the click would reopen it at once.
    const closedOutsideAt = useRef(0);
    const reduxLanguage = useSelector(state => state.language);
    const siteKey = useSelector(state => state.site);
    const jcontentPath = useSelector(state => state.jcontent?.path || null);
    const pagePath = path || jcontentPath;
    const lang = language || reduxLanguage || 'en';

    const {checksResult} = useNodeChecks(
        {path: pagePath},
        {showOnNodeTypes: ['jnt:page', 'jmix:mainResource'], requireModuleInstalledOnSite: [MODULE]}
    );
    // The compliance scan reads a page and everything it shows, as someone who may edit it.
    const {checksResult: isPage} = useNodeChecks({path: pagePath}, {showOnNodeTypes: ['jnt:page']});
    const {checksResult: canWrite} = useNodeChecks({path: pagePath}, {requiredPermission: ['jcr:write']});
    const {settings, isOn} = useSiteSettings(checksResult ? siteKey : null);

    const close = useCallback(reason => {
        if (reason === 'outside') {
            closedOutsideAt.current = Date.now();
        }
        setOpen(null);
    }, []);
    const toggle = useCallback(() => {
        if (Date.now() - closedOutsideAt.current < 500) {
            return;
        }
        setOpen(o => (o ? null : {section: null}));
    }, []);
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

    const sections = SECTION_ORDER.filter(id => isOn(id)).map(id => {
        if (id === 'compliance' && !isPage) {
            return {id, unavailable: 'notPage'};
        }
        if (id === 'compliance' && !canWrite) {
            return {id, unavailable: 'noWrite'};
        }
        return {id};
    });
    if (sections.length === 0) {
        return null;
    }

    return (
        <>
            <Render {...rest} onClick={toggle}/>
            {open && ReactDOM.createPortal(
                <Suspense fallback={null}>
                    <GovernanceDrawer path={pagePath}
                                      language={lang}
                                      sections={sections}
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
