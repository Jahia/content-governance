import {gql} from '@apollo/client';

/** What the Rules & domains screen reads: the hierarchy of the site with its switches, and its saved layout. */
export const RULES_SCREEN = gql`
    query ContentGovernanceRulesScreen($siteKey: String!, $language: String) {
        contentGovernance {
            siteSettings(siteKey: $siteKey) {
                siteKey
                moduleEnabled
                canEdit
            }
            siteLayout(siteKey: $siteKey) {
                saved
                off
            }
            contentDomains(siteKey: $siteKey, language: $language, includeOff: true) {
                id
                label
                help
                panel
                switchedOn
                isNew
                subdomains {
                    id
                    label
                    help
                    switchedOn
                    isNew
                }
            }
            contentRules(siteKey: $siteKey, language: $language) {
                id
                label
                subdomain
                order
                severity
                switchedOn
                isNew
            }
        }
    }
`;

export const SAVE_LAYOUT = gql`
    mutation ContentGovernanceSaveLayout($siteKey: String!, $layout: String!) {
        contentGovernance {
            saveSiteLayout(siteKey: $siteKey, layout: $layout) {
                saved
            }
        }
    }
`;

export const RESET_LAYOUT = gql`
    mutation ContentGovernanceResetLayout($siteKey: String!) {
        contentGovernance {
            resetSiteLayout(siteKey: $siteKey) {
                saved
            }
        }
    }
`;
