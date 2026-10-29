import {gql} from '@apollo/client';

export const SCAN_PAGE = gql`
    query ComplianceScanPage($path: String!, $language: String!, $uiLanguage: String, $renderedPaths: [String]) {
        complianceScan {
            scanPage(path: $path, language: $language, uiLanguage: $uiLanguage, renderedPaths: $renderedPaths) {
                pagePath
                language
                status
                scannedContents
                truncated
                glossaries {
                    path
                    uuid
                    title
                    termCount
                }
                issues {
                    nodePath
                    nodeUuid
                    nodeName
                    nodeType
                    propertyName
                    propertyLabel
                    term
                    matchedText
                    before
                    after
                    reason
                    alternative
                    glossaryPath
                    origin
                    originPath
                }
            }
        }
    }
`;
