import React, {useEffect, useMemo, useRef} from 'react';
import PropTypes from 'prop-types';
import {Typography} from '@jahia/moonstone';
import {useGovernanceTranslation} from '../common/i18n';
import {CollapsibleSection, ExpandAll} from '../common/CollapsibleSection';
import {defaultOpen, useOpenSections} from '../common/openSections';
import {HighlightContext} from '../audit/HighlightContext';
import {PageAuditStatus, PageAuditToolbar, PageFindingsOf, PageSubdomainView} from '../audit/PageAudit';
import {GeoFacts, GeoFindingsOf, GeoSubdomainView, GeoToolbar} from '../geo/GeoParts';
import {ComplianceFacts, ComplianceSubdomainView, ComplianceToolbar} from '../compliance/ComplianceParts';
import {COMPLIANCE, GEO, PAGE, PATTERN, sectionElementId, viewOf} from './subdomains';
import styles from './DomainTab.module.css';

/**
 * What a subdomain shows: the dedicated view of its check, then the findings of the rules that the site places
 * in it and that this view does not show, whatever check found them.
 */
function SubdomainContent({id, engines, places, path, complianceUnavailable, onEdit}) {
    const {t} = useGovernanceTranslation('drawer');
    const view = viewOf(id);
    const rules = places.rulesIn(id);
    const ruleIds = useMemo(() => new Set(rules.map(rule => rule.id)), [rules]);
    const hasTerms = rules.some(rule => rule.evaluator === PATTERN);
    const edit = uuid => onEdit(uuid, id);
    return (
        <>
            {view === GEO && <GeoSubdomainView geo={engines.geo} id={id} path={path}/>}
            {view === COMPLIANCE && complianceUnavailable && <Typography>{t(`unavailable.${complianceUnavailable}`)}</Typography>}
            {view === COMPLIANCE && !complianceUnavailable &&
                <ComplianceSubdomainView dedicated scan={engines.compliance} id={id} subdomainOf={places.subdomainOf} onEdit={edit}/>}
            {view === PAGE || view === null ?
                <PageSubdomainView audit={engines.page} id={id} subdomainOf={places.subdomainOf}/> :
                <PageFindingsOf audit={engines.page} id={id} subdomainOf={places.subdomainOf}/>}
            {view === null && <GeoFindingsOf geo={engines.geo} id={id} path={path} ruleIds={ruleIds}/>}
            {view !== COMPLIANCE && hasTerms && !complianceUnavailable &&
                <ComplianceSubdomainView scan={engines.compliance} id={id} subdomainOf={places.subdomainOf} dedicated={false} onEdit={edit}/>}
        </>
    );
}

SubdomainContent.propTypes = {
    id: PropTypes.string.isRequired,
    engines: PropTypes.object.isRequired,
    places: PropTypes.object.isRequired,
    path: PropTypes.string.isRequired,
    complianceUnavailable: PropTypes.string,
    onEdit: PropTypes.func.isRequired
};

/**
 * A domain of the drawer, such as Page quality: the toolbar of each check that its subdomains read, what holds
 * for the whole page, and one section per subdomain, in the order of the layout of the site. A section opens
 * by default when it holds a critical or serious finding, or when its check did not finish.
 */
export function DomainTab({sections, checks, engines, places, path, language, requested, complianceUnavailable, onEdit}) {
    const {t} = useGovernanceTranslation('audit');
    const {t: tGeo} = useGovernanceTranslation('geo');
    const {page, geo, compliance} = engines;
    // A new run of the check of a section opens it again when it holds a major defect. A choice that the editor
    // makes while the check runs holds for the results of that run.
    const runs = {[PAGE]: page.runId, [GEO]: geo.runs, [COMPLIANCE]: compliance.result.run ?? 0};
    const resetKeys = Object.fromEntries(sections.map(section => [section.id, runs[viewOf(section.id) || PAGE]]));
    const {isOpen, setOpen} = useOpenSections(defaultOpen(sections), resetKeys);
    const usesCompliance = checks.has(COMPLIANCE) && !complianceUnavailable;

    // A "Fix first" link, or a request to open the drawer on a subdomain, opens its section and brings it into
    // view, once per request
    const handled = useRef(null);
    useEffect(() => {
        if (requested && requested !== handled.current) {
            handled.current = requested;
            setOpen([requested.tab], true);
            document.getElementById(sectionElementId(requested.tab))?.scrollIntoView({block: 'start'});
        }
    }, [requested, setOpen]);

    return (
        <>
            <div className={styles.toolbar}>
                {checks.has(PAGE) && <PageAuditToolbar audit={page} language={language}/>}
                {checks.has(GEO) && <GeoToolbar geo={geo}/>}
                {usesCompliance && <ComplianceToolbar scan={compliance}/>}
                <ExpandAll ids={sections.map(section => section.id)} isOpen={isOpen} setOpen={setOpen}/>
            </div>
            <HighlightContext.Provider value={page.highlight}>
                <div className={styles.content}>
                    <div className={styles.facts}>
                        {checks.has(PAGE) && <PageAuditStatus audit={page}/>}
                        {checks.has(GEO) && <GeoFacts geo={geo}/>}
                        {usesCompliance && <ComplianceFacts scan={compliance} onEdit={uuid => onEdit(uuid, null)}/>}
                    </div>
                    {sections.map(section => (
                        <CollapsibleSection key={section.id}
                                            id={sectionElementId(section.id)}
                                            label={section.label}
                                            help={section.help || section.label}
                                            note={section.count ? t('tabs.badgeTooltip', {count: section.count}) : undefined}
                                            status={section.unavailable ? tGeo('citations.notConfiguredLabel') : undefined}
                                            count={section.count}
                                            isSerious={section.serious}
                                            isOpen={isOpen(section.id)}
                                            onToggle={() => setOpen([section.id], !isOpen(section.id))}
                        >
                            <SubdomainContent id={section.id}
                                              engines={engines}
                                              places={places}
                                              path={path}
                                              complianceUnavailable={complianceUnavailable}
                                              onEdit={onEdit}/>
                        </CollapsibleSection>
                    ))}
                </div>
            </HighlightContext.Provider>
        </>
    );
}

DomainTab.propTypes = {
    // The subdomains of the domain, in order, each with its label, its help and what it has to review
    sections: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string.isRequired,
        label: PropTypes.string.isRequired,
        help: PropTypes.string,
        count: PropTypes.number,
        serious: PropTypes.bool,
        incomplete: PropTypes.bool,
        unavailable: PropTypes.bool
    })).isRequired,
    // The checks that the subdomains read (drawer/subdomains.js)
    checks: PropTypes.instanceOf(Set).isRequired,
    // The state of the page check, the GEO check and the compliance scan
    engines: PropTypes.shape({
        page: PropTypes.object.isRequired,
        geo: PropTypes.object.isRequired,
        compliance: PropTypes.object.isRequired
    }).isRequired,
    // Where the rules of the site sit (drawer/layout.js)
    places: PropTypes.object.isRequired,
    path: PropTypes.string.isRequired,
    language: PropTypes.string.isRequired,
    // {tab, at}: the subdomain to open, once per request
    requested: PropTypes.shape({tab: PropTypes.string, at: PropTypes.number}),
    // Why the editor cannot use the compliance scan here: notPage or noWrite
    complianceUnavailable: PropTypes.string,
    // Opens a content in Content Editor, and the drawer again on a subdomain when it closes
    onEdit: PropTypes.func.isRequired
};
