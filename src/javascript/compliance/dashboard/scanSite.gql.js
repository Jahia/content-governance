import {gql} from '@apollo/client';

export const SCAN_SITE = gql`
    query ComplianceScanSite($siteKey: String!, $language: String!, $uiLanguage: String) {
        complianceScan {
            scanSite(siteKey: $siteKey, language: $language, uiLanguage: $uiLanguage) {
                siteKey
                language
                status
                scannedPages
                scannedContents
                skippedLocations
                truncated
                glossaries {
                    path
                    uuid
                    title
                    termCount
                }
                terms {
                    ruleId
                    term
                    folder
                    glossaryPath
                    reason
                    alternative
                    uses
                    locations
                }
                locations {
                    path
                    uuid
                    name
                    kind
                    typeLabel
                    issueCount
                    issues {
                        nodePath
                        nodeUuid
                        nodeName
                        nodeType
                        propertyName
                        propertyLabel
                        ruleId
                        severity
                        folder
                        term
                        matchedText
                        before
                        after
                        reason
                        alternative
                        glossaryPath
                    }
                }
            }
        }
    }
`;
