import React from 'react';

/**
 * What the drawer hands to the entries of other modules: `{path, language, findings, actions}`. The
 * findings are as `entryFindings` builds them (drawer/findings.js), and the actions are the registry
 * entries of `findingActions` (drawer/entries.js). Outside the drawer, there is neither.
 *
 * The cards of the drawer also read `ruleOf(id)`, the rule of the site with the fields of `contentRules`, and
 * `previewDocument()`, the document of the preview of the page, which a fixer receives.
 */
export const DrawerEntryContext = React.createContext({
    path: '',
    language: '',
    findings: [],
    actions: [],
    ruleOf: () => undefined,
    previewDocument: () => null
});
