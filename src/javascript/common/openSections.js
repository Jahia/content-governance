import {useCallback, useState} from 'react';

/**
 * Which subdomain sections of a drawer domain are open. A section opens by default when it holds a
 * critical or serious defect, or when its check did not finish. The editor's own opening and closing
 * holds until the domain shows new results (`resetKey`, such as the time of the audit): the defaults
 * then apply again.
 */

const NONE = Object.freeze({});

/** The editor's own choices that still apply: none once the results changed. */
const own = (choice, resetKey) => (choice.key === resetKey ? choice.open : NONE);

/**
 * @param {Array<{id: string, count: ?number, serious: boolean, incomplete: boolean}>} sections the sections
 * @returns {Object<string, boolean>} by section id, whether it opens by default
 */
export const defaultOpen = sections => Object.fromEntries(sections.map(section => [
    section.id,
    Boolean(section.incomplete) || (Boolean(section.serious) && section.count > 0)
]));

/**
 * @param {{key: *, open: Object<string, boolean>}} choice the editor's choices, and the results they apply to
 * @param {*} resetKey the results in view
 * @param {Object<string, boolean>} defaults by section id (`defaultOpen`)
 * @param {string} id the section
 * @returns {boolean} whether the section is open
 */
export function isSectionOpen(choice, resetKey, defaults, id) {
    const mine = own(choice, resetKey);
    return Object.hasOwn(mine, id) ? mine[id] : Boolean(defaults[id]);
}

/**
 * @param {{key: *, open: Object<string, boolean>}} choice the editor's choices so far
 * @param {*} resetKey the results in view
 * @param {Array<string>} ids the sections the editor opens or closes
 * @param {boolean} open whether they open
 * @returns {{key: *, open: Object<string, boolean>}} the choices with this one
 */
export const withSections = (choice, resetKey, ids, open) => ({
    key: resetKey,
    open: {...own(choice, resetKey), ...Object.fromEntries(ids.map(id => [id, open]))}
});

/**
 * @param {Object<string, boolean>} defaults by section id (`defaultOpen`)
 * @param {*} resetKey the results in view: a new value applies the defaults again
 * @returns {{isOpen: function(string): boolean, setOpen: function(Array<string>, boolean): void}} the state
 */
export function useOpenSections(defaults, resetKey) {
    const [choice, setChoice] = useState(() => ({key: resetKey, open: NONE}));
    const isOpen = id => isSectionOpen(choice, resetKey, defaults, id);
    const setOpen = useCallback((ids, open) => setChoice(c => withSections(c, resetKey, ids, open)), [resetKey]);
    return {isOpen, setOpen};
}
