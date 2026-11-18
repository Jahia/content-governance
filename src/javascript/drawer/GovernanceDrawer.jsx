import React, {Suspense, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Button, Close, Loader, Tab, TabItem, Typography} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import {useGovernanceTranslation} from '../common/i18n';
import {AuditSection} from '../audit/AuditSection';
import {GeoSection} from '../geo/GeoSection';
import {ComplianceSection} from '../compliance/ComplianceSection';
import {FixFirst} from './FixFirst';
import {entryFindings, isSerious, rankFindings} from './findings';
import {addedTabs, findingActions} from './entries';
import {DrawerEntryContext} from './DrawerEntryContext';
import {EntryBoundary} from './EntryBoundary';
import {useDock} from './useDock';
import styles from './GovernanceDrawer.module.css';
import {CountBadge} from '../common/CountBadge';

const OWN_SECTIONS = ['audit', 'geo', 'compliance'];

/**
 * The one drawer of the module: a header, one tab per section the site switched on, and the
 * sections themselves. A section mounts the first time it is shown and stays mounted while the
 * drawer is open, so switching back to it keeps its results and any AI answer still on its way.
 *
 * Sections the page cannot use (compliance on a content item that is not a page, or without write
 * access) stay in the bar, greyed out, with the reason as a tooltip.
 *
 * Tabs that other modules add (drawer/entries.js) follow the module's own sections. They, and the
 * actions on a finding, read the page, the language and the findings from `DrawerEntryContext`.
 */
export const GovernanceDrawer = ({path, language, sections, initialSection, settings, isOn, onClose, onReopen}) => {
    const {t} = useGovernanceTranslation('drawer');
    // An entry's labels are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    // Read once per opening: modules add their entries when jContent starts
    const added = useMemo(() => addedTabs(OWN_SECTIONS), []);
    const actions = useMemo(() => findingActions(), []);
    const tabs = [...sections, ...added.map(entry => ({id: entry.key, entry}))];
    const usable = tabs.filter(s => !s.unavailable);
    const [active, setActive] = useState(() => (usable.find(s => s.id === initialSection) || usable[0] || {}).id);
    const [visited, setVisited] = useState(() => new Set(active ? [active] : []));
    const [counts, setCounts] = useState({});
    // Findings per section for "Fix first", and the tab a "Fix first" link asks a section to show
    const [findings, setFindings] = useState({});
    const [requested, setRequested] = useState(null);
    const drawerRef = useRef(null);
    const previouslyFocused = useRef(null);
    const dock = useDock(drawerRef);

    const select = useCallback(id => {
        setActive(id);
        setVisited(v => (v.has(id) ? v : new Set([...v, id])));
    }, []);

    const reportCount = useCallback((id, count) => {
        setCounts(c => (c[id] === count ? c : {...c, [id]: count}));
    }, []);

    // Sections report on every change of their results: the same list again changes nothing.
    const reportFindings = useCallback((id, list) => {
        setFindings(f => (JSON.stringify(f[id] || []) === JSON.stringify(list) ? f : {...f, [id]: list}));
    }, []);

    // Stable per section: a section keys its effects on these, so a new function on every render of
    // the drawer would refetch on every render.
    const scoped = useMemo(() => Object.fromEntries([...OWN_SECTIONS, ...added.map(entry => entry.key)].map(id => [id, {
        isOn: tab => isOn(id, tab),
        onCount: count => reportCount(id, count),
        onFindings: list => reportFindings(id, list)
    }])), [isOn, reportCount, reportFindings, added]);

    // "Fix first" lists what is critical or serious only, sections in the drawer's order so findings
    // of equal severity follow it; a section's count is red when it holds one of them
    const ranked = useMemo(() => rankFindings(sections.filter(s => !s.unavailable)
        .flatMap(s => findings[s.id] || [])
        .filter(finding => isSerious(finding.severity))),
    [sections, findings]);

    // The same object while the findings and the usable sections stay the same, so an entry that
    // keys an effect on it runs that effect again only when they change
    const usableIds = sections.filter(s => !s.unavailable).map(s => s.id).join(',');
    const entryContext = useMemo(() => ({path, language, findings: entryFindings(findings, usableIds.split(',')), actions}),
        [path, language, findings, usableIds, actions]);
    const hasSerious = id => (findings[id] || []).some(finding => isSerious(finding.severity));
    const goTo = useCallback(finding => {
        select(finding.section);
        if (finding.tab) {
            setRequested({section: finding.section, tab: finding.tab, at: Date.now()});
        }
    }, [select]);
    const requestedFor = id => (requested && requested.section === id ? requested : null);
    const reopenCompliance = useCallback(() => onReopen('compliance'), [onReopen]);

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

    const renderEntry = entry => {
        const Entry = entry.component;
        return (
            <EntryBoundary name={entry.key}
                           fallback={<div className={styles.empty}><Typography>{t('entryFailed')}</Typography></div>}
            >
                <Suspense fallback={<Loader size="small"/>}>
                    <Entry path={path}
                           language={language}
                           findings={entryContext.findings}
                           onCount={scoped[entry.key].onCount}/>
                </Suspense>
            </EntryBoundary>
        );
    };

    const renderSection = ({id, entry}) => {
        if (entry) {
            return renderEntry(entry);
        }
        switch (id) {
            case 'audit':
                return (
                    <AuditSection path={path}
                                  language={language}
                                  isOn={scoped.audit.isOn}
                                  ai={settings.ai}
                                  onCount={scoped.audit.onCount}
                                  onFindings={scoped.audit.onFindings}
                                  requestedTab={requestedFor('audit')}/>
                );
            case 'geo':
                return (
                    <GeoSection path={path}
                                language={language}
                                isOn={scoped.geo.isOn}
                                onCount={scoped.geo.onCount}
                                onFindings={scoped.geo.onFindings}
                                requestedTab={requestedFor('geo')}/>
                );
            case 'compliance':
                return (
                    <ComplianceSection path={path}
                                       language={language}
                                       onClose={onClose}
                                       onReopen={reopenCompliance}
                                       onCount={scoped.compliance.onCount}
                                       onFindings={scoped.compliance.onFindings}/>
                );
            default:
                return null;
        }
    };

    // An added tab has its own label, and its help or else its label as tooltip
    const label = section => (section.entry ? translate(section.entry.label) : t(`section.${section.id}`));
    const help = section => (section.entry ? translate(section.entry.help || section.entry.label) : t(`sectionHelp.${section.id}`));

    return (
        // A non-modal <dialog>, rendered `open` rather than through showModal(): showModal() makes it
        // modal and moves it to the browser's top layer, above Content Editor and jContent's menus.
        // useDock places it beside the page under edit.
        <DrawerEntryContext.Provider value={entryContext}>
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
                        <Typography id="content-governance-drawer-title" variant="heading" component="h2">{t('title')}</Typography>
                        <Typography variant="caption" className={styles.path} title={path}>{path}</Typography>
                    </div>
                    <Button variant="ghost" icon={<Close/>} aria-label={t('close')} onClick={() => onClose()}/>
                </header>

                {tabs.length > 0 && (
                    <div className={styles.sectionBar}>
                        <Tab className={styles.sectionTabs} role="tablist" aria-label={t('sections')}>
                            {tabs.map(section => (
                                <HelpTab key={section.id}
                                         help={help(section)}
                                         note={section.unavailable ? t(`unavailable.${section.unavailable}`) : undefined}
                                         isSelected={active === section.id}
                                >
                                    <TabItem id={`content-governance-section-${section.id}`}
                                             role="tab"
                                             aria-selected={active === section.id}
                                             aria-controls={`content-governance-panel-${section.id}`}
                                             size="big"
                                             label={label(section)}
                                             isSelected={active === section.id}
                                             isDisabled={Boolean(section.unavailable)}
                                             icon={counts[section.id] > 0 ? <CountBadge count={counts[section.id]} isSerious={hasSerious(section.id)}/> : undefined}
                                             onClick={() => select(section.id)}/>
                                </HelpTab>
                            ))}
                        </Tab>
                    </div>
                )}

                <FixFirst findings={ranked} onGo={goTo}/>

                {!active && (
                    <div className={styles.empty}>
                        <Typography>{t('nothingAvailable')}</Typography>
                    </div>
                )}

                {tabs.filter(s => visited.has(s.id)).map(section => (
                    <div key={section.id}
                         id={`content-governance-panel-${section.id}`}
                         role="tabpanel"
                         aria-labelledby={`content-governance-section-${section.id}`}
                         className={styles.section}
                         hidden={active !== section.id}
                    >
                        {renderSection(section)}
                    </div>
                ))}
            </dialog>
        </DrawerEntryContext.Provider>
    );
};

GovernanceDrawer.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // [{id, unavailable?: 'notPage' | 'noWrite'}] in drawer order, switched-on sections only
    sections: PropTypes.arrayOf(PropTypes.shape({id: PropTypes.string.isRequired, unavailable: PropTypes.string})).isRequired,
    initialSection: PropTypes.string,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    onReopen: PropTypes.func.isRequired
};
