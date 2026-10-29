/**
 * Paths of the contents the Page Builder frame shows for the page: header and footer stored on the
 * home page, listing results, references. jContent marks every rendered content with
 * [jahiatype="module"][path] and the page with [jahiatype="mainmodule"].
 *
 * @param {string} pagePath the page being scanned
 * @returns {string[]|null} the paths, or null when no Page Builder frame shows this page
 */
export const collectRenderedPaths = pagePath => {
    const frame = document.querySelector('iframe[data-sel-role="page-builder-frame-active"]');
    let doc;
    try {
        doc = frame?.contentDocument;
    } catch {
        return null;
    }
    const mainPath = doc?.querySelector('[jahiatype="mainmodule"]')?.getAttribute('path');
    if (!doc || mainPath !== pagePath) {
        return null;
    }
    const paths = new Set();
    doc.querySelectorAll('[jahiatype="module"][path]').forEach(element => {
        const path = element.getAttribute('path');
        if (path && path.startsWith('/')) {
            paths.add(path);
        }
    });
    return [...paths];
};
