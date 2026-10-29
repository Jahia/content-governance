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
import styles from './SettingsPanel.module.css';

const PANEL_TABS = ['features', 'ai', 'botrank'];
const LLM_CFG = 'digital-factory-data/karaf/etc/org.jahia.community.contentgovernance.llm.cfg';

/** The switched-off ids of a draft: a section off hides its tabs, which keep their own state. */
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
    const {t: tDrawer} = useGovernanceTranslation('drawer');
    const {t: tAudit} = useGovernanceTranslation('audit');
    const {t: tGeo} = useGovernanceTranslation('geo');
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

    // The labels the drawer shows, so the switch names what the editor sees.
    const tabLabel = (section, tab) => (section === 'geo' ? tGeo(`${tab}.tab`) : tAudit(`tabs.${tab}`));
    // The same text as the tab's tooltip in the drawer.
    const tabHelp = (section, tab) => (section === 'geo' ? tGeo(`tabHelp.${tab}`) : tAudit(`tabHelp.${tab}`));

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
                    {saved && !dirty && <div role="status"><Banner variant="info" title={t('savedTitle')}>{t('saved')}</Banner></div>}
                    <Typography>{t('intro')}</Typography>
                    {settings.sections.map(section => {
                        const sectionOn = !draft.has(section.id);
                        return (
                            <Paper key={section.id} className={styles.card}>
                                <div className={styles.cardHeader}>
                                    <div>
                                        <Typography component="h3" variant="heading">{tDrawer(`section.${section.id}`)}</Typography>
                                        <Typography variant="caption" className={styles.muted}>{t(`sectionHelp.${section.id}`)}</Typography>
                                    </div>
                                    <label className={styles.switchLabel}>
                                        <Switch checked={sectionOn}
                                                isDisabled={!settings.canEdit}
                                                aria-label={t('sectionSwitch', {section: tDrawer(`section.${section.id}`)})}
                                                onChange={(e, value, checked) => setDraft(d => toggle(d, section.id, checked))}/>
                                        <Typography variant="caption">{sectionOn ? t('on') : t('off')}</Typography>
                                    </label>
                                </div>
                                {section.tabs.length > 0 && (
                                    <ul className={styles.tabs}>
                                        {section.tabs.map(tab => {
                                            const id = `${section.id}.${tab.id}`;
                                            const needsAi = id === 'audit.ai';
                                            const needsBotRank = id === 'geo.citations';
                                            return (
                                                <li key={tab.id} className={styles.tab}>
                                                    <Switch checked={!draft.has(id)}
                                                            isDisabled={!settings.canEdit || !sectionOn}
                                                            aria-label={tabLabel(section.id, tab.id)}
                                                            onChange={(e, value, checked) => setDraft(d => toggle(d, id, checked))}/>
                                                    <Typography className={styles.tabLabel}>{tabLabel(section.id, tab.id)}</Typography>
                                                    {needsAi && !settings.ai.configured && <Chip color="warning" label={t('needsAi')}/>}
                                                    {needsBotRank && <Chip color="accent" label={t('needsBotRank')}/>}
                                                    <Typography variant="caption" className={styles.tabHelp}>{tabHelp(section.id, tab.id)}</Typography>
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
    return (
        <LayoutContent
            hasPadding
            header={<Header title={title} toolbarLeft={toolbarLeft}/>}
            content={(
                <div className={styles.content}>
                    <Paper className={styles.card}>
                        <div className={styles.cardHeader}>
                            <Typography component="h3" variant="heading">{t('ai.title')}</Typography>
                            {ai.configured ? <Chip color="success" label={t('ai.configured')}/> : <Chip color="warning" label={t('ai.notConfigured')}/>}
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
                            <Typography>{t('ai.howTo')}</Typography>
                        )}
                        <Typography variant="caption" className={styles.muted}>{t('ai.file')}</Typography>
                        <code className={styles.code}>{LLM_CFG}</code>
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
 * Site Settings > Content governance: which sections and tabs the site uses, the shared model the
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
