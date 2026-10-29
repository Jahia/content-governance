/**
 * Module servlet and asset URLs under Jahia's context path, which is empty at the web root and
 * "/jahia" (or any other prefix) when Jahia is deployed under a path.
 */
export const moduleUrl = path => `${globalThis.contextJsParameters?.contextPath || ''}/modules/content-governance${path}`;
