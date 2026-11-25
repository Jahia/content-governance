import React, {Suspense, useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useApolloClient} from '@apollo/client';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {Loader, Tab, TabItem, Typography} from '@jahia/moonstone';
import {HelpTab} from '../common/HelpTab';
import {useGovernanceTranslation} from '../common/i18n';
import {GOVERNANCE_PANEL} from '../common/module';
import {useDrawerDisplay} from '../common/useDrawerDisplay';
import {fetchPageAudits, saveFindings} from '../audit/auditHistory';
import {usePageAudit} from '../audit/usePageAudit';
import {PagePreview} from '../audit/PageAudit';
import {OTHER} from '../audit/subdomainResults';
import {useGeoCheck} from '../geo/useGeoCheck';
import {useComplianceScan} from '../compliance/useComplianceScan';
import {FixFirst} from './FixFirst';
import {entryFindings, isSerious, rankFindings} from './findings';
import {drawerTabs, findingActions} from './entries';
import {openPanel} from './drawerApi';
import {DrawerEntryContext} from './DrawerEntryContext';
import {EntryBoundary} from './EntryBoundary';
import {DomainTab} from './DomainTab';
import {heldTabs, usableHeldIn, withHeldSections, withoutHeld} from './heldTabs';
import {rulePlaces, useDrawerLayout} from './layout';
import {checksOf, COMPLIANCE, GEO, PAGE} from './subdomains';
import {domainFindings, sectionsOf} from './sections';
import styles from './GovernanceDrawer.module.css';
import {CountBadge} from '../common/CountBadge';

const NO_DOMAINS = Object.freeze([]);
const NO_PLACES = rulePlaces([]);

/**
 * Why the page cannot use a tab of another module, or undefined when it can: a tab may need a page (not another
 * main resource) and write access to it.
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

/** The subdomain that shows the results of the page check under a key of `RESULT_KEYS`: `health` for `jahia`. */
const SUBDOMAIN_OF_TAB = {jahia: 'health'};

/**
 * The tabs of the governance panel, "Fix first", and the content of those tabs. The main tabs are the domains of
 * the layout of the site, such as Page quality, in its order (drawer/layout.js): each shows its subdomains as
 * sections (drawer/DomainTab.jsx). The tabs that other modules register come after them (drawer/entries.js),
 * except a tab that a subdomain holds, which shows as the section of that subdomain (drawer/heldTabs.js). The
 * bar of these tabs is the main tab bar of the drawer, right under its header. A tab mounts the first time it is
 * shown and stays mounted while the drawer is open, so switching back to it keeps its results and any answer
 * still on its way.
 *
 * The three checks of the module feed the domains: the page check, with the preview of the page, the GEO check
 * and the compliance scan (drawer/subdomains.js). Each runs once for the drawer, the first time a domain that
 * reads it shows, so a subdomain that a site moves to another domain keeps its results.
 *
 * A tab of another module that the page cannot use (one that needs a page, or write access) stays in the bar,
 * greyed out, with the reason as a tooltip. The compliance scan needs them too: its sections say why instead.
 *
 * A tab, and the actions on a finding, read the page, the language and the findings from its props and from
 * `DrawerEntryContext`.
 */
export const GovernanceSections = ({path, language, domains = NO_DOMAINS, places = NO_PLACES, tabs, request, settings, isOn, isPage = true, canWrite = true, onClose}) => {
    const {t} = useGovernanceTranslation('drawer');
    const {t: tAudit} = useGovernanceTranslation('audit');
    const {t: tGeo} = useGovernanceTranslation('geo');
    const {t: tCompliance} = useGovernanceTranslation('compliance');
    // An entry's labels are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    const site = useSelector(state => state.site);
    // Read once per opening: modules add their entries when jContent starts
    const actions = useMemo(() => findingActions(), []);
    // "Fix first" shows where the cfg of the server shows it
    const display = useDrawerDisplay();
    // The client of jContent, with which a tab stores and reads its history
    const client = useApolloClient();

    // The bar: the domains, then the tabs of other modules whose key is neither the id of a domain nor of a
    // subdomain, which shows the tab as its section (drawer/heldTabs.js)
    const domainIds = new Set(domains.map(domain => domain.id));
    const held = heldTabs(domains, tabs);
    const bar = [
        ...domains.map(domain => ({id: domain.id, label: domain.label, help: domain.help || domain.label, domain})),
        ...tabs.filter(tab => !domainIds.has(tab.id) && !held.has(tab.id)).map(tab => ({
            ...tab,
            label: translate(tab.entry.label),
            help: translate(tab.entry.help || tab.entry.label)
        }))
    ];
    const usable = bar.filter(item => !item.unavailable);
    const usableIds = usable.map(item => item.id).join(',');
    const domainOfSubdomain = useMemo(() => new Map(domains.flatMap(domain => domain.subdomains.map(subdomain => [subdomain.id, domain.id]))),
        [domains]);

    // A request names a tab, or a subdomain, whose domain then shows with its section open
    const resolve = useCallback(section => {
        if (!section) {
            return null;
        }
        if (usableIds.split(',').includes(section)) {
            return {id: section};
        }
        const domain = domainOfSubdomain.get(section);
        return domain ? {id: domain, tab: section} : null;
    }, [usableIds, domainOfSubdomain]);

    const [active, setActive] = useState(() => (resolve(request?.section) || usable[0] || {}).id);
    const [visited, setVisited] = useState(() => new Set(active ? [active] : []));
    const [counts, setCounts] = useState({});
    // Findings per tab of another module for "Fix first", and the subdomain a link asks a domain to open
    const [findings, setFindings] = useState({});
    const [requested, setRequested] = useState(() => {
        const wanted = resolve(request?.section);
        return wanted?.tab ? {section: wanted.id, tab: wanted.tab, at: Date.now()} : null;
    });

    const select = useCallback(id => {
        setActive(id);
        setVisited(v => (v.has(id) ? v : new Set([...v, id])));
    }, []);

    // A request to open the panel on a tab or a subdomain (the Publish menu, a section reopening after an
    // edit): a new object on each request, so a second request for the same tab shows it again
    useEffect(() => {
        const wanted = resolve(request?.section);
        if (wanted) {
            select(wanted.id);
            if (wanted.tab) {
                setRequested({section: wanted.id, tab: wanted.tab, at: Date.now()});
            }
        }
    }, [request, resolve, select]);

    // The checks run once a domain that reads them showed
    const shownKey = domains.flatMap(domain => domain.subdomains.map(subdomain => subdomain.id)).join(',');
    const shown = useMemo(() => new Set(shownKey.split(',')), [shownKey]);
    const pageIsOn = useCallback(tab => tab === OTHER || shown.has(SUBDOMAIN_OF_TAB[tab] || tab), [shown]);
    // The checks of the module read the subdomains that hold no tab
    const heldKey = [...held.keys()].join(',');
    const ownDomains = useMemo(() => {
        const ids = new Set(heldKey.split(','));
        return domains.map(domain => withoutHeld(domain, ids));
    }, [domains, heldKey]);
    const checks = useMemo(() => Object.fromEntries(ownDomains.map(domain => [domain.id, checksOf(domain, places)])), [ownDomains, places]);
    const needed = new Set(domains.filter(domain => visited.has(domain.id)).flatMap(domain => [...checks[domain.id]]));
    let complianceUnavailable = null;
    if (!isPage) {
        complianceUnavailable = 'notPage';
    } else if (!canWrite) {
        complianceUnavailable = 'noWrite';
    }
    const page = usePageAudit({path, language, active: needed.has(PAGE), isOn: pageIsOn});
    const geo = useGeoCheck({path, language, active: needed.has(GEO), citationsOn: shown.has('geo.citations')});
    const compliance = useComplianceScan({path, language, active: needed.has(COMPLIANCE) && !complianceUnavailable});
    const engines = {page, geo, compliance};

    const reportCount = useCallback((id, count) => {
        setCounts(c => (c[id] === count ? c : {...c, [id]: count}));
    }, []);

    // Tabs report on every change of their results: the same list again changes nothing. Each finding
    // names the tab that found it, so "Fix first" opens that tab.
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

    // The sections of each domain, and the findings of the domains, the same object while they stay the same
    const ownKey = JSON.stringify(domainFindings(ownDomains, engines, places, complianceUnavailable, {audit: tAudit, geo: tGeo, compliance: tCompliance}));
    const own = useMemo(() => JSON.parse(ownKey), [ownKey]);
    const all = useMemo(() => ({...findings, ...own}), [findings, own]);
    const hasSerious = id => (all[id] || []).some(finding => isSerious(finding.severity));
    const sections = Object.fromEntries(domains.map((domain, index) => [domain.id, withHeldSections(domain,
        sectionsOf(ownDomains[index], engines, places, complianceUnavailable), held, counts, hasSerious)]));
    const countOf = id => (sections[id] ? sections[id].reduce((sum, section) => sum + (section.count || 0), 0) : counts[id]);

    // The ids whose findings each usable tab of the bar shows: a domain shows those of the tabs it holds too
    const idsOf = item => (item.domain ? [item.id, ...usableHeldIn(item.domain, held)] : [item.id]);
    const order = usable.flatMap(idsOf).join(',');

    // "Fix first" lists what is critical or serious only, tabs in the drawer's order so findings of
    // equal severity follow it; a tab's count is red when it holds one of them
    const ranked = useMemo(() => rankFindings(order.split(',')
        .flatMap(id => all[id] || [])
        .filter(finding => isSerious(finding.severity))),
    [order, all]);

    // The same object while the findings and the usable tabs stay the same, so an entry that keys
    // an effect on it runs that effect again only when they change
    const entryContext = useMemo(() => ({path, language, findings: entryFindings(all, order.split(',')), actions}),
        [path, language, all, order, actions]);
    // A finding of a tab that a subdomain holds opens its domain, its section, and the part of the tab
    const goTo = useCallback(finding => {
        const domain = domainOfSubdomain.get(finding.section);
        if (domain) {
            select(domain);
            setRequested({section: domain, tab: finding.section, part: finding.tab, at: Date.now()});
            return;
        }
        select(finding.section);
        if (finding.tab) {
            setRequested({section: finding.section, tab: finding.tab, at: Date.now()});
        }
    }, [select, domainOfSubdomain]);
    const requestedFor = id => (requested && requested.section === id ? requested : null);
    // The part that a tab shows on request: a tab of the bar receives the request of its section, a held tab the
    // part that a finding of the tab names
    const requestedTabOf = id => {
        if (!held.has(id)) {
            return requestedFor(id);
        }
        return requested && requested.tab === id && requested.part ? {tab: requested.part, at: requested.at} : null;
    };

    // Content Editor opens over jContent: close the drawer, and open it again on the subdomain once the editor
    // closes, so the compliance scan runs again
    const edit = useCallback((uuid, subdomainId) => {
        if (!globalThis.CE_API?.edit) {
            return;
        }
        onClose();
        globalThis.CE_API.edit({
            uuid,
            site,
            lang: language,
            isFullscreen: false,
            onClosedCallback: () => openPanel(GOVERNANCE_PANEL, {section: subdomainId || active}, path)
        });
    }, [onClose, site, language, path, active]);

    const renderEntry = ({id, entry}) => {
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
                           requestedTab={requestedTabOf(id)}
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

    // A tab that a subdomain holds, in the section of the subdomain, or why the page cannot use it
    const renderHeld = id => {
        const tab = held.get(id);
        return tab.unavailable ? <Typography>{t(`unavailable.${tab.unavailable}`)}</Typography> : renderEntry(tab);
    };

    const renderTab = item => (item.domain ? (
        <DomainTab sections={sections[item.id]}
                   checks={checks[item.id]}
                   engines={engines}
                   places={places}
                   path={path}
                   language={language}
                   requested={requestedFor(item.id)}
                   complianceUnavailable={complianceUnavailable}
                   renderEntry={renderHeld}
                   onEdit={edit}/>
    ) : renderEntry(item));

    // The preview stays behind the tabs, unless the editor shows it on a domain that reads the page check
    const previewHidden = page.previewCollapsed || !checks[active]?.has(PAGE);

    return (
        <DrawerEntryContext.Provider value={entryContext}>
            {bar.length > 0 && (
                <div className={styles.sectionBar}>
                    <Tab className={styles.sectionTabs} role="tablist" aria-label={t('sections')}>
                        {bar.map(item => (
                            <HelpTab key={item.id}
                                     help={item.help}
                                     note={item.unavailable ? t(`unavailable.${item.unavailable}`) : undefined}
                                     isSelected={active === item.id}
                            >
                                <TabItem id={`content-governance-section-${item.id}`}
                                         role="tab"
                                         aria-selected={active === item.id}
                                         aria-controls={`content-governance-panel-${item.id}`}
                                         label={item.label}
                                         isSelected={active === item.id}
                                         isDisabled={Boolean(item.unavailable)}
                                         icon={countOf(item.id) > 0 ? <CountBadge count={countOf(item.id)} isSerious={idsOf(item).some(hasSerious)}/> : undefined}
                                         onClick={() => select(item.id)}/>
                            </HelpTab>
                        ))}
                    </Tab>
                </div>
            )}

            {display.fixFirst && <FixFirst findings={ranked} onGo={goTo}/>}

            {!active && (
                <div className={styles.empty}>
                    <Typography>{t('nothingAvailable')}</Typography>
                </div>
            )}

            <div className={styles.body}>
                {needed.has(PAGE) && <PagePreview audit={page} collapsed={previewHidden}/>}
                {bar.filter(item => visited.has(item.id)).map(item => (
                    <div key={item.id}
                         id={`content-governance-panel-${item.id}`}
                         role="tabpanel"
                         aria-labelledby={`content-governance-section-${item.id}`}
                         className={styles.section}
                         hidden={active !== item.id}
                    >
                        {renderTab(item)}
                    </div>
                ))}
            </div>
        </DrawerEntryContext.Provider>
    );
};

GovernanceSections.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // The domains of the governance panel in the layout of the site, in order (drawer/layout.js)
    domains: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired,
        help: PropTypes.string,
        subdomains: PropTypes.arrayOf(PropTypes.shape({
            id: PropTypes.string.isRequired,
            label: PropTypes.string.isRequired,
            help: PropTypes.string
        })).isRequired
    })),
    // Where the rules of the site sit (drawer/layout.js)
    places: PropTypes.shape({
        subdomainOf: PropTypes.func.isRequired,
        rulesIn: PropTypes.func.isRequired
    }),
    // [{id, entry, unavailable?: 'notPage' | 'noWrite'}]: the tabs of other modules that the site keeps on, in
    // drawer order; `entry` is the registry entry of the tab (drawer/entries.js)
    tabs: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        entry: PropTypes.object.isRequired,
        unavailable: PropTypes.string
    })).isRequired,
    // The context the panel was last opened with: `{section}` names the tab or the subdomain to show
    request: PropTypes.object,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    // Whether the node in view is a page, and whether the editor may write it: the compliance scan needs both
    isPage: PropTypes.bool,
    canWrite: PropTypes.bool,
    onClose: PropTypes.func.isRequired
};

/**
 * The governance panel of the drawer: the domains of the layout of the site, then the tabs of other modules
 * that the site keeps on, each greyed out when the page cannot use it. Its context may name the tab or the
 * subdomain to show: `{section: 'terms'}`.
 */
export const GovernancePanel = ({path, language, context, settings, isOn, onClose}) => {
    const {i18n} = useGovernanceTranslation();
    const siteKey = path.split('/')[2] || null;
    // The labels of the domains follow the editor's interface language
    const layout = useDrawerLayout(siteKey, i18n?.language || 'en');
    // A tab may need a page, and write access to it (drawer/entries.js)
    const {checksResult: isPage, loading: typeLoading} = useNodeChecks({path}, {showOnNodeTypes: ['jnt:page']});
    const {checksResult: canWrite, loading: writeLoading} = useNodeChecks({path}, {requiredPermission: ['jcr:write']});
    if (typeLoading || writeLoading || layout.loading) {
        return <Loader size="small"/>;
    }

    // Every tab of another module that the site keeps on, in the order of their priority
    const tabs = drawerTabs().filter(entry => isOn(entry.key))
        .map(entry => ({id: entry.key, entry, unavailable: unavailable(entry, isPage, canWrite)}));
    return (
        <GovernanceSections path={path}
                            language={language}
                            domains={layout.domains}
                            places={layout.places}
                            tabs={tabs}
                            request={context}
                            settings={settings}
                            isOn={isOn}
                            isPage={Boolean(isPage)}
                            canWrite={Boolean(canWrite)}
                            onClose={onClose}/>
    );
};

GovernancePanel.propTypes = {
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // `{section}`: the tab or the subdomain to show
    context: PropTypes.object,
    settings: PropTypes.object.isRequired,
    isOn: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired
};
