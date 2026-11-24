import {gql} from '@apollo/client';

const SETTINGS_FIELDS = `
    siteKey
    moduleEnabled
    canEdit
    disabled
`;

export const SITE_SETTINGS = gql`
    query ContentGovernanceSiteSettings($siteKey: String!) {
        contentGovernance {
            siteSettings(siteKey: $siteKey) {${SETTINGS_FIELDS}}
        }
    }
`;
