import React, {useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useMutation} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {Banner, Button, Chip, Header, LayoutContent, Loader, Paper, Switch, Tab, TabItem, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {GOVERNANCE_PANEL, MODULE} from '../common/module';
import {useSiteSettings} from '../common/useSiteSettings';
import {SAVE_SITE_SETTINGS} from '../common/settings.gql';
import {CitationsSettings} from '../geo/settings/CitationsSettings';
import {drawerPanels, drawerTabs, tabParts} from '../drawer/entries';
import styles from './SettingsPanel.module.css';

const PANEL_TABS = ['features', 'botrank'];

/** What a part of a tab needs, as a chip: a BotRank connection. */
const NEEDS = {botRank: () => 'needsBotRank'};
const NEEDS_COLOR = {needsBotRank: 'accent'};

/** The switched-off ids of a draft: a tab off hides its parts, which keep their own state. */
const toggle = (draft, id, on) => {
    const next = new Set(draft);
    if (on) {
        next.delete(id);
    } else {
        next.add(id);
    }
    return next;
};

/**
 * The card of one tab of the governance panel: a switch for the tab, then a switch for each of its
 * parts. They stay as they are, greyed out, while the panel is off.
 */
const TabCard = ({entry, draft, settings, isPanelOn, onToggle}) => {
    const {t} = useGovernanceTranslation('settings');
    // The labels of a tab are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    const tabOn = !draft.has(entry.key);
    const label = translate(entry.label);
    const parts = tabParts(entry);
    return (
        <Paper className={styles.card} data-tab={entry.key}>
            <div className={styles.cardHeader}>
                <div>
                    <Typography component="h3" variant="heading">{label}</Typography>
                    <Typography variant="caption" className={styles.muted}>
                        {translate(entry.settingsHelp || entry.help || entry.label)}
                    </Typography>
                </div>
                <label className={styles.switchLabel}>
                    <Switch checked={tabOn}
                            isDisabled={!settings.canEdit || !isPanelOn}
                            aria-label={t('sectionSwitch', {section: label})}
                            onChange={(e, value, checked) => onToggle(entry.key, checked)}/>
                    <Typography variant="caption">{tabOn ? t('on') : t('off')}</Typography>
                </label>
            </div>
            {parts.length > 0 && (
                <ul className={styles.tabs}>
                    {parts.map(part => {
                        const id = `${entry.key}.${part.id}`;
                        const partLabel = translate(part.label);
                        const need = NEEDS[part.needs]?.(settings);
                        return (
                            <li key={part.id} className={styles.tab}>
                                <Switch checked={!draft.has(id)}
                                        isDisabled={!settings.canEdit || !isPanelOn || !tabOn}
                                        aria-label={partLabel}
                                        onChange={(e, value, checked) => onToggle(id, checked)}/>
                                <Typography className={styles.tabLabel}>{partLabel}</Typography>
                                {need && <Chip color={NEEDS_COLOR[need]} label={t(need)}/>}
                                {part.help && <Typography variant="caption" className={styles.tabHelp}>{translate(part.help)}</Typography>}
                            </li>
                        );
                    })}
                </ul>
            )}
        </Paper>
    );
};

TabCard.propTypes = {
    // A registry entry of drawerTabs (drawer/entries.js)
    entry: PropTypes.object.isRequired,
    // The ids switched off in the draft
    draft: PropTypes.instanceOf(Set).isRequired,
    settings: PropTypes.object.isRequired,
    // Whether the draft keeps the governance panel on
    isPanelOn: PropTypes.bool.isRequired,
    // (id, on): switches a tab or a part on or off in the draft
    onToggle: PropTypes.func.isRequired
};

/**
 * One panel of the drawer: its switch, and under it what the panel holds. A panel switched off
 * loses its button in the header of jContent.
 */
const PanelSettings = ({entry, draft, settings, onToggle, children}) => {
    const {t} = useGovernanceTranslation('settings');
    // The labels of a panel are keys with their own namespace (`my-module:panel.label`)
    const {t: translate} = useGovernanceTranslation();
    const on = !draft.has(entry.key);
    const label = translate(entry.label);
    return (
        <section className={styles.panel} data-panel={entry.key} aria-label={label}>
            <Paper className={styles.card}>
                <div className={styles.cardHeader}>
                    <div>
                        <Typography component="h2" variant="heading">{label}</Typography>
                        {entry.settingsHelp && (
                            <Typography variant="caption" className={styles.muted}>{translate(entry.settingsHelp)}</Typography>
                        )}
                    </div>
                    <label className={styles.switchLabel}>
                        <Switch checked={on}
                                isDisabled={!settings.canEdit}
                                aria-label={t('panelSwitch', {panel: label})}
                                onChange={(e, value, checked) => onToggle(entry.key, checked)}/>
                        <Typography variant="caption">{on ? t('on') : t('off')}</Typography>
                    </label>
                </div>
            </Paper>
            {children}
        </section>
    );
};

PanelSettings.propTypes = {
    // A registry entry of drawerPanels (drawer/entries.js)
    entry: PropTypes.object.isRequired,
    draft: PropTypes.instanceOf(Set).isRequired,
    settings: PropTypes.object.isRequired,
    onToggle: PropTypes.func.isRequired,
    children: PropTypes.node
};

const FeaturesScreen = ({settings, toolbarLeft, title, onSaved}) => {
    const {t} = useGovernanceTranslation('settings');
    // Every panel and tab of the drawer, the module's own and those other modules add (drawer/entries.js)
    const panels = useMemo(() => drawerPanels(), []);
    const tabs = useMemo(() => drawerTabs(), []);
    const [draft, setDraft] = useState(() => new Set(settings.disabled));
    const [save, {loading: saving, error}] = useMutation(SAVE_SITE_SETTINGS);
    const [saved, setSaved] = useState(false);
    const dirty = useMemo(() => {
        const stored = new Set(settings.disabled);
        return stored.size !== draft.size || [...draft].some(id => !stored.has(id));
    }, [settings.disabled, draft]);

    useEffect(() => setDraft(new Set(settings.disabled)), [settings.disabled]);

    const submit = useCallback(async () => {
        setSaved(false);
        const {data} = await save({variables: {siteKey: settings.siteKey, disabled: [...draft]}});
        if (data) {
            setSaved(true);
            onSaved();
        }
    }, [save, settings.siteKey, draft, onSaved]);

    const onToggle = useCallback((id, on) => setDraft(d => toggle(d, id, on)), []);

    return (
        <LayoutContent
            hasPadding
            header={(
                <Header title={title}
                        toolbarLeft={toolbarLeft}
                        mainActions={[
                            <Button key="save"
                                    size="big"
                                    color="accent"
                                    label={t('save')}
                                    isDisabled={!settings.canEdit || !dirty || saving}
                                    onClick={() => submit().catch(() => null)}/>
                        ]}/>
            )}
            content={(
                <div className={styles.content}>
                    {!settings.canEdit && <Banner variant="info" title={t('readOnlyTitle')}>{t('readOnly')}</Banner>}
                    {error && <div role="alert"><Banner variant="danger" title={t('errorTitle')}>{t('error')}</Banner></div>}
                    {saved && !dirty && <output><Banner variant="info" title={t('savedTitle')}>{t('saved')}</Banner></output>}
                    <Typography>{t('intro')}</Typography>
                    {panels.map(panel => (
                        <PanelSettings key={panel.key} entry={panel} draft={draft} settings={settings} onToggle={onToggle}>
                            {panel.key === GOVERNANCE_PANEL && tabs.map(entry => (
                                <TabCard key={entry.key}
                                         entry={entry}
                                         draft={draft}
                                         settings={settings}
                                         isPanelOn={!draft.has(GOVERNANCE_PANEL)}
                                         onToggle={onToggle}/>
                            ))}
                        </PanelSettings>
                    ))}
                </div>
            )}
        />
    );
};

FeaturesScreen.propTypes = {
    settings: PropTypes.object.isRequired,
    toolbarLeft: PropTypes.node,
    title: PropTypes.string.isRequired,
    onSaved: PropTypes.func.isRequired
};

/**
 * Site Settings > Content governance: which panels of the drawer, tabs of the governance panel and
 * parts of tabs the site uses (every panel and tab of the drawer's registry, those of other modules
 * included), and the site's BotRank connection. The route hides the entry from users
 * without site-admin; the component checks the module and the permission again, and the server
 * checks site-admin on every save.
 */
export const SettingsPanel = () => {
    const {t} = useGovernanceTranslation('settings');
    const siteKey = useSelector(state => state.site);
    const [tab, setTab] = useState('features');
    const {checksResult, loading: checking} = useNodeChecks({path: `/sites/${siteKey}`},
        {requireModuleInstalledOnSite: [MODULE], requiredPermission: ['site-admin']});
    const {settings, loading, error, refetch} = useSiteSettings(checksResult ? siteKey : null);
    const title = t('title');

    // Tabs in the header wrap under the content when they do not fit; this box scrolls instead.
    const toolbarLeft = (
        <div className={styles.headerTabs}>
            <Tab className={styles.headerTabsInner}>
                {PANEL_TABS.map(id => (
                    <TabItem key={id} label={t(`panelTab.${id}`)} isSelected={tab === id} onClick={() => setTab(id)}/>
                ))}
            </Tab>
        </div>
    );

    if (checking) {
        return null;
    }
    if (!checksResult) {
        return <LayoutContent hasPadding header={<Header title={title}/>} content={<Typography>{t('notEnabled')}</Typography>}/>;
    }
    if (tab === 'botrank') {
        return <CitationsSettings title={title} toolbarLeft={toolbarLeft}/>;
    }
    if (loading || (!settings && !error)) {
        return <LayoutContent hasPadding header={<Header title={title} toolbarLeft={toolbarLeft}/>} content={<Loader size="big"/>}/>;
    }
    if (error || !settings) {
        return (
            <LayoutContent hasPadding
                           header={<Header title={title} toolbarLeft={toolbarLeft}/>}
                           content={<Banner variant="danger" title={t('errorTitle')}>{t('loadError')}</Banner>}/>
        );
    }
    return <FeaturesScreen key={siteKey} settings={settings} toolbarLeft={toolbarLeft} title={title} onSaved={refetch}/>;
};
