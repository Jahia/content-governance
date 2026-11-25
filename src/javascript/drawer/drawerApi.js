import {registry} from '@jahia/ui-extender';
import {PassedFold, ResultCard} from '../common/results/ResultCard';
import {FactBlock} from '../common/results/FactBlock';
import {ScoreBlock} from '../common/results/ScoreBlock';
import {ResultBanner} from '../common/results/ResultBanner';

/**
 * How code opens the drawer on a panel: the module's own (the Publish menu, a tab that reopens
 * after an edit), and other modules through the registry entry of `registerDrawerApi`. The header
 * button of the page in view listens (drawer/GovernanceAction.jsx).
 */

/** The registry type of what the module gives other modules. */
export const API_TYPE = 'contentGovernanceApi';

/** The key of the drawer's entry of that type. */
export const DRAWER_API_KEY = 'drawer';

/** The key of the entry of that type that holds the result patterns of the drawer (docs/result-patterns.md). */
export const RESULTS_API_KEY = 'results';

const listeners = new Set();

/**
 * Opens the drawer on a panel, with a context the panel's component receives. The drawer that is
 * already open switches to that panel.
 *
 * @param {string} id the key of the panel, such as `governance` or `chat`
 * @param {object} [context] what the panel opens with, such as `{finding, path, language}`; without
 *     it, the panel keeps the context it has
 * @param {string} [path] only the drawer of this page opens; any drawer when not given
 * @returns {boolean} true when a drawer opened the panel; false when no drawer is in view, or the
 *     panel is not registered, switched off or not available on the page
 */
export function openPanel(id, context, path) {
    const request = {panel: id, context: context ?? null, path: path ?? null};
    return [...listeners].some(listener => listener(request) === true);
}

/**
 * Listens to the requests of `openPanel`.
 *
 * @param {Function} listener given `{panel, context, path}`; returns true when it opened the panel
 * @returns {Function} stops listening
 */
export function onPanelRequest(listener) {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

/** Gives other modules `openPanel(id, context)` as `registry.get('contentGovernanceApi', 'drawer')`. */
export function registerDrawerApi() {
    registry.add(API_TYPE, DRAWER_API_KEY, {
        openPanel: (id, context) => openPanel(id, context)
    });
}

/**
 * Gives other modules the result patterns of the drawer, so that a tab of another module shows its results as the
 * sections of the drawer do: `registry.get('contentGovernanceApi', 'results')` holds `ResultCard`, `PassedFold`,
 * `FactBlock`, `ScoreBlock` and `ResultBanner`.
 */
export function registerResultPatterns() {
    registry.add(API_TYPE, RESULTS_API_KEY, {ResultCard, PassedFold, FactBlock, ScoreBlock, ResultBanner});
}
