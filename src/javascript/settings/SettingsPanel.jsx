import React, {useCallback, useEffect, useMemo, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useMutation} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {Banner, Button, Chip, Header, LayoutContent, Loader, Paper, Switch, Tab, TabItem, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {MODULE} from '../common/module';
import {useSiteSettings} from '../common/useSiteSettings';
import {SAVE_SITE_SETTINGS} from '../common/settings.gql';
import {CitationsSettings} from '../geo/settings/CitationsSettings';
import {drawerTabs, tabParts} from '../drawer/entries';
import styles from './SettingsPanel.module.css';

const PANEL_TABS = ['features', 'ai', 'botrank'];

/** What a part of a tab needs, as a chip: the AI model while none answers, or a BotRank connection. */
const NEEDS = {ai: settings => (settings.ai.configured ? null : 'needsAi'), botRank: () => 'needsBotRank'};
const NEEDS_COLOR = {needsAi: 'warning', needsBotRank: 'accent'};

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

const FeaturesScreen = ({settings, toolbarLeft, title, onSaved}) => {
    const {t} = useGovernanceTranslation('settings');
    // The labels of a tab are keys with their own namespace (`my-module:tab.label`)
    const {t: translate} = useGovernanceTranslation();
    // Every tab of the drawer, the module's own and those other modules add (drawer/entries.js)
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
                    {tabs.map(entry => {
                        const sectionOn = !draft.has(entry.key);
                        const label = translate(entry.label);
                        const parts = tabParts(entry);
                        return (
                            <Paper key={entry.key} className={styles.card} data-tab={entry.key}>
                                <div className={styles.cardHeader}>
                                    <div>
                                        <Typography component="h3" variant="heading">{label}</Typography>
                                        <Typography variant="caption" className={styles.muted}>
                                            {translate(entry.settingsHelp || entry.help || entry.label)}
                                        </Typography>
                                    </div>
                                    <label className={styles.switchLabel}>
                                        <Switch checked={sectionOn}
                                                isDisabled={!settings.canEdit}
                                                aria-label={t('sectionSwitch', {section: label})}
                                                onChange={(e, value, checked) => setDraft(d => toggle(d, entry.key, checked))}/>
                                        <Typography variant="caption">{sectionOn ? t('on') : t('off')}</Typography>
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
                                                            isDisabled={!settings.canEdit || !sectionOn}
                                                            aria-label={partLabel}
                                                            onChange={(e, value, checked) => setDraft(d => toggle(d, id, checked))}/>
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
                    })}
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

const AiScreen = ({settings, toolbarLeft, title}) => {
    const {t} = useGovernanceTranslation('settings');
    const {ai} = settings;
    const noAccess = ai.reason === 'noAccess';
    let chip = <Chip color="success" label={t('ai.configured')}/>;
    if (!ai.configured) {
        chip = <Chip color="warning" label={noAccess ? t('ai.noAccess') : t('ai.notConfigured')}/>;
    }

    return (
        <LayoutContent
            hasPadding
            header={<Header title={title} toolbarLeft={toolbarLeft}/>}
            content={(
                <div className={styles.content}>
                    <Paper className={styles.card}>
                        <div className={styles.cardHeader}>
                            <Typography component="h3" variant="heading">{t('ai.title')}</Typography>
                            {chip}
                        </div>
                        {ai.configured ? (
                            <dl className={styles.facts}>
                                <dt>{t('ai.provider')}</dt>
                                <dd>{ai.provider}</dd>
                                <dt>{t('ai.model')}</dt>
                                <dd>{ai.model}</dd>
                                <dt>{t('ai.images')}</dt>
                                <dd>{ai.images ? t('ai.yes') : t('ai.no')}</dd>
                            </dl>
                        ) : (
                            <Typography>{noAccess ? t('ai.howToAccess') : t('ai.howTo')}</Typography>
                        )}
                        <Typography variant="caption" className={styles.muted}>{t('ai.servedBy')}</Typography>
                        <Typography variant="caption" className={styles.muted}>{t('ai.uses')}</Typography>
                    </Paper>
                </div>
            )}
        />
    );
};

AiScreen.propTypes = {
    settings: PropTypes.object.isRequired,
    toolbarLeft: PropTypes.node,
    title: PropTypes.string.isRequired
};

/**
 * Site Settings > Content governance: which tabs of the drawer, and parts of tabs, the site uses
 * (every tab of the drawer's registry, those of other modules included), the shared model the
 * AI features run on, and the site's BotRank connection. The route hides the entry from users
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
    return tab === 'ai'
        ? <AiScreen settings={settings} toolbarLeft={toolbarLeft} title={title}/>
        : <FeaturesScreen key={siteKey} settings={settings} toolbarLeft={toolbarLeft} title={title} onSaved={refetch}/>;
};
