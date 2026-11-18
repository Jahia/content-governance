import React from 'react';

/**
 * What the drawer hands to the entries of other modules: `{path, language, findings, actions}`. The
 * findings are as `entryFindings` builds them (drawer/findings.js), and the actions are the registry
 * entries of `findingActions` (drawer/entries.js). Outside the drawer, there is neither.
 */
export const DrawerEntryContext = React.createContext({path: '', language: '', findings: [], actions: []});
