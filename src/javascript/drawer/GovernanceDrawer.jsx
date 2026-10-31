import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {Badge, Button, Close, Tab, TabItem, Typography} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import {useGovernanceTranslation} from '../common/i18n';
import {AuditSection} from '../audit/AuditSection';
import {GeoSection} from '../geo/GeoSection';
import {ComplianceSection} from '../compliance/ComplianceSection';
import {FixFirst} from './FixFirst';
import {rankFindings} from './findings';
import styles from './GovernanceDrawer.module.css';

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * The one drawer of the module: a header, one tab per section the site switched on, and the
 * sections themselves. A section mounts the first time it is shown and stays mounted while the
 * drawer is open, so switching back to it keeps its results and any AI answer still on its way.
 *
 * Sections the page cannot use (compliance on a content item that is not a page, or without write
 * access) stay in the bar, greyed out, with the reason as a tooltip.
 */
export const GovernanceDrawer = ({path, language, sections, initialSection, settings, isOn, onClose, onReopen}) => {
    const {t} = useGovernanceTranslation('drawer');
    const usable = sections.filter(s => !s.unavailable);
    const [active, setActive] = useState(() => (usable.find(s => s.id === initialSection) || usable[0] || {}).id);
    const [visited, setVisited] = useState(() => new Set(active ? [active] : []));
    const [counts, setCounts] = useState({});
    // Findings per section for "Fix first", and the tab a "Fix first" link asks a section to show
    const [findings, setFindings] = useState({});
    const [requested, setRequested] = useState(null);
    const drawerRef = useRef(null);
    const previouslyFocused = useRef(null);
    // Set when a press elsewhere closed the drawer: focus then belongs where the user pressed.
    const closedOutside = useRef(false);

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
    const scoped = useMemo(() => Object.fromEntries(['audit', 'geo', 'compliance'].map(id => [id, {
        isOn: tab => isOn(id, tab),
        onCount: count => reportCount(id, count),
        onFindings: list => reportFindings(id, list)
    }])), [isOn, reportCount, reportFindings]);

    // Sections in the drawer's order, so findings of equal severity follow it
    const ranked = useMemo(() => rankFindings(sections.filter(s => !s.unavailable).flatMap(s => findings[s.id] || [])),
        [sections, findings]);
    const goTo = useCallback(finding => {
        select(finding.section);
        if (finding.tab) {
            setRequested({section: finding.section, tab: finding.tab, at: Date.now()});
        }
    }, [select]);
    const requestedFor = id => (requested && requested.section === id ? requested : null);
    const reopenCompliance = useCallback(() => onReopen('compliance'), [onReopen]);

    // A portal into document.body has no place in the page's focus order: take focus on open and
    // give it back on close, unless the drawer closed because the user pressed somewhere else.
    useEffect(() => {
        previouslyFocused.current = document.activeElement;
        drawerRef.current?.focus();
        return () => {
            if (!closedOutside.current && typeof previouslyFocused.current?.focus === 'function') {
                previouslyFocused.current.focus();
            }
        };
    }, []);

    // Escape closes; Tab stays inside. The dialog is non-modal (see below), so both are ours.
    useEffect(() => {
        const node = drawerRef.current;
        if (!node) {
            return undefined;
        }
        const onKeyDown = e => {
            if (e.key === 'Escape') {
                onClose();
                return;
            }
            if (e.key !== 'Tab') {
                return;
            }
            const focusable = [...node.querySelectorAll(FOCUSABLE)].filter(el => !el.closest('[hidden]'));
            if (focusable.length === 0) {
                return;
            }
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        };
        node.addEventListener('keydown', onKeyDown);
        return () => node.removeEventListener('keydown', onKeyDown);
    }, [onClose]);

    // Closes on a press outside, the way jContent's own side panels behave. The page under edit is
    // an iframe whose clicks never reach this document, so focus moving into an iframe outside the
    // drawer counts as outside too; the preview frame of the Page quality section is inside.
    useEffect(() => {
        const isOutside = target => drawerRef.current && target instanceof Node && !drawerRef.current.contains(target);
        const onPointerDown = e => {
            if (isOutside(e.target)) {
                closedOutside.current = true;
                onClose('outside');
            }
        };
        const onBlur = () => {
            setTimeout(() => {
                const focused = document.activeElement;
                if (focused && focused.tagName === 'IFRAME' && isOutside(focused)) {
                    closedOutside.current = true;
                    onClose('outside');
                }
            }, 0);
        };
        document.addEventListener('pointerdown', onPointerDown, true);
        globalThis.addEventListener('blur', onBlur);
        return () => {
            document.removeEventListener('pointerdown', onPointerDown, true);
            globalThis.removeEventListener('blur', onBlur);
        };
    }, [onClose]);

    const renderSection = id => {
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

    return (
        // A real <dialog>, rendered `open` rather than through showModal(): showModal() moves it to
        // the browser's top layer, out of jContent's stacking context. aria-modal and the focus
        // trap stay ours to enforce for the same reason.
        <dialog ref={drawerRef}
                open
                className={styles.drawer}
                aria-labelledby="content-governance-drawer-title"
                aria-modal="true"
                tabIndex={-1}
        >
            <header className={styles.header}>
                <div className={styles.headerText}>
                    <Typography id="content-governance-drawer-title" variant="heading" component="h2">{t('title')}</Typography>
                    <Typography variant="caption" className={styles.path} title={path}>{path}</Typography>
                </div>
                <Button variant="ghost" icon={<Close/>} aria-label={t('close')} onClick={() => onClose()}/>
            </header>

            {sections.length > 0 && (
                <div className={styles.sectionBar}>
                    <Tab className={styles.sectionTabs} role="tablist" aria-label={t('sections')}>
                        {sections.map(section => (
                            <HelpTab key={section.id}
                                     help={t(`sectionHelp.${section.id}`)}
                                     note={section.unavailable ? t(`unavailable.${section.unavailable}`) : undefined}
                                     isSelected={active === section.id}
                            >
                                <TabItem id={`content-governance-section-${section.id}`}
                                         role="tab"
                                         aria-selected={active === section.id}
                                         aria-controls={`content-governance-panel-${section.id}`}
                                         size="big"
                                         label={t(`section.${section.id}`)}
                                         isSelected={active === section.id}
                                         isDisabled={Boolean(section.unavailable)}
                                         icon={counts[section.id] > 0 ? <Badge label={String(counts[section.id])} color="danger"/> : undefined}
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

            {sections.filter(s => visited.has(s.id)).map(section => (
                <div key={section.id}
                     id={`content-governance-panel-${section.id}`}
                     role="tabpanel"
                     aria-labelledby={`content-governance-section-${section.id}`}
                     className={styles.section}
                     hidden={active !== section.id}
                >
                    {renderSection(section.id)}
                </div>
            ))}
        </dialog>
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
