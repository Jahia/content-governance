import {showPanel} from './drawerState';

const STORAGE_KEY = 'content-governance:drawerPanel';

/**
 * The drawer as the browser tab last left it: open on a panel, or closed. jContent and Page Builder
 * mount the header button again on each page, and the button reads this when it mounts. The tab
 * keeps it through a reload; a new tab starts closed. The context of a panel is not kept: it
 * belongs to the request that opened the panel.
 *
 * @param {Storage} [storage] where the tab keeps it
 * @returns {{panel: string, contexts: object}|null} the state of the drawer, or null when closed
 */
export function rememberedState(storage = globalThis.sessionStorage) {
    try {
        const panel = storage?.getItem(STORAGE_KEY);
        return panel ? showPanel(null, panel) : null;
    } catch {
        return null;
    }
}

/**
 * Keeps the panel in view for the next page, or that the drawer is closed.
 *
 * @param {string|null} panel the key of the panel in view, or null when the drawer is closed
 * @param {Storage} [storage] where the tab keeps it
 */
export function rememberPanel(panel, storage = globalThis.sessionStorage) {
    try {
        if (panel) {
            storage?.setItem(STORAGE_KEY, panel);
        } else {
            storage?.removeItem(STORAGE_KEY);
        }
    } catch {
        // A private window or blocked storage only closes the drawer on the next page
    }
}
