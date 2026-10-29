import React from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {EmptyData, Header, LayoutContent, Loader} from '@jahia/moonstone';
import {useGovernanceTranslation} from './i18n';
import {useSiteSettings} from './useSiteSettings';

/**
 * Renders a site dashboard only while the site keeps its section switched on. The menu entry is
 * registered once for every site, so a site that switched the section off still lists it: the
 * entry then says so instead of showing the dashboard. The server refuses the dashboard's calls too.
 *
 * @param {string} section the section id (`geo`, `compliance`)
 * @param {Function} children render function, given `isOn(tabId)` for that section
 */
export const SectionGate = ({section, title, children}) => {
    const {t} = useGovernanceTranslation('common');
    const siteKey = useSelector(state => state.site);
    const {settings, isOn, loading} = useSiteSettings(siteKey);

    if (loading || !settings) {
        return <LayoutContent hasPadding header={<Header title={title}/>} content={<Loader size="big"/>}/>;
    }
    if (!isOn(section)) {
        return (
            <LayoutContent hasPadding
                           header={<Header title={title}/>}
                           content={<EmptyData title={t('sectionOff.title')} message={t('sectionOff.message')}/>}/>
        );
    }
    return children(tab => isOn(section, tab));
};

SectionGate.propTypes = {
    section: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    children: PropTypes.func.isRequired
};
