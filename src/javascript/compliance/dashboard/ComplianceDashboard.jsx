import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useApolloClient} from '@apollo/client';
import {useGovernanceTranslation} from '../../common/i18n';
import {
    Banner,
    Button,
    Check,
    Chip,
    DataTable,
    Download,
    Edit,
    EmptyData,
    Header,
    Information,
    LayoutContent,
    Loader,
    Pagination,
    Reload,
    Typography,
    Warning
} from '@jahia/moonstone';
import {SCAN_SITE} from './scanSite.gql';
import {exportCsv, exportJson} from './exportReport';
import styles from './ComplianceDashboard.module.css';

const PER_PAGE_OPTIONS = [10, 20, 50];

const languageName = (code, uiLanguage) => {
    try {
        return new Intl.DisplayNames([uiLanguage || 'en'], {type: 'language'}).of(code.replace('_', '-')) || code;
    } catch {
        return code;
    }
};

const editInContentEditor = (uuid, site, language) => {
    window.CE_API?.edit?.({uuid, site, lang: language, isFullscreen: false});
};

/** Page Builder URL of a page: the jContent pages route, under the context path. */
const pageBuilderUrl = (path, siteKey, language) => {
    const relative = path.slice(`/sites/${siteKey}`.length).split('/').map(encodeURIComponent).join('/');
    return `${globalThis.contextJsParameters?.contextPath || ''}/jahia/jcontent/${siteKey}/${language}/pages${relative}`;
};

const Excerpt = ({issue}) => (
    <Typography variant="caption" className={styles.wrap}>
        {issue.before}<mark className={styles.mark}>{issue.matchedText}</mark>{issue.after}
    </Typography>
);

Excerpt.propTypes = {issue: PropTypes.object.isRequired};

const Summary = ({report, language}) => {
    const {t} = useGovernanceTranslation('compliance');
    const locations = report.locations.length;
    const uses = report.locations.reduce((sum, l) => sum + l.issueCount, 0);
    let chip;
    let text;
    if (report.status === 'SAFE') {
        chip = <Chip color="success" icon={<Check/>} label={t('status.safe')}/>;
        text = t('dashboard.summary.safe', {language});
    } else if (report.status === 'ISSUES') {
        chip = <Chip color="danger" icon={<Warning/>} label={t('status.issues')}/>;
        text = t('dashboard.summary.issues', {count: uses, locations, language});
    } else {
        chip = <Chip color="warning" icon={<Information/>} label={t('status.noGlossary')}/>;
        text = t('summary.noGlossary', {language});
    }
    return (
        <div className={styles.summary}>
            <div className={styles.inline}>
                {chip}
                <Typography>{text}</Typography>
            </div>
            {report.status !== 'NO_GLOSSARY' && (
                <Typography variant="caption" className={styles.muted}>
                    {t('dashboard.summary.read', {pages: report.scannedPages, contents: report.scannedContents})}
                </Typography>
            )}
        </div>
    );
};

Summary.propTypes = {
    report: PropTypes.object.isRequired,
    language: PropTypes.string.isRequired
};

const TermsTable = ({terms, glossaryTitles}) => {
    const {t} = useGovernanceTranslation('compliance');
    const columns = [
        {
            key: 'term',
            label: t('dashboard.terms.term'),
            width: '22%',
            render: ({value, data}) => (
                <div className={styles.cellText}>
                    <Typography weight="semiBold" className={styles.wrap}>{value}</Typography>
                    {glossaryTitles.size > 1 && (
                        <Typography variant="caption" className={`${styles.wrap} ${styles.muted}`}>
                            {glossaryTitles.get(data.glossaryPath) || data.glossaryPath}
                        </Typography>
                    )}
                </div>
            )
        },
        {
            key: 'reason',
            label: t('dashboard.terms.reason'),
            width: '48%',
            render: ({data}) => (
                <div className={styles.cellText}>
                    {data.reason && <Typography variant="caption" className={styles.wrap}>{data.reason}</Typography>}
                    {data.alternative && (
                        <Typography variant="caption" className={styles.wrap}>
                            <Typography component="span" variant="caption" weight="bold">{t('issue.alternative')}</Typography>
                            {' '}{data.alternative}
                        </Typography>
                    )}
                </div>
            )
        },
        {key: 'uses', label: t('dashboard.terms.uses'), width: '15%', render: ({value}) => <Typography>{value}</Typography>},
        {key: 'locations', label: t('dashboard.terms.locations'), width: '15%', render: ({value}) => <Typography>{value}</Typography>}
    ];
    const data = terms.map(term => ({id: `${term.glossaryPath}|${term.term}`, ...term}));
    return (
        <div className={styles.tableScroll}>
            <DataTable primaryKey="id" data={data} columns={columns} enablePagination={false}/>
        </div>
    );
};

TermsTable.propTypes = {
    terms: PropTypes.array.isRequired,
    glossaryTitles: PropTypes.instanceOf(Map).isRequired
};

const LocationsTable = ({locations, glossaryTitles, siteKey, language}) => {
    const {t} = useGovernanceTranslation('compliance');
    const [page, setPage] = useState(1);
    const [perPage, setPerPage] = useState(PER_PAGE_OPTIONS[0]);
    const lastPage = Math.max(1, Math.ceil(locations.length / perPage));
    const current = Math.min(page, lastPage);

    // A location row and its issues as sub-rows. Every column key points at the row itself: the cells
    // render from it, whatever the row's kind.
    const data = useMemo(() => locations.slice((current - 1) * perPage, current * perPage).map(location => {
        const row = {id: location.uuid, kind: 'location', location};
        const subRows = location.issues.map((issue, index) => {
            const sub = {id: `${location.uuid}-${index}`, kind: 'issue', location, issue};
            return {...sub, place: sub, type: sub, detail: sub, actions: sub};
        });
        return {...row, place: row, type: row, detail: row, actions: row, subRows};
    }), [locations, current, perPage]);

    const columns = [
        {
            key: 'place',
            label: t('dashboard.locations.place'),
            width: '38%',
            render: ({value}) => (value.kind === 'location' ? (
                <div className={styles.cellText}>
                    <Typography weight="semiBold" className={styles.wrap}>{value.location.name || value.location.path}</Typography>
                    <Typography variant="caption" className={`${styles.wrap} ${styles.muted}`}>{value.location.path}</Typography>
                </div>
            ) : (
                <div className={styles.cellText}>
                    <Typography variant="caption" weight="bold" className={styles.wrap}>
                        {value.issue.nodeName} · {value.issue.propertyLabel || value.issue.propertyName}
                    </Typography>
                    <Excerpt issue={value.issue}/>
                </div>
            ))
        },
        {
            key: 'type',
            label: t('dashboard.locations.type'),
            width: '17%',
            render: ({value}) => {
                if (value.kind !== 'location') {
                    return <Chip color="danger" label={value.issue.term}/>;
                }
                const kindLabel = t(`dashboard.kind.${value.location.kind.toLowerCase()}`);
                // A page's type label is "Page" too: show the type only when it says more than the kind.
                return (
                    <div className={styles.cellText}>
                        <Typography variant="caption" weight="semiBold">{kindLabel}</Typography>
                        {value.location.typeLabel && value.location.typeLabel !== kindLabel && (
                            <Typography variant="caption" className={styles.muted}>{value.location.typeLabel}</Typography>
                        )}
                    </div>
                );
            }
        },
        {
            key: 'detail',
            label: t('dashboard.locations.issues'),
            width: '25%',
            render: ({value}) => (value.kind === 'location'
                ? <Typography>{t('dashboard.locations.count', {count: value.location.issueCount})}</Typography>
                : (
                    <div className={styles.cellText}>
                        <Typography variant="caption" className={styles.wrap}>{value.issue.reason || ''}</Typography>
                        {glossaryTitles.size > 1 && (
                            <Typography variant="caption" className={`${styles.wrap} ${styles.muted}`}>
                                {glossaryTitles.get(value.issue.glossaryPath) || value.issue.glossaryPath}
                            </Typography>
                        )}
                    </div>
                ))
        },
        {
            key: 'actions',
            label: t('dashboard.locations.actions'),
            width: '20%',
            align: 'right',
            render: ({value}) => {
                if (value.kind === 'location' && value.location.kind === 'PAGE') {
                    return (
                        <Button variant="ghost"
                                size="small"
                                label={t('dashboard.openPage')}
                                aria-label={t('dashboard.openPageLabel', {name: value.location.name})}
                                onClick={e => {
                                    e.stopPropagation();
                                    window.location.assign(pageBuilderUrl(value.location.path, siteKey, language));
                                }}/>
                    );
                }
                const uuid = value.kind === 'location' ? value.location.uuid : value.issue.nodeUuid;
                const name = value.kind === 'location' ? value.location.name : value.issue.nodeName;
                return (
                    <Button variant="ghost"
                            size="small"
                            icon={<Edit/>}
                            label={t('issue.edit')}
                            aria-label={t('issue.editLabel', {name})}
                            isDisabled={!window.CE_API?.edit}
                            onClick={e => {
                                e.stopPropagation();
                                editInContentEditor(uuid, siteKey, language);
                            }}/>
                );
            }
        }
    ];

    return (
        <>
            <div className={styles.tableScroll}>
                <DataTable primaryKey="id" data={data} columns={columns} isStructured defaultExpandedRows={[]} enablePagination={false}/>
            </div>
            {locations.length > PER_PAGE_OPTIONS[0] && (
                <div className={styles.paging}>
                    <Pagination currentPage={current}
                                itemsPerPage={perPage}
                                itemsPerPageOptions={PER_PAGE_OPTIONS}
                                totalOfItems={locations.length}
                                i18n={{itemsPerPage: t('dashboard.pagination.perPage'), of: t('dashboard.pagination.of')}}
                                onPageChange={setPage}
                                onItemsPerPageChange={n => {
                                    setPerPage(n);
                                    setPage(1);
                                }}/>
                </div>
            )}
        </>
    );
};

LocationsTable.propTypes = {
    locations: PropTypes.array.isRequired,
    glossaryTitles: PropTypes.instanceOf(Map).isRequired,
    siteKey: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired
};

export const ComplianceDashboard = ({siteKey, language}) => {
    const {t} = useGovernanceTranslation('compliance');
    const client = useApolloClient();
    const uiLanguage = useSelector(state => state.uilang);
    const [result, setResult] = useState({loading: true});
    const runId = useRef(0);

    const run = useCallback(() => {
        const id = ++runId.current;
        setResult({loading: true});
        client.query({query: SCAN_SITE, variables: {siteKey, language, uiLanguage}, fetchPolicy: 'network-only'})
            .then(({data}) => {
                if (id === runId.current) {
                    setResult({report: data.complianceScan.scanSite, scannedAt: new Date().toISOString()});
                }
            })
            .catch(error => {
                if (id === runId.current) {
                    console.error('[content-compliance-scan]', error);
                    setResult({error});
                }
            });
    }, [client, siteKey, language, uiLanguage]);

    useEffect(() => {
        run();
        return () => {
            runId.current++;
        };
    }, [run]);

    const {report, scannedAt} = result;
    const glossaryTitles = useMemo(() => new Map((report?.glossaries || []).map(g => [g.path, g.title])), [report]);
    const languageLabel = languageName(language, uiLanguage);

    const header = (
        <Header
            title={t('dashboard.title')}
            mainActions={[
                <Button key="csv" variant="outlined" size="big" icon={<Download/>} label={t('dashboard.exportCsv')}
                        isDisabled={!report || report.status === 'NO_GLOSSARY'} onClick={() => exportCsv(report, scannedAt)}/>,
                <Button key="json" variant="outlined" size="big" icon={<Download/>} label={t('dashboard.exportJson')}
                        isDisabled={!report} onClick={() => exportJson(report, scannedAt)}/>,
                <Button key="scan" color="accent" size="big" icon={<Reload/>} label={t('dialog.rescan')}
                        isDisabled={Boolean(result.loading)} onClick={run}/>
            ]}
        />
    );

    const content = (
        <div className={styles.content} aria-live="polite" aria-busy={Boolean(result.loading)}>
            {result.loading && (
                <div className={`${styles.card} ${styles.loading}`}>
                    <Loader size="big"/>
                    <Typography>{t('dashboard.running')}</Typography>
                </div>
            )}
            {result.error && (
                <Banner variant="danger" title={t('dialog.error.title')}>
                    <Typography>{t('dashboard.error')}</Typography>
                </Banner>
            )}
            {report && (
                <>
                    <section className={styles.card}>
                        <Summary report={report} language={languageLabel}/>
                        {report.status === 'NO_GLOSSARY' && (
                            <Banner variant="info" title={t('banner.noGlossary.title', {language: languageLabel})}>
                                <Typography>{t('banner.noGlossary.message', {language: languageLabel})}</Typography>
                            </Banner>
                        )}
                        {report.skippedLocations > 0 && (
                            <Banner variant="warning" title={t('banner.truncated.title')}>
                                <Typography>{t('dashboard.skipped', {count: report.skippedLocations})}</Typography>
                            </Banner>
                        )}
                        {report.truncated && (
                            <Banner variant="warning" title={t('banner.truncated.title')}>
                                <Typography>{t('dashboard.truncated')}</Typography>
                            </Banner>
                        )}
                        {report.glossaries.length > 0 && (
                            <div className={styles.inline}>
                                <Typography variant="caption" weight="bold">{t('glossaries.applied')}</Typography>
                                {report.glossaries.map(glossary => (
                                    <Button key={glossary.uuid}
                                            variant="ghost"
                                            size="small"
                                            label={t('glossaries.item', {title: glossary.title, count: glossary.termCount})}
                                            isDisabled={!window.CE_API?.edit}
                                            onClick={() => editInContentEditor(glossary.uuid, siteKey, language)}/>
                                ))}
                            </div>
                        )}
                    </section>
                    {report.status !== 'NO_GLOSSARY' && (
                        <>
                            <section className={styles.card}>
                                <Typography component="h2" variant="heading">{t('dashboard.terms.title')}</Typography>
                                {report.terms.length > 0
                                    ? <TermsTable terms={report.terms} glossaryTitles={glossaryTitles}/>
                                    : <EmptyData message={t('dashboard.terms.empty')}/>}
                            </section>
                            <section className={styles.card}>
                                <Typography component="h2" variant="heading">{t('dashboard.locations.title')}</Typography>
                                {report.locations.length > 0
                                    ? <LocationsTable locations={report.locations} glossaryTitles={glossaryTitles} siteKey={siteKey} language={language}/>
                                    : <EmptyData message={t('dashboard.locations.empty')}/>}
                            </section>
                        </>
                    )}
                </>
            )}
        </div>
    );

    return (
        <div className={styles.root}>
            <LayoutContent hasPadding header={header} content={content}/>
        </div>
    );
};

ComplianceDashboard.propTypes = {
    siteKey: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired
};

/** jContent app route: remounts on a site or language switch, so the scan always matches the selector. */
export const ComplianceDashboardRoute = () => {
    const siteKey = useSelector(state => state.site);
    const language = useSelector(state => state.language);
    return <ComplianceDashboard key={`${siteKey}/${language}`} siteKey={siteKey} language={language}/>;
};
