import React, {Suspense, useEffect, useRef} from 'react';
import PropTypes from 'prop-types';
import {Button, Close, Loader, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {EntryBoundary} from './EntryBoundary';
import {useDock} from './useDock';
import styles from './GovernanceDrawer.module.css';

/**
 * The one drawer of the module: a header with the label of the panel in view, and the panels. A
 * panel is a registry entry (drawer/entries.js): the governance panel of the module, or the panel of
 * another module, such as a chat. The buttons of the panels in the header of jContent switch between
 * them (drawer/GovernanceAction.jsx). A panel mounts the first time it is shown and stays mounted
 * while the drawer is open, so switching back to it keeps its state.
 *
 * A panel reads the page, the language and the context it was opened with from its props.
 */
export const GovernanceDrawer = ({path, language, panels, active, contexts, settings, isOn, onClose}) => {
    const {t} = useGovernanceTranslation('drawer');
    // A panel's label is a key with its own namespace (`my-module:panel.label`)
    const {t: translate} = useGovernanceTranslation();
    const drawerRef = useRef(null);
    const previouslyFocused = useRef(null);
    // The panels shown since the drawer opened: they stay mounted
    const visited = useRef(new Set());
    visited.current.add(active);
    const dock = useDock(drawerRef);
    const current = panels.find(panel => panel.key === active);

    // A portal into document.body has no place in the page's focus order: take focus on open and
    // give it back on close.
    useEffect(() => {
        previouslyFocused.current = document.activeElement;
        drawerRef.current?.focus();
        return () => {
            if (typeof previouslyFocused.current?.focus === 'function') {
                previouslyFocused.current.focus();
            }
        };
    }, []);

    // Escape closes. The panel is not modal: focus moves freely between it and the page under edit,
    // and a press on the page leaves it open, as jContent's preview panel does.
    useEffect(() => {
        const node = drawerRef.current;
        if (!node) {
            return undefined;
        }
        const onKeyDown = e => {
            if (e.key === 'Escape') {
                onClose();
            }
        };
        node.addEventListener('keydown', onKeyDown);
        return () => node.removeEventListener('keydown', onKeyDown);
    }, [onClose]);

    const renderPanel = panel => {
        const Panel = panel.component;
        return (
            <EntryBoundary name={panel.key}
                           fallback={<div className={styles.empty}><Typography>{t('panelFailed')}</Typography></div>}
            >
                <Suspense fallback={<Loader size="small"/>}>
                    <Panel path={path}
                           language={language}
                           context={contexts[panel.key] || null}
                           settings={settings}
                           isOn={isOn}
                           onClose={onClose}/>
                </Suspense>
            </EntryBoundary>
        );
    };

    return (
        // A non-modal <dialog>, rendered `open` rather than through showModal(): showModal() makes it
        // modal and moves it to the browser's top layer, above Content Editor and jContent's menus.
        // useDock places it beside the page under edit.
        <dialog ref={drawerRef}
                open
                className={styles.drawer}
                aria-labelledby="content-governance-drawer-title"
                tabIndex={-1}
        >
            {/* The left edge: dragged, or moved with the arrow keys once focused. A native button: Moonstone
                has no splitter, and Sonar refuses a focusable separator role on a div (S6819, S6845, S6847). */}
            <button type="button"
                    aria-label={t('resize')}
                    title={t('resize')}
                    className={styles.resize}
                    onPointerDown={dock.onPointerDown}
                    onKeyDown={dock.onKeyDown}/>
            <header className={styles.header}>
                <div className={styles.headerText}>
                    <Typography id="content-governance-drawer-title" variant="heading" component="h2">
                        {current ? translate(current.label) : t('title')}
                    </Typography>
                    <Typography variant="caption" className={styles.path} title={path}>{path}</Typography>
                </div>
                <Button variant="ghost" icon={<Close/>} aria-label={t('close')} onClick={() => onClose()}/>
            </header>

            {panels.filter(panel => visited.current.has(panel.key)).map(panel => (
                <div key={panel.key}
                     id={`content-governance-drawer-panel-${panel.key}`}
                     className={styles.panel}
                     hidden={active !== panel.key}
                >
                    {renderPanel(panel)}
                </div>
            ))}
        </dialog>
    );
};

GovernanceDrawer.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // The registry entries of the panels the page can show, in order (drawer/entries.js)
    panels: PropTypes.arrayOf(PropTypes.shape({
        key: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired,
        component: PropTypes.elementType.isRequired
    })).isRequired,
    // The key of the panel in view
    active: PropTypes.string.isRequired,
    // The context each panel was last opened with, by key (drawer/drawerState.js)
    contexts: PropTypes.object.isRequired,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
