import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {useApolloClient} from '@apollo/client';
import {Banner, Button, Chip, DataTable, Download, EmptyData, Header, LayoutContent, Loader, Pagination, Reload, Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../../common/i18n';
import {languageName, pageBuilderUrl} from '../../common/url';
import {SITE_AUDITS} from '../auditHistory';
import {keyed, readFindings, subRowsOf} from './pageFindings';
import {TAB_COLUMNS, exportCsv, exportJson} from './exportAudits';
import styles from './AuditDashboard.module.css';

const PER_PAGE_OPTIONS = [10, 20, 50];
const COUNTED_PAGES_LIMIT = 10_000;
const SEVERITY_COLORS = {critical: 'danger', serious: 'danger', moderate: 'warning', minor: 'default'};

/** A state update that records what was read for one page. */
const withDetail = (path, detail) => all => ({...all, [path]: detail});

/** Fewer issues than the audit before: green; more: red; the same or no audit before: nothing. */
const Trend = ({total, previous}) => {
    const {t} = useGovernanceTranslation('audit');
    if (previous === null || previous === undefined || previous === total) {
        return null;
    }
    const delta = total - previous;
    return (
        <span title={t('dashboard.trend', {previous})}>
            <Chip color={delta < 0 ? 'success' : 'danger'} label={delta < 0 ? `−${-delta}` : `+${delta}`}/>
        </span>
    );
};

Trend.propTypes = {
    total: PropTypes.number.isRequired,
    previous: PropTypes.number
};

const PagesTable = ({pages, siteKey, language}) => {
    const {t} = useGovernanceTranslation('audit');
    const client = useApolloClient();
    const [page, setPage] = useState(1);
    const [expanded, setExpanded] = useState([]);
    // Findings per page path, read when its row is first opened: {kind: 'error'} or {findings}
    const [details, setDetails] = useState({});
    const requested = useRef(new Set());
    const [perPage, setPerPage] = useState(PER_PAGE_OPTIONS[0]);
    const lastPage = Math.max(1, Math.ceil(pages.length / perPage));
    const current = Math.min(page, lastPage);
    const formatDate = iso => new Date(iso).toLocaleString(language === 'fr' ? 'fr-FR' : language);

    // A page with issues gets sub-rows, so it can be opened; they are its findings once read.
    const data = useMemo(() => pages.slice((current - 1) * perPage, current * perPage).map(item => {
        const row = keyed({id: item.path, kind: 'page', item});
        return item.lastAudit.summary.total > 0 ? {...row, subRows: subRowsOf(item, details)} : row;
    }), [pages, current, perPage, details]);

    const load = useCallback(path => {
        requested.current.add(path);
        readFindings(client, path, language, t)
            .then(findings => setDetails(withDetail(path, {findings})))
            .catch(error => {
                console.error('[content-governance]', error);
                // Opening the row again tries again
                requested.current.delete(path);
                setDetails(withDetail(path, {kind: 'error'}));
            });
    }, [client, language, t]);

    const onExpandChange = useCallback(ids => {
        setExpanded(ids);
        ids.filter(id => !requested.current.has(id) && pages.some(item => item.path === id)).forEach(load);
    }, [load, pages]);

    const columns = [
        {
            key: 'page',
            label: t('dashboard.page'),
            width: '34%',
            render: ({value}) => {
                if (value.kind === 'finding') {
                    return (
                        <div className={styles.cellText}>
                            <Typography weight="semiBold" className={styles.wrap}>{value.finding.title}</Typography>
                            {value.finding.detail && <Typography variant="caption" className={`${styles.wrap} ${styles.muted}`}>{value.finding.detail}</Typography>}
                        </div>
                    );
                }
                if (value.kind !== 'page') {
                    return (
                        <div className={styles.inline}>
                            {value.kind === 'loading' && <Loader size="small"/>}
                            <Typography variant="caption" className={styles.wrap}>{t(`dashboard.findings.${value.kind}`)}</Typography>
                        </div>
                    );
                }
                return (
                    <div className={styles.cellText}>
                        <Typography weight="semiBold" className={styles.wrap}>{value.item.title || value.item.path}</Typography>
                        <Typography variant="caption" className={`${styles.wrap} ${styles.muted}`}>{value.item.path}</Typography>
                    </div>
                );
            }
        },
        {
            key: 'audited',
            label: t('dashboard.audited'),
            width: '17%',
            render: ({value}) => {
                if (value.kind === 'finding') {
                    return <Chip color={SEVERITY_COLORS[value.finding.severity] || 'default'} label={t(`a11y.impacts.${value.finding.severity}`)}/>;
                }
                if (value.kind !== 'page') {
                    return null;
                }
                return (
                    <div className={styles.cellText}>
                        <Typography variant="caption" className={styles.wrap}>{formatDate(value.item.lastAudit.auditedAt)}</Typography>
                        <Typography variant="caption" className={styles.muted}>{value.item.lastAudit.auditedBy}</Typography>
                    </div>
                );
            }
        },
        {
            key: 'tabs',
            label: t('dashboard.byTab'),
            width: '29%',
            render: ({value}) => {
                if (value.kind === 'finding') {
                    return (
                        <div className={styles.inline}>
                            <Chip label={t(`tabs.${value.finding.tab}`)}/>
                            {value.finding.count > 0 && (
                                <Typography variant="caption" className={styles.muted}>{t('dashboard.findings.elements', {count: value.finding.count})}</Typography>
                            )}
                        </div>
                    );
                }
                if (value.kind !== 'page') {
                    return null;
                }
                const summary = value.item.lastAudit.summary;
                const withIssues = TAB_COLUMNS.filter(tab => summary[tab] > 0);
                if (withIssues.length === 0) {
                    return <Typography variant="caption" className={styles.muted}>{t('dashboard.noIssues')}</Typography>;
                }
                return (
                    <div className={styles.inline}>
                        {withIssues.map(tab => {
                            const label = t(`tabs.${tab}`);
                            return <Chip key={tab} label={`${label} ${summary[tab]}`}/>;
                        })}
                    </div>
                );
            }
        },
        {
            key: 'total',
            label: t('dashboard.total'),
            width: '10%',
            render: ({value}) => value.kind === 'page' && (
                <div className={styles.inline}>
                    {/* Moonstone's Typography drops a bare 0: the count is written as text */}
                    <Typography weight="bold">{`${value.item.lastAudit.summary.total}`}</Typography>
                    <Trend total={value.item.lastAudit.summary.total} previous={value.item.previousTotal}/>
                </div>
            )
        },
        {
            key: 'actions',
            label: t('dashboard.actions'),
            width: '10%',
            align: 'right',
            render: ({value}) => value.kind === 'page' && (
                <Button variant="ghost"
                        size="small"
                        label={t('dashboard.openPage')}
                        aria-label={t('dashboard.openPageLabel', {name: value.item.title || value.item.path})}
                        onClick={e => {
                            e.stopPropagation();
                            globalThis.location.assign(pageBuilderUrl(value.item.path, siteKey, language));
                        }}/>
            )
        }
    ];

    return (
        <>
            <div className={styles.tableScroll}>
                <DataTable primaryKey="id"
                           data={data}
                           columns={columns}
                           isStructured
                           expandedRows={expanded}
                           enablePagination={false}
                           onExpandChange={onExpandChange}/>
            </div>
            {pages.length > PER_PAGE_OPTIONS[0] && (
                <div className={styles.paging}>
                    <Pagination currentPage={current}
                                itemsPerPage={perPage}
                                itemsPerPageOptions={PER_PAGE_OPTIONS}
                                totalOfItems={pages.length}
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

PagesTable.propTypes = {
    pages: PropTypes.array.isRequired,
    siteKey: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired
};

/**
 * The site dashboard of Page quality: the latest audit of every audited page of the site in the
 * current language, most issues first, with exports. Audits are the ones editors ran in the drawer.
 */
export const AuditDashboard = ({siteKey, language}) => {
    const {t} = useGovernanceTranslation('audit');
    const client = useApolloClient();
    const uiLanguage = useSelector(state => state.uilang);
    const [result, setResult] = useState({loading: true});
    const runId = useRef(0);

    const load = useCallback(() => {
        const id = ++runId.current;
        setResult({loading: true});
        client.query({query: SITE_AUDITS, variables: {siteKey, language}, fetchPolicy: 'network-only'})
            .then(({data}) => {
                if (id === runId.current) {
                    setResult({report: data.contentGovernance.siteAudits});
                }
            })
            .catch(error => {
                if (id === runId.current) {
                    console.error('[content-governance]', error);
                    setResult({error});
                }
            });
    }, [client, siteKey, language]);

    useEffect(() => {
        load();
        return () => {
            runId.current++;
        };
    }, [load]);

    const {report} = result;
    const languageLabel = languageName(language, uiLanguage);
    const pageCount = report && report.pageCount >= COUNTED_PAGES_LIMIT ? `${COUNTED_PAGES_LIMIT}+` : report?.pageCount;

    const header = (
        <Header
            title={t('dashboard.title')}
            mainActions={[
                <Button key="csv" variant="outlined" size="big" icon={<Download/>} label={t('dashboard.exportCsv')}
                        isDisabled={!report || report.pages.length === 0} onClick={() => exportCsv(report, siteKey, language)}/>,
                <Button key="json" variant="outlined" size="big" icon={<Download/>} label={t('dashboard.exportJson')}
                        isDisabled={!report} onClick={() => exportJson(report, siteKey, language)}/>,
                <Button key="refresh" color="accent" size="big" icon={<Reload/>} label={t('dashboard.refresh')}
                        isDisabled={Boolean(result.loading)} onClick={load}/>
            ]}
        />
    );

    const content = (
        <div className={styles.content} aria-live="polite" aria-busy={Boolean(result.loading)}>
            {result.loading && (
                <div className={`${styles.card} ${styles.loading}`}>
                    <Loader size="big"/>
                </div>
            )}
            {result.error && (
                <Banner variant="danger" title={t('dashboard.errorTitle')}>
                    <Typography>{t('dashboard.error')}</Typography>
                </Banner>
            )}
            {report && (
                <section className={styles.card}>
                    <Typography component="h2" variant="heading">
                        {t('dashboard.summary', {audited: report.pages.length, total: pageCount, language: languageLabel})}
                    </Typography>
                    <Typography className={styles.muted}>{t('dashboard.hint')}</Typography>
                    {report.pages.length > 0 ?
                        <PagesTable pages={report.pages} siteKey={siteKey} language={language}/> :
                        <EmptyData title={t('dashboard.emptyTitle')} message={t('dashboard.empty', {language: languageLabel})}/>}
                </section>
            )}
        </div>
    );

    return (
        <div className={styles.root}>
            <LayoutContent hasPadding header={header} content={content}/>
        </div>
    );
};

AuditDashboard.propTypes = {
    siteKey: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired
};

/** jContent app route: remounts on a site or language switch, so the list always matches the selector. */
export const AuditDashboardRoute = () => {
    const siteKey = useSelector(state => state.site);
    const language = useSelector(state => state.language);
    return <AuditDashboard key={`${siteKey}/${language}`} siteKey={siteKey} language={language}/>;
};
