import {gql} from '@apollo/client';

const SETTINGS_FIELDS = `
    siteKey
    moduleEnabled
    canEdit
    disabled
    sections {
        id
        enabled
        tabs {
            id
            enabled
        }
    }
    ai {
        configured
        provider
        model
        images
    }
`;

export const SITE_SETTINGS = gql`
    query ContentGovernanceSiteSettings($siteKey: String!) {
        contentGovernance {
            siteSettings(siteKey: $siteKey) {${SETTINGS_FIELDS}}
        }
    }
`;

export const SAVE_SITE_SETTINGS = gql`
    mutation ContentGovernanceSaveSiteSettings($siteKey: String!, $disabled: [String]!) {
        contentGovernance {
            saveSiteSettings(siteKey: $siteKey, disabled: $disabled) {${SETTINGS_FIELDS}}
        }
    }
`;
