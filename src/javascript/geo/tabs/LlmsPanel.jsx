import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useGovernanceTranslation} from '../../common/i18n';
import {Banner, Button, Loader, Textarea, Typography} from '@jahia/moonstone';
import {readLlms} from '../api/siteFiles';
import {scanStatus} from '../api/siteScore';
import {Actions, Card, Figure, Figures, Panel, PanelHeader} from '../dashboard/Layout';
import styles from './Tabs.module.css';

/**
 * The site's llms.txt as the llms module serves it, read only. The file is written in that module's
 * own screen (Additional, SEO, Llms.txt); this tab shows what it says and which of its links no
 * longer lead to a published page, from the last scan.
 */
export const LlmsPanel = ({path, siteKey, language}) => {
    const {t} = useGovernanceTranslation('geo');
    const [file, setFile] = useState(null);
    const [error, setError] = useState(false);
    const [freshness, setFreshness] = useState(null);

    useEffect(() => {
        let live = true;
        setFile(null);
        setError(false);
        readLlms({path, language})
            .then(f => live && setFile(f))
            .catch(() => live && setError(true));
        scanStatus({path, language})
            .then(s => live && setFreshness(s.llms))
            .catch(() => {});
        return () => {
            live = false;
        };
    }, [path, language]);

    const editorUrl = `${globalThis.contextJsParameters?.contextPath || ''}/jahia/jcontent/${siteKey}/${language}/apps/siteSettingsSeo/llms`;
    const stats = file && file.content ? countOf(file.content) : null;

    return (
        <Panel>
            <PanelHeader title={t('files.llms.view.title')} intro={t('files.llms.view.intro')}/>

            {error && <Banner variant="danger" title={t('files.llms.view.errorTitle')}>{t('files.llms.view.error')}</Banner>}
            {!file && !error && <Loader size="big"/>}

            {file && !file.moduleEnabled && (
                <Banner variant="info" title={t('files.llms.view.moduleOffTitle')}>
                    {t('files.llms.view.moduleOff')}
                </Banner>
            )}

            {file && file.moduleEnabled && (
                <>
                    {/*
                      * A stale llms.txt fails silently: nothing errors, an assistant is just handed
                      * links to pages that moved or were removed. The last scan names them.
                      */}
                    {freshness && freshness.outdated && (
                        <Banner variant="warning" title={t('files.llms.stale.title')}>
                            {t('files.llms.stale.summary', {count: (freshness.stale || []).length})}
                            <ul className={styles.checkList}>
                                {(freshness.stale || []).slice(0, 10).map(r => (
                                    <li key={r.path} className={styles.checkItem}>
                                        <span className={styles.checkText}>
                                            <Typography variant="caption" className={styles.checkFix}>
                                                {r.path} · {t(`files.llms.stale.why.${r.why}`)}
                                            </Typography>
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </Banner>
                    )}
                    {freshness && freshness.present && !freshness.outdated && (
                        <Banner variant="info" title={t('files.llms.fresh.title')}>
                            {t('files.llms.fresh.summary', {count: freshness.listed})}
                        </Banner>
                    )}
                    {!file.content && (
                        <Banner variant="info" title={t('files.llms.view.emptyTitle')}>
                            {t('files.llms.view.empty')}
                        </Banner>
                    )}
                    {file.placeholder && (
                        <Banner variant="warning" title={t('files.llms.view.placeholderTitle')}>
                            {t('files.llms.view.placeholder')}
                        </Banner>
                    )}

                    <Card title={t('files.llms.view.published')}>
                        {stats && (
                            <Figures>
                                <Figure label={t('files.llms.links')} value={stats.links}/>
                                <Figure label={t('files.llms.sections')} value={stats.sections}/>
                            </Figures>
                        )}
                        {file.content && (
                            <Textarea
                                isReadOnly
                                isResizable
                                id="contentGovernanceLlmsText"
                                aria-label={t('files.llms.view.published')}
                                className={styles.genText}
                                value={file.content}
                                spellCheck="false"
                                onChange={() => {}}
                            />
                        )}
                        <Actions>
                            <Button
                                size="big"
                                variant="outlined"
                                label={t('files.llms.view.edit')}
                                onClick={() => globalThis.location.assign(editorUrl)}
                            />
                        </Actions>
                    </Card>
                </>
            )}
        </Panel>
    );
};

/** Counted from the published text so the figures always match what is shown. */
function countOf(md) {
    return {
        links: (md.match(/^\s*- \[/gm) || []).length,
        sections: (md.match(/^## /gm) || []).length
    };
}

LlmsPanel.propTypes = {
    path: PropTypes.string.isRequired,
    siteKey: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired
};
