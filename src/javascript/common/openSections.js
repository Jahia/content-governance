import {useCallback, useState} from 'react';

/**
 * Which subdomain sections of a drawer domain are open. Every section starts closed. The editor opens and
 * closes them, one by one or all at once, and a "Fix first" link opens the section of its finding. The choice
 * holds while the drawer is open, whatever check runs again. Example: the editor opens Jahia health and presses
 * Re-run, and Jahia health stays open.
 */

const NONE = Object.freeze(new Set());

/**
 * @param {Set<string>} open the sections open so far
 * @param {Array<string>} ids the sections the editor opens or closes
 * @param {boolean} isOpen whether they open
 * @returns {Set<string>} the sections open with this choice
 */
export function withSections(open, ids, isOpen) {
    const next = new Set(open);
    ids.forEach(id => (isOpen ? next.add(id) : next.delete(id)));
    return next;
}

/**
 * @returns {{isOpen: function(string): boolean, setOpen: function(Array<string>, boolean): void}} the state
 */
export function useOpenSections() {
    const [open, setOpenIds] = useState(NONE);
    const isOpen = id => open.has(id);
    const setOpen = useCallback((ids, value) => setOpenIds(current => withSections(current, ids, value)), []);
    return {isOpen, setOpen};
}
