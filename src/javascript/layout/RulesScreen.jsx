import React, {useCallback, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useMutation, useQuery} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {Banner, Button, Chip, Dropdown, HandleDrag, Header, LayoutContent, Loader, Paper, Switch, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {GOVERNANCE_PANEL, MODULE} from '../common/module';
import {drawerPanels, drawerTabs, tabParts} from '../drawer/entries';
import {DOMAIN, RULE, SUBDOMAIN, draftOf, drop, move, moveOptions, sameLayout, shift, toLayout, toggle} from './layoutModel';
import {RESET_LAYOUT, RULES_SCREEN, SAVE_LAYOUT} from './layout.gql';
import styles from './RulesScreen.module.css';

/** The site whose tree holds the defaults of every site. */
export const SYSTEM_SITE = 'systemsite';

/** What a subdomain needs, as a chip: AI citations read BotRank. */
const NEEDS = {'geo.citations': 'needsBotRank'};

/**
 * A level accepts a drop of its own level, and the level under it, which goes last into it. A subdomain that holds
 * the tab of another module takes no rule: the tab shows instead of the findings of rules.
 */
const accepts = (item, over, tabs) => item.level === over.level ||
    (item.level === SUBDOMAIN && over.level === DOMAIN) || (item.level === RULE && over.level === SUBDOMAIN && !tabs[over.id]);

/**
 * Drag and drop between the rows. The handle of a row starts a drag, and a row of the same level, or of the level
 * above, takes the drop. The "Move to" menu does the same moves from the keyboard.
 *
 * @param {boolean} canEdit whether the screen can change the draft
 * @param {Function} setDraft the setter of the draft
 * @param {Object<string, object>} tabs the tabs of other modules by key, which a subdomain may hold
 */
function useDragAndDrop(canEdit, setDraft, tabs) {
    const [dragging, setDragging] = useState(null);
    const [over, setOver] = useState(null);
    const end = useCallback(() => {
        setDragging(null);
        setOver(null);
    }, []);

    const handle = (level, id, rowRef) => (canEdit ? {
        draggable: true,
        onDragStart: e => {
            e.stopPropagation();
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', id);
            if (rowRef.current) {
                e.dataTransfer.setDragImage(rowRef.current, 0, 0);
            }
            setDragging({level, id});
        },
        onDragEnd: end
    } : {});

    const target = (level, id) => (canEdit && dragging ? {
        onDragOver: e => {
            if (accepts(dragging, {level, id}, tabs)) {
                e.preventDefault();
                e.stopPropagation();
                if (over?.id !== id || over?.level !== level) {
                    setOver({level, id});
                }
            }
        },
        onDrop: e => {
            if (accepts(dragging, {level, id}, tabs)) {
                e.preventDefault();
                e.stopPropagation();
                const item = dragging;
                setDraft(d => drop(d, item, {level, id}));
                end();
            }
        }
    } : {});

    const isOver = (level, id) => Boolean(dragging) && over?.level === level && over?.id === id;
    return {handle, target, isOver, dragging};
}

/** The drag handle of a row: drags the row, and says what it moves to a screen reader. */
const DragHandle = ({label, dragProps}) => (
    <span className={styles.handle} title={label} aria-hidden="true" {...dragProps}>
        <HandleDrag/>
    </span>
);

DragHandle.propTypes = {
    label: PropTypes.string.isRequired,
    dragProps: PropTypes.object.isRequired
};

/** The "Move to" menu of an item: up, down, and into another parent, but a rule into no subdomain that holds a tab. */
const MoveMenu = ({level, id, label, draft, labels, tabs, isDisabled, onMove}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const all = moveOptions(draft, level, id);
    const options = level === RULE ? {...all, parents: all.parents.filter(parent => !tabs[parent])} : all;
    const data = [{
        groupLabel: t('moveOrder'),
        options: [
            {label: t('moveUp'), value: 'up', isDisabled: !options.up},
            {label: t('moveDown'), value: 'down', isDisabled: !options.down}
        ]
    }];
    if (options.parents.length > 0) {
        data.push({
            groupLabel: t('moveInto'),
            options: options.parents.map(parent => ({label: labels[parent] || parent, value: `into:${parent}`}))
        });
    }
    return (
        <Dropdown className={styles.move}
                  variant="ghost"
                  size="small"
                  placeholder={t('moveTo')}
                  label={t('moveTo')}
                  value=""
                  data={data}
                  isDisabled={isDisabled}
                  hasSearch={options.parents.length > 7}
                  aria-label={t('moveItem', {item: label})}
                  onChange={(e, item) => onMove(level, id, item.value)}/>
    );
};

MoveMenu.propTypes = {
    level: PropTypes.string.isRequired,
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    draft: PropTypes.object.isRequired,
    // The label of each domain and subdomain, by id
    labels: PropTypes.object.isRequired,
    // The tabs of other modules by key, which a subdomain may hold
    tabs: PropTypes.object.isRequired,
    isDisabled: PropTypes.bool,
    // (level, id, value): value is up, down or into:<parent id>
    onMove: PropTypes.func.isRequired
};

/** The switch of an item, with its state in words. */
const ItemSwitch = ({label, isOn, isDisabled, onToggle}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    return (
        <label className={styles.switchLabel}>
            <Switch checked={isOn}
                    isDisabled={isDisabled}
                    aria-label={label}
                    onChange={(e, value, checked) => onToggle(checked)}/>
            <Typography variant="caption">{isOn ? t('on') : t('off')}</Typography>
        </label>
    );
};

ItemSwitch.propTypes = {
    label: PropTypes.string.isRequired,
    isOn: PropTypes.bool.isRequired,
    isDisabled: PropTypes.bool,
    onToggle: PropTypes.func.isRequired
};

/** One rule of a subdomain. */
const RuleRow = ({id, rule, isParentOn, ctx}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const rowRef = useRef(null);
    const label = rule?.label || id;
    const isOn = !ctx.draft.off.has(id);
    return (
        <li ref={rowRef}
            className={`${styles.rule} ${ctx.dnd.isOver(RULE, id) ? styles.over : ''}`}
            data-rule={id}
            {...ctx.dnd.target(RULE, id)}
        >
            <DragHandle label={t('drag', {item: label})} dragProps={ctx.dnd.handle(RULE, id, rowRef)}/>
            <ItemSwitch label={label} isOn={isOn} isDisabled={!ctx.canEdit || !isParentOn} onToggle={on => ctx.onToggle(id, on)}/>
            <span className={styles.ruleText}>
                <Typography className={styles.itemLabel}>{label}</Typography>
                {label !== id && <Typography variant="caption" className={styles.muted}>{id}</Typography>}
            </span>
            {rule?.isNew && <Chip color="accent" label={t('new')}/>}
            <MoveMenu level={RULE} id={id} label={label} draft={ctx.draft} labels={ctx.labels} tabs={ctx.tabs} isDisabled={!ctx.canEdit} onMove={ctx.onMove}/>
        </li>
    );
};

RuleRow.propTypes = {
    id: PropTypes.string.isRequired,
    // The rule as contentRules gives it, or undefined
    rule: PropTypes.object,
    isParentOn: PropTypes.bool.isRequired,
    ctx: PropTypes.object.isRequired
};

/** The parts of the tab of another module, each with its switch: the layout stores the id `<tab>.<part>`. */
const PartRows = ({entry, draft, isDisabled, onToggle}) => {
    // The labels of a part are keys with their own namespace (`my-module:tab.part`)
    const {t: translate} = useGovernanceTranslation();
    return tabParts(entry).map(part => {
        const id = `${entry.key}.${part.id}`;
        const partLabel = translate(part.label);
        return (
            <li key={part.id} className={styles.rule} data-part={id}>
                <ItemSwitch label={partLabel} isOn={!draft.off.has(id)} isDisabled={isDisabled} onToggle={on => onToggle(id, on)}/>
                <Typography className={styles.itemLabel}>{partLabel}</Typography>
            </li>
        );
    });
};

PartRows.propTypes = {
    // A registry entry of drawerTabs (drawer/entries.js)
    entry: PropTypes.object.isRequired,
    draft: PropTypes.object.isRequired,
    isDisabled: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired
};

/**
 * One subdomain of a domain, with its rules, which open under it. A subdomain that holds the tab of another module
 * lists the parts of the tab instead, on the screen of a site.
 */
const SubdomainRow = ({place, subdomain, isParentOn, ctx}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const {t: tSettings} = useGovernanceTranslation('settings');
    const rowRef = useRef(null);
    const [isOpen, setOpen] = useState(false);
    const id = place.id;
    const label = subdomain?.label || id;
    const isOn = !ctx.draft.off.has(id);
    const need = NEEDS[id];
    const rulesId = `cg-rules-${id}`;
    const tab = ctx.tabs[id];
    const parts = tab && !ctx.isDefaults ? tabParts(tab) : [];
    let toggle = null;
    if (!tab || parts.length > 0) {
        toggle = (
            <Button variant="ghost"
                    size="small"
                    label={tab ? t('parts', {count: parts.length}) : t('rules', {count: place.rules.length})}
                    aria-expanded={isOpen}
                    aria-controls={rulesId}
                    onClick={() => setOpen(open => !open)}/>
        );
    }
    let list = null;
    if (isOpen && tab) {
        list = (
            <ul id={rulesId} className={styles.rules} aria-label={t('partsOf', {item: label})}>
                <PartRows entry={tab} draft={ctx.draft} isDisabled={!ctx.canEdit || !isParentOn || !isOn} onToggle={ctx.onToggle}/>
            </ul>
        );
    } else if (isOpen) {
        list = (
            <ul id={rulesId} className={styles.rules} aria-label={t('rulesOf', {item: label})}>
                {place.rules.length === 0 && <li className={styles.muted}><Typography variant="caption">{t('noRule')}</Typography></li>}
                {place.rules.map(ruleId => (
                    <RuleRow key={ruleId} id={ruleId} rule={ctx.rules[ruleId]} isParentOn={isParentOn && isOn} ctx={ctx}/>
                ))}
            </ul>
        );
    }
    return (
        <li ref={rowRef}
            className={`${styles.subdomain} ${ctx.dnd.isOver(SUBDOMAIN, id) ? styles.over : ''}`}
            data-subdomain={id}
            {...ctx.dnd.target(SUBDOMAIN, id)}
        >
            <div className={styles.row}>
                <DragHandle label={t('drag', {item: label})} dragProps={ctx.dnd.handle(SUBDOMAIN, id, rowRef)}/>
                <ItemSwitch label={label} isOn={isOn} isDisabled={!ctx.canEdit || !isParentOn} onToggle={on => ctx.onToggle(id, on)}/>
                <span className={styles.ruleText}>
                    <Typography weight="bold" className={styles.itemLabel}>{label}</Typography>
                    {subdomain?.help && <Typography variant="caption" className={styles.muted}>{subdomain.help}</Typography>}
                </span>
                {subdomain?.isNew && <Chip color="accent" label={t('new')}/>}
                {need && <Chip color="accent" label={tSettings(need)}/>}
                {toggle}
                <MoveMenu level={SUBDOMAIN} id={id} label={label} draft={ctx.draft} labels={ctx.labels} tabs={ctx.tabs} isDisabled={!ctx.canEdit} onMove={ctx.onMove}/>
            </div>
            {list}
        </li>
    );
};

SubdomainRow.propTypes = {
    // The subdomain in the draft: {id, rules}
    place: PropTypes.object.isRequired,
    // The subdomain as contentDomains gives it
    subdomain: PropTypes.object,
    isParentOn: PropTypes.bool.isRequired,
    ctx: PropTypes.object.isRequired
};

/** The card of one domain, with a row per subdomain. */
const DomainCard = ({place, domain, ctx}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const rowRef = useRef(null);
    const id = place.id;
    const label = domain?.label || id;
    const isPanelOn = !domain?.panel || !ctx.draft.off.has(domain.panel) || ctx.isDefaults;
    const isOn = !ctx.draft.off.has(id);
    return (
        <Paper className={`${styles.card} ${ctx.dnd.isOver(DOMAIN, id) ? styles.over : ''}`} data-domain={id}>
            <div ref={rowRef} className={styles.cardHeader} {...ctx.dnd.target(DOMAIN, id)}>
                <DragHandle label={t('drag', {item: label})} dragProps={ctx.dnd.handle(DOMAIN, id, rowRef)}/>
                <div className={styles.cardTitle}>
                    <Typography component="h2" variant="heading">{label}</Typography>
                    {domain?.help && <Typography variant="caption" className={styles.muted}>{domain.help}</Typography>}
                </div>
                {domain?.isNew && <Chip color="accent" label={t('new')}/>}
                <MoveMenu level={DOMAIN} id={id} label={label} draft={ctx.draft} labels={ctx.labels} tabs={ctx.tabs} isDisabled={!ctx.canEdit} onMove={ctx.onMove}/>
                <ItemSwitch label={label} isOn={isOn} isDisabled={!ctx.canEdit || !isPanelOn} onToggle={on => ctx.onToggle(id, on)}/>
            </div>
            <ul className={styles.subdomains} aria-label={t('subdomainsOf', {item: label})}>
                {place.subdomains.length === 0 && <li className={styles.muted}><Typography variant="caption">{t('noSubdomain')}</Typography></li>}
                {place.subdomains.map(subdomain => (
                    <SubdomainRow key={subdomain.id}
                                  place={subdomain}
                                  subdomain={ctx.subdomains[subdomain.id]}
                                  isParentOn={isPanelOn && isOn}
                                  ctx={ctx}/>
                ))}
            </ul>
        </Paper>
    );
};

DomainCard.propTypes = {
    // The domain in the draft: {id, subdomains}
    place: PropTypes.object.isRequired,
    // The domain as contentDomains gives it
    domain: PropTypes.object,
    ctx: PropTypes.object.isRequired
};

/** The switches of the panels of the drawer, at the top of the screen of a site. */
const PanelSwitches = ({panels, draft, canEdit, onToggle}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    // The labels of a panel are keys with their own namespace (`my-module:panel.label`)
    const {t: translate} = useGovernanceTranslation();
    return (
        <Paper className={styles.card} data-panels="true">
            <div className={styles.cardTitle}>
                <Typography component="h2" variant="heading">{t('panelsTitle')}</Typography>
                <Typography variant="caption" className={styles.muted}>{t('panelsHelp')}</Typography>
            </div>
            <ul className={styles.panels}>
                {panels.map(panel => {
                    const label = translate(panel.label);
                    return (
                        <li key={panel.key} className={styles.panel} data-panel={panel.key}>
                            <ItemSwitch label={t('panelSwitch', {panel: label})}
                                        isOn={!draft.off.has(panel.key)}
                                        isDisabled={!canEdit}
                                        onToggle={on => onToggle(panel.key, on)}/>
                            <span className={styles.ruleText}>
                                <Typography className={styles.itemLabel}>{label}</Typography>
                                {panel.settingsHelp && <Typography variant="caption" className={styles.muted}>{translate(panel.settingsHelp)}</Typography>}
                            </span>
                        </li>
                    );
                })}
            </ul>
        </Paper>
    );
};

PanelSwitches.propTypes = {
    // The registry entries of drawerPanels (drawer/entries.js)
    panels: PropTypes.array.isRequired,
    draft: PropTypes.object.isRequired,
    canEdit: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired
};

/**
 * The tabs that other modules add to the governance panel, with their parts, when no subdomain of the tree holds
 * them: the screen switches them as the registry of the drawer lists them.
 */
const OtherTabs = ({tabs, draft, canEdit, isPanelOn, onToggle}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    // The labels of a tab are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    return (
        <Paper className={styles.card} data-other-tabs="true">
            <div className={styles.cardTitle}>
                <Typography component="h2" variant="heading">{t('otherTabsTitle')}</Typography>
                <Typography variant="caption" className={styles.muted}>{t('otherTabsHelp')}</Typography>
            </div>
            <ul className={styles.subdomains}>
                {tabs.map(entry => {
                    const label = translate(entry.label);
                    const tabOn = !draft.off.has(entry.key);
                    return (
                        <li key={entry.key} className={styles.subdomain} data-tab={entry.key}>
                            <div className={styles.row}>
                                <ItemSwitch label={label} isOn={tabOn} isDisabled={!canEdit || !isPanelOn} onToggle={on => onToggle(entry.key, on)}/>
                                <span className={styles.ruleText}>
                                    <Typography weight="bold" className={styles.itemLabel}>{label}</Typography>
                                    <Typography variant="caption" className={styles.muted}>{translate(entry.settingsHelp || entry.help || entry.label)}</Typography>
                                </span>
                            </div>
                            {tabParts(entry).length > 0 && (
                                <ul className={styles.rules}>
                                    <PartRows entry={entry} draft={draft} isDisabled={!canEdit || !isPanelOn || !tabOn} onToggle={onToggle}/>
                                </ul>
                            )}
                        </li>
                    );
                })}
            </ul>
        </Paper>
    );
};

OtherTabs.propTypes = {
    // The registry entries of drawerTabs (drawer/entries.js) that no domain or subdomain holds
    tabs: PropTypes.array.isRequired,
    draft: PropTypes.object.isRequired,
    canEdit: PropTypes.bool.isRequired,
    // Whether the draft keeps the governance panel on
    isPanelOn: PropTypes.bool.isRequired,
    onToggle: PropTypes.func.isRequired
};

/** The screen once its data is read: the draft, its moves, and the save. */
const LayoutEditor = ({siteKey, data, isDefaults, canEdit, isReloading, title, status, onSaved}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const governance = data.contentGovernance;
    const panels = useMemo(() => (isDefaults ? [] : drawerPanels()), [isDefaults]);
    // The tabs of other modules, by key: a subdomain of the tree with the key of a tab holds it, and the screen lists
    // the parts of the tab under that subdomain; the other tabs go to their own card
    const tabs = useMemo(() => Object.fromEntries(drawerTabs().map(entry => [entry.key, entry])), []);
    const otherTabs = useMemo(() => {
        const tree = new Set(governance.contentDomains.flatMap(domain => [domain.id, ...domain.subdomains.map(s => s.id)]));
        return isDefaults ? [] : Object.values(tabs).filter(entry => !tree.has(entry.key));
    }, [governance, isDefaults, tabs]);
    // The ids that the layout switches off, those of the panels and of the tabs of other modules included
    const initial = useMemo(() => draftOf(governance.contentDomains, governance.contentRules, governance.siteLayout.off),
        [governance]);
    const [draft, setDraft] = useState(initial);
    const [confirmReset, setConfirmReset] = useState(false);
    const [save, {loading: saving, error: saveError}] = useMutation(SAVE_LAYOUT);
    const [reset, {loading: resetting, error: resetError}] = useMutation(RESET_LAYOUT);
    const dirty = !sameLayout(draft, initial);
    // A change while a save runs, or while the screen reads what it saved, would be lost when the draft starts again
    const editable = canEdit && !saving && !resetting && !isReloading;
    const dnd = useDragAndDrop(editable, setDraft, tabs);

    const lookups = useMemo(() => {
        const domains = Object.fromEntries(governance.contentDomains.map(domain => [domain.id, domain]));
        const subdomains = Object.fromEntries(governance.contentDomains.flatMap(domain => domain.subdomains).map(s => [s.id, s]));
        const rules = Object.fromEntries(governance.contentRules.map(rule => [rule.id, rule]));
        const labels = {};
        governance.contentDomains.forEach(domain => {
            labels[domain.id] = domain.label;
            domain.subdomains.forEach(subdomain => {
                labels[subdomain.id] = `${domain.label} › ${subdomain.label}`;
            });
        });
        return {domains, subdomains, rules, labels};
    }, [governance]);

    const onToggle = useCallback((id, on) => setDraft(d => toggle(d, id, on)), []);
    const onMove = useCallback((level, id, value) => {
        if (value === 'up' || value === 'down') {
            setDraft(d => shift(d, level, id, value === 'up' ? -1 : 1));
        } else if (value?.startsWith('into:')) {
            setDraft(d => move(d, level, id, {parent: value.slice('into:'.length), index: Number.MAX_SAFE_INTEGER}));
        }
    }, []);

    const submit = async () => {
        const {data: saved} = await save({variables: {siteKey, layout: JSON.stringify(toLayout(draft))}});
        if (saved) {
            onSaved('saved');
        }
    };
    const submitReset = async () => {
        setConfirmReset(false);
        const {data: done} = await reset({variables: {siteKey}});
        if (done) {
            onSaved('resetDone');
        }
    };

    const ctx = {draft, canEdit: editable, isDefaults, dnd, tabs, onToggle, onMove, ...lookups};
    const error = saveError || resetError;
    const actions = [];
    if (!isDefaults) {
        actions.push(<Button key="reset"
                             size="big"
                             variant="outlined"
                             label={t('reset')}
                             isDisabled={!editable || !governance.siteLayout.saved}
                             onClick={() => setConfirmReset(true)}/>);
    }
    actions.push(<Button key="save"
                         size="big"
                         color="accent"
                         label={t('save')}
                         isDisabled={!editable || !dirty}
                         onClick={() => submit().catch(() => null)}/>);

    return (
        <LayoutContent
            hasPadding
            header={<Header title={title} mainActions={actions}/>}
            content={(
                <div className={styles.content}>
                    {!canEdit && <Banner variant="info" title={t('readOnlyTitle')}>{t(isDefaults ? 'readOnlyDefaults' : 'readOnly')}</Banner>}
                    {error && <div role="alert"><Banner variant="danger" title={t('errorTitle')}>{t('error')}</Banner></div>}
                    {status && !dirty && <output><Banner variant="info" title={t(`${status}Title`)}>{t(status)}</Banner></output>}
                    {confirmReset && (
                        <div role="alertdialog" aria-label={t('resetConfirmTitle')} className={styles.confirm}>
                            <Banner variant="warning" title={t('resetConfirmTitle')}>{t('resetConfirm')}</Banner>
                            <div className={styles.confirmActions}>
                                <Button label={t('resetCancel')} variant="outlined" onClick={() => setConfirmReset(false)}/>
                                <Button label={t('resetDo')} color="danger" onClick={() => submitReset().catch(() => null)}/>
                            </div>
                        </div>
                    )}
                    <Typography>{t(isDefaults ? 'defaultsIntro' : 'intro')}</Typography>
                    {!isDefaults && (
                        <Typography variant="caption" className={styles.muted}>
                            {t(governance.siteLayout.saved ? 'stateSaved' : 'stateDefaults')}
                        </Typography>
                    )}
                    {panels.length > 0 && <PanelSwitches panels={panels} draft={draft} canEdit={editable} onToggle={onToggle}/>}
                    {draft.domains.map(domain => (
                        <DomainCard key={domain.id} place={domain} domain={lookups.domains[domain.id]} ctx={ctx}/>
                    ))}
                    {otherTabs.length > 0 && (
                        <OtherTabs tabs={otherTabs}
                                   draft={draft}
                                   canEdit={editable}
                                   isPanelOn={!draft.off.has(GOVERNANCE_PANEL)}
                                   onToggle={onToggle}/>
                    )}
                </div>
            )}
        />
    );
};

LayoutEditor.propTypes = {
    siteKey: PropTypes.string.isRequired,
    // The result of RULES_SCREEN
    data: PropTypes.object.isRequired,
    // True for the defaults of the system site
    isDefaults: PropTypes.bool.isRequired,
    canEdit: PropTypes.bool.isRequired,
    // True while the screen reads the layout again after a save or a reset
    isReloading: PropTypes.bool.isRequired,
    title: PropTypes.string.isRequired,
    // What the last save did, saved or reset, or null
    status: PropTypes.string,
    // (status): reads the layout again after a save or a reset
    onSaved: PropTypes.func.isRequired
};

/** Reads the hierarchy of a site and shows the editor, or why it cannot. */
const RulesLoader = ({siteKey, isDefaults, title}) => {
    const {t, i18n} = useGovernanceTranslation('rulesScreen');
    const language = (i18n?.language || 'en').slice(0, 2).toLowerCase();
    const {data, loading, error, refetch} = useQuery(RULES_SCREEN, {
        variables: {siteKey, language},
        fetchPolicy: 'network-only'
    });
    // The draft starts again from what the server stored after each save
    const [version, setVersion] = useState(0);
    const [status, setStatus] = useState(null);
    const [isReloading, setReloading] = useState(false);
    const onSaved = useCallback(done => {
        setReloading(true);
        refetch().then(() => {
            setStatus(done);
            setVersion(v => v + 1);
        }).catch(() => null).finally(() => setReloading(false));
    }, [refetch]);

    if (loading && !data) {
        return <LayoutContent hasPadding header={<Header title={title}/>} content={<Loader size="big"/>}/>;
    }
    if (error || !data?.contentGovernance) {
        return (
            <LayoutContent hasPadding
                           header={<Header title={title}/>}
                           content={<Banner variant="danger" title={t('errorTitle')}>{t('loadError')}</Banner>}/>
        );
    }
    // The route of the defaults shows only to a server administrator, and the server checks the permission again
    const canEdit = isDefaults || Boolean(data.contentGovernance.siteSettings?.canEdit);
    return (
        <LayoutEditor key={`${siteKey}-${version}`}
                      siteKey={siteKey}
                      data={data}
                      isDefaults={isDefaults}
                      canEdit={canEdit}
                      isReloading={isReloading}
                      title={title}
                      status={status}
                      onSaved={onSaved}/>
    );
};

RulesLoader.propTypes = {
    siteKey: PropTypes.string.isRequired,
    isDefaults: PropTypes.bool.isRequired,
    title: PropTypes.string.isRequired
};

/**
 * jContent > Additional > Rules & domains: how a site arranges the domains, the subdomains and the rules of the
 * drawer, and which ones and which panels it switches off. The route hides the entry from users without site-admin;
 * the component checks the module and the permission again, and the server checks site-admin on every save.
 *
 * @param {string} siteKey the site
 */
export const RulesScreen = ({siteKey}) => {
    const {t} = useGovernanceTranslation('rulesScreen');
    const title = t('title');
    const {checksResult, loading} = useNodeChecks({path: `/sites/${siteKey}`},
        {requireModuleInstalledOnSite: [MODULE], requiredPermission: ['site-admin']});
    if (loading) {
        return null;
    }
    if (!checksResult) {
        return <LayoutContent hasPadding header={<Header title={title}/>} content={<Typography>{t('notEnabled')}</Typography>}/>;
    }
    return <RulesLoader key={siteKey} siteKey={siteKey} isDefaults={false} title={title}/>;
};

RulesScreen.propTypes = {
    siteKey: PropTypes.string.isRequired
};

/**
 * Administration > Server settings > Rules & domains: the defaults of every site, the order and the default switches
 * of the tree of the system site, for server administrators.
 */
export const DefaultRulesScreen = () => {
    const {t} = useGovernanceTranslation('rulesScreen');
    return <RulesLoader siteKey={SYSTEM_SITE} isDefaults title={t('defaultsTitle')}/>;
};
