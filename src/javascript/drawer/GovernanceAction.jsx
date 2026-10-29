import React, {useCallback, useRef, useState} from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {useSiteSettings} from '../common/useSiteSettings';
import {MODULE} from '../common/module';
import {GovernanceDrawer} from './GovernanceDrawer';

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

    if (!pagePath || !checksResult || !settings) {
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
                <GovernanceDrawer path={pagePath}
                                  language={lang}
                                  sections={sections}
                                  initialSection={open.section}
                                  settings={settings}
                                  isOn={isOn}
                                  onClose={close}
                                  onReopen={reopen}/>,
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
