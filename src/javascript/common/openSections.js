import {useCallback, useRef, useState} from 'react';

/**
 * Which subdomain sections of a drawer domain are open. A section opens by default when it holds a
 * critical or serious defect, or when its check did not finish. The editor's own opening and closing
 * of a section holds until the check that feeds the section runs again (its reset key, such as the
 * number of the run of the page check): its default then applies again. A choice made while the check
 * runs holds for the results of that run. Each section has its own key, because the checks of one
 * domain run at different times. Example: the scan of the terms answers while the editor reads
 * Readability, and Readability stays open.
 */

const NONE = Object.freeze({});

/** The editor's choice for a section, while the section shows the results that the choice was made on. */
const own = (choice, resetKeys, id) => {
    const mine = choice[id];
    return mine && mine.key === resetKeys[id] ? mine : null;
};

/**
 * @param {Array<{id: string, count: ?number, serious: boolean, incomplete: boolean}>} sections the sections
 * @returns {Object<string, boolean>} by section id, whether it opens by default
 */
export const defaultOpen = sections => Object.fromEntries(sections.map(section => [
    section.id,
    Boolean(section.incomplete) || (Boolean(section.serious) && section.count > 0)
]));

/**
 * @param {Object<string, {key: *, open: boolean}>} choice the editor's choices, each with the results it applies to
 * @param {Object<string, *>} resetKeys by section id, the results in view
 * @param {Object<string, boolean>} defaults by section id (`defaultOpen`)
 * @param {string} id the section
 * @returns {boolean} whether the section is open
 */
export function isSectionOpen(choice, resetKeys, defaults, id) {
    const mine = own(choice, resetKeys, id);
    return mine ? mine.open : Boolean(defaults[id]);
}

/**
 * @param {Object<string, {key: *, open: boolean}>} choice the editor's choices so far
 * @param {Object<string, *>} resetKeys by section id, the results in view
 * @param {Array<string>} ids the sections the editor opens or closes
 * @param {boolean} open whether they open
 * @returns {Object<string, {key: *, open: boolean}>} the choices with this one
 */
export const withSections = (choice, resetKeys, ids, open) => ({
    ...choice,
    ...Object.fromEntries(ids.map(id => [id, {key: resetKeys[id], open}]))
});

/**
 * @param {Object<string, boolean>} defaults by section id (`defaultOpen`)
 * @param {Object<string, *>} resetKeys by section id, the results in view: a new value applies the default of
 *     the section again
 * @returns {{isOpen: function(string): boolean, setOpen: function(Array<string>, boolean): void}} the state
 */
export function useOpenSections(defaults, resetKeys) {
    const [choice, setChoice] = useState(NONE);
    // The keys of the last render: setOpen stays the same function while they change
    const keys = useRef(resetKeys);
    keys.current = resetKeys;
    const isOpen = id => isSectionOpen(choice, resetKeys, defaults, id);
    const setOpen = useCallback((ids, open) => setChoice(c => withSections(c, keys.current, ids, open)), []);
    return {isOpen, setOpen};
}
