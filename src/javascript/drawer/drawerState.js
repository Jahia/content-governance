/**
 * What the drawer shows: `null` while it is closed, else `{panel, contexts}`, the key of the panel
 * in view and the context each panel was last opened with. One drawer holds every panel, so opening
 * a panel while another is in view switches to it.
 */

/** Shows a panel, each panel keeping its context. */
export const showPanel = (state, id) => ({panel: id, contexts: state?.contexts || {}});

/**
 * A press on the button of a panel: opens the drawer on it, switches to it, or closes the drawer
 * when it is already in view. The drawer docks beside the page and leaves the buttons in view.
 */
export const pressButton = (state, id) => (state?.panel === id ? null : showPanel(state, id));

/**
 * A request of `openPanel`: shows a panel with a context. The panel gets a new object on each
 * request, so it can tell a second request with the same values from a render.
 */
export const openWith = (state, id, context) => {
    if (!context) {
        return showPanel(state, id);
    }
    return {panel: id, contexts: {...state?.contexts, [id]: {...context}}};
};
