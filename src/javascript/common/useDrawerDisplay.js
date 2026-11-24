import {gql, useQuery} from '@apollo/client';

export const DRAWER_DISPLAY = gql`
    query ContentGovernanceDrawerDisplay {
        contentGovernance {
            drawerDisplay {
                fixFirst
                previewButton
            }
        }
    }
`;

const HIDDEN = {fixFirst: false, previewButton: false};

/**
 * What the drawer shows to every editor of the server, as the cfg of the module sets it
 * (org.jahia.community.contentgovernance.drawer.cfg). Each element stays hidden while the answer
 * loads, and when it fails.
 *
 * @returns {{fixFirst: boolean, previewButton: boolean}} whether the drawer shows "Fix first", and
 *   whether Page quality shows its "Show preview" button
 */
export function useDrawerDisplay() {
    // The cached answer at once, then the server's: an administrator may have changed the cfg since
    const {data} = useQuery(DRAWER_DISPLAY, {fetchPolicy: 'cache-and-network'});
    return data?.contentGovernance?.drawerDisplay || HIDDEN;
}
