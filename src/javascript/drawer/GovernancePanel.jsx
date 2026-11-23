import React, {Suspense, useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {Loader, Tab, TabItem, Typography} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import {useGovernanceTranslation} from '../common/i18n';
import {GOVERNANCE_PANEL} from '../common/module';
import {fetchPageAudits, saveFindings} from '../audit/auditHistory';
import {FixFirst} from './FixFirst';
import {entryFindings, isSerious, rankFindings} from './findings';
import {drawerTabs, findingActions} from './entries';
import {openPanel} from './drawerApi';
import {DrawerEntryContext} from './DrawerEntryContext';
import {EntryBoundary} from './EntryBoundary';
import styles from './GovernanceDrawer.module.css';
import {CountBadge} from '../common/CountBadge';

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
 * The tabs of the governance panel: one tab per entry of the registry the site switched on
 * (drawer/entries.js), "Fix first", and the content of those tabs. Each tab is a domain, such as
 * Page quality, and the bar of the domains is the main tab bar of the drawer, right under its
 * header. The module's own tabs register there too, so the panel renders every tab the same way. A
 * tab mounts the first time it is shown and stays mounted while the drawer is open, so switching
 * back to it keeps its results and any answer still on its way.
 *
 * Tabs the page cannot use (compliance on a content item that is not a page, or without write
 * access) stay in the bar, greyed out, with the reason as a tooltip.
 *
 * A tab, and the actions on a finding, read the page, the language and the findings from its props
 * and from `DrawerEntryContext`.
 */
export const GovernanceSections = ({path, language, tabs, request, settings, isOn, onClose}) => {
    const {t} = useGovernanceTranslation('drawer');
    // An entry's labels are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    // Read once per opening: modules add their entries when jContent starts
    const actions = useMemo(() => findingActions(), []);
    // The client of jContent, with which a tab stores and reads its history
    const client = useApolloClient();
    const usable = tabs.filter(s => !s.unavailable);
    const usableIds = usable.map(s => s.id).join(',');
    const [active, setActive] = useState(() => (usable.find(s => s.id === request?.section) || usable[0] || {}).id);
    const [visited, setVisited] = useState(() => new Set(active ? [active] : []));
    const [counts, setCounts] = useState({});
    // Findings per section for "Fix first", and the tab a "Fix first" link asks a section to show
    const [findings, setFindings] = useState({});
    const [requested, setRequested] = useState(null);

    const select = useCallback(id => {
        setActive(id);
        setVisited(v => (v.has(id) ? v : new Set([...v, id])));
    }, []);

    // A request to open the panel on a tab (the Publish menu, a tab reopening after an edit): a new
    // object on each request, so a second request for the same tab shows it again
    useEffect(() => {
        const wanted = request?.section;
        if (wanted && usableIds.split(',').includes(wanted)) {
            select(wanted);
        }
    }, [request, usableIds, select]);

    const reportCount = useCallback((id, count) => {
        setCounts(c => (c[id] === count ? c : {...c, [id]: count}));
    }, []);

    // Tabs report on every change of their results: the same list again changes nothing. Each
    // finding names the tab that found it, so "Fix first" opens that tab.
    const reportFindings = useCallback((id, list) => {
        const own = (Array.isArray(list) ? list : []).map(finding => ({...finding, section: id}));
        setFindings(f => (JSON.stringify(f[id] || []) === JSON.stringify(own) ? f : {...f, [id]: own}));
    }, []);

    const ids = tabs.map(s => s.id).join(',');
    // Stable per tab: a tab keys its effects on these, so a new function on every render of the
    // drawer would refetch on every render.
    const scoped = useMemo(() => Object.fromEntries(ids.split(',').map(id => [id, {
        isOn: part => isOn(id, part),
        onCount: count => reportCount(id, count),
        onFindings: list => reportFindings(id, list),
        onReopen: () => openPanel(GOVERNANCE_PANEL, {section: id}, path),
        saveFindings: list => saveFindings(client, path, language, id, list),
        fetchHistory: () => fetchPageAudits(client, path, language, id)
    }])), [ids, isOn, reportCount, reportFindings, client, path, language]);

    // "Fix first" lists what is critical or serious only, tabs in the drawer's order so findings of
    // equal severity follow it; a tab's count is red when it holds one of them
    const ranked = useMemo(() => rankFindings(usableIds.split(',')
        .flatMap(id => findings[id] || [])
        .filter(finding => isSerious(finding.severity))),
    [usableIds, findings]);

    // The same object while the findings and the usable tabs stay the same, so an entry that keys
    // an effect on it runs that effect again only when they change
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

    const renderTab = ({id, entry}) => {
        const Entry = entry.component;
        return (
            <EntryBoundary name={id}
                           fallback={<div className={styles.empty}><Typography>{t('entryFailed')}</Typography></div>}
            >
                <Suspense fallback={<Loader size="small"/>}>
                    <Entry path={path}
                           language={language}
                           findings={entryContext.findings}
                           settings={settings}
                           isOn={scoped[id].isOn}
                           requestedTab={requestedFor(id)}
                           onCount={scoped[id].onCount}
                           onFindings={scoped[id].onFindings}
                           saveFindings={scoped[id].saveFindings}
                           fetchHistory={scoped[id].fetchHistory}
                           onClose={onClose}
                           onReopen={scoped[id].onReopen}/>
                </Suspense>
            </EntryBoundary>
        );
    };

    // A tab has its own label, and its help or else its label as tooltip
    const label = tab => translate(tab.entry.label);
    const help = tab => translate(tab.entry.help || tab.entry.label);

    return (
        <DrawerEntryContext.Provider value={entryContext}>
            {tabs.length > 0 && (
                <div className={styles.sectionBar}>
                    <Tab className={styles.sectionTabs} role="tablist" aria-label={t('sections')}>
                        {tabs.map(tab => (
                            <HelpTab key={tab.id}
                                     help={help(tab)}
                                     note={tab.unavailable ? t(`unavailable.${tab.unavailable}`) : undefined}
                                     isSelected={active === tab.id}
                            >
                                <TabItem id={`content-governance-section-${tab.id}`}
                                         role="tab"
                                         aria-selected={active === tab.id}
                                         aria-controls={`content-governance-panel-${tab.id}`}
                                         label={label(tab)}
                                         isSelected={active === tab.id}
                                         isDisabled={Boolean(tab.unavailable)}
                                         icon={counts[tab.id] > 0 ? <CountBadge count={counts[tab.id]} isSerious={hasSerious(tab.id)}/> : undefined}
                                         onClick={() => select(tab.id)}/>
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

            {tabs.filter(s => visited.has(s.id)).map(tab => (
                <div key={tab.id}
                     id={`content-governance-panel-${tab.id}`}
                     role="tabpanel"
                     aria-labelledby={`content-governance-section-${tab.id}`}
                     className={styles.section}
                     hidden={active !== tab.id}
                >
                    {renderTab(tab)}
                </div>
            ))}
        </DrawerEntryContext.Provider>
    );
};

GovernanceSections.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // [{id, entry, unavailable?: 'notPage' | 'noWrite'}] in drawer order, switched-on tabs only;
    // `entry` is the registry entry of the tab (drawer/entries.js)
    tabs: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        entry: PropTypes.object.isRequired,
        unavailable: PropTypes.string
    })).isRequired,
    // The context the panel was last opened with: `{section}` names the tab to show
    request: PropTypes.object,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};

/**
 * The governance panel of the drawer: the tabs of the registry that the site keeps on, each greyed
 * out when the page cannot use it. Its context may name the tab to show: `{section: 'compliance'}`.
 */
export const GovernancePanel = ({path, language, context, settings, isOn, onClose}) => {
    // A tab may need a page, and write access to it (drawer/entries.js)
    const {checksResult: isPage, loading: typeLoading} = useNodeChecks({path}, {showOnNodeTypes: ['jnt:page']});
    const {checksResult: canWrite, loading: writeLoading} = useNodeChecks({path}, {requiredPermission: ['jcr:write']});
    if (typeLoading || writeLoading) {
        return <Loader size="small"/>;
    }

    // Every tab of the registry that the site keeps on, in the order of their priority
    const tabs = drawerTabs().filter(entry => isOn(entry.key))
        .map(entry => ({id: entry.key, entry, unavailable: unavailable(entry, isPage, canWrite)}));
    return (
        <GovernanceSections path={path}
                            language={language}
                            tabs={tabs}
                            request={context}
                            settings={settings}
                            isOn={isOn}
                            onClose={onClose}/>
    );
};

GovernancePanel.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // `{section}`: the tab to show
    context: PropTypes.object,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
