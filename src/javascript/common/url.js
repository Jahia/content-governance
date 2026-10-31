/**
 * Module servlet and asset URLs under Jahia's context path, which is empty at the web root and
 * "/jahia" (or any other prefix) when Jahia is deployed under a path.
 */
export const moduleUrl = path => `${globalThis.contextJsParameters?.contextPath || ''}/modules/content-governance${path}`;

/** Page Builder URL of a page: the jContent pages route, under the context path. */
export const pageBuilderUrl = (path, siteKey, language) => {
    const relative = path.slice(`/sites/${siteKey}`.length).split('/').map(encodeURIComponent).join('/');
    return `${globalThis.contextJsParameters?.contextPath || ''}/jahia/jcontent/${siteKey}/${language}/pages${relative}`;
};

/** A language code's name in the editor's language (`fr` in English: "French"), else the code. */
export const languageName = (code, uiLanguage) => {
    try {
        return new Intl.DisplayNames([uiLanguage || 'en'], {type: 'language'}).of(code.replace('_', '-')) || code;
    } catch {
        return code;
    }
};
