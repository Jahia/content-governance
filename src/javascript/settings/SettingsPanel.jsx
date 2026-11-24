import React from 'react';
import {useSelector} from 'react-redux';
import {useNodeChecks} from '@jahia/data-helper';
import {Header, LayoutContent, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {MODULE} from '../common/module';
import {CitationsSettings} from '../geo/settings/CitationsSettings';

/**
 * Site Settings > Content governance: the BotRank connection of the site. The switches of the drawer moved to
 * jContent > Additional > Rules & domains (layout/RulesScreen.jsx). The route hides the entry from users without
 * site-admin; the component checks the module and the permission again, and the server checks them on every save.
 */
export const SettingsPanel = () => {
    const {t} = useGovernanceTranslation('settings');
    const siteKey = useSelector(state => state.site);
    const {checksResult, loading} = useNodeChecks({path: `/sites/${siteKey}`},
        {requireModuleInstalledOnSite: [MODULE], requiredPermission: ['site-admin']});
    const title = t('title');

    if (loading) {
        return null;
    }
    if (!checksResult) {
        return <LayoutContent hasPadding header={<Header title={title}/>} content={<Typography>{t('notEnabled')}</Typography>}/>;
    }
    return <CitationsSettings key={siteKey} title={title}/>;
};
