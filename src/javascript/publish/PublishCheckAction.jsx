import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';
import {useSelector} from 'react-redux';
import {gql, useApolloClient} from '@apollo/client';
import {useNodeChecks} from '@jahia/data-helper';
import {useSiteSettings} from '../common/useSiteSettings';
import {MODULE, OPEN_DRAWER_EVENT} from '../common/module';
import {NS} from '../common/i18n';
import {collectRenderedPaths} from '../compliance/renderedPaths';

const AUDIT_SUMMARY = gql`
    query ContentGovernancePublishAudit($path: String!, $language: String!) {
        contentGovernance {
            pageAudits(path: $path, language: $language) {
                auditedAt
                summary { total seriousAccessibility }
            }
        }
    }
`;

const TERMS = gql`
    query ContentGovernancePublishTerms($path: String!, $language: String!, $renderedPaths: [String]) {
        complianceScan {
            scanPage(path: $path, language: $language, renderedPaths: $renderedPaths) {
                issues { term }
            }
        }
    }
`;

/** The latest stored audit: `{run}` (null when the page has none), or `{failed: true}` when unreadable. */
const lastAudit = client => (path, language) => client
    .query({query: AUDIT_SUMMARY, variables: {path, language}, fetchPolicy: 'network-only'})
    .then(({data}) => ({run: data?.contentGovernance?.pageAudits?.[0] || null}))
    .catch(() => ({failed: true}));

const termCount = client => (path, language) => client
    .query({query: TERMS, variables: {path, language, renderedPaths: collectRenderedPaths(path) || []}, fetchPolicy: 'network-only'})
    .then(({data}) => data?.complianceScan?.scanPage?.issues?.length ?? 0)
    .catch(() => 0);

/**
 * The menu item a site adds at the top of jContent's Publish menu, for the page in view: what the
 * latest stored audit and the compliance scan found, opening the drawer. Non-blocking: jContent's own
 * publish actions are untouched. Shown only when there is something to review or no audit yet.
 *
 * jContent writes the label as HTML: its parameters are numbers counted here, never page text.
 */
export const PublishCheckAction = ({path, language, render: Render, ...rest}) => {
    const client = useApolloClient();
    const viewedPath = useSelector(state => state.jcontent?.path || null);
    const siteKey = useSelector(state => state.site);
    const reduxLanguage = useSelector(state => state.language);
    const lang = language || reduxLanguage;
    const inView = Boolean(path) && path === viewedPath;
    const {checksResult} = useNodeChecks(
        {path},
        {showOnNodeTypes: ['jnt:page', 'jmix:mainResource'], requireModuleInstalledOnSite: [MODULE]}
    );
    // The compliance scan reads a page as someone who may edit it
    const {checksResult: canWrite} = useNodeChecks({path}, {requiredPermission: ['jcr:write']});
    const {settings, isOn} = useSiteSettings(checksResult && inView ? siteKey : null);
    const [found, setFound] = useState(null);

    const auditOn = Boolean(settings) && isOn('audit');
    const termsOn = Boolean(settings) && isOn('compliance') && Boolean(canWrite);
    useEffect(() => {
        let cancelled = false;
        if (auditOn || termsOn) {
            Promise.all([
                auditOn ? lastAudit(client)(path, lang) : Promise.resolve({failed: true}),
                termsOn ? termCount(client)(path, lang) : Promise.resolve(0)
            ]).then(([audit, terms]) => {
                if (!cancelled) {
                    setFound({audit, terms});
                }
            });
        } else {
            setFound(null);
        }
        return () => {
            cancelled = true;
        };
    }, [client, path, lang, auditOn, termsOn]);

    if (!inView || !found) {
        return null;
    }
    const {audit, terms} = found;
    const run = audit.failed ? null : audit.run;
    const notAudited = auditOn && !audit.failed && run === null;
    const issues = run ? run.summary.total : 0;
    const serious = run ? Math.max(0, run.summary.seriousAccessibility ?? 0) : 0;
    if (!notAudited && issues === 0 && terms === 0) {
        return null;
    }

    const section = !notAudited && issues === 0 && terms > 0 ? 'compliance' : 'audit';
    return (
        <Render {...rest}
                buttonLabel={`${NS}:publishCheck.${labelKey(notAudited, auditOn, termsOn && terms > 0)}`}
                buttonLabelParams={{issues, serious, terms}}
                onClick={() => globalThis.dispatchEvent(new CustomEvent(OPEN_DRAWER_EVENT, {detail: {path, section}}))}/>
    );
};

/**
 * The label for what was checked: a count of a section the site keeps off would read as "none found".
 * `termsFound` is true when the compliance scan ran and found terms.
 */
function labelKey(notAudited, auditOn, termsFound) {
    if (notAudited) {
        return termsFound ? 'notAuditedTerms' : 'notAudited';
    }
    if (auditOn && termsFound) {
        return 'review';
    }
    return auditOn ? 'reviewAudit' : 'reviewTerms';
}

PublishCheckAction.propTypes = {
    path: PropTypes.string,
    language: PropTypes.string,
    render: PropTypes.elementType.isRequired
};
