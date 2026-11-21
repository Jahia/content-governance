import {registry} from '@jahia/ui-extender';
import {CHECK_TYPE} from '../drawer/entries';

/**
 * The checks of Content Governance that run in the browser. They register as the check of another module
 * does (`docs/drawer-entries.md`), and their code loads the first time the drawer runs them.
 */

/** The evaluators of the eco-design checks, one per measure (checks/ecodesign.js). */
export const ECODESIGN_EVALUATORS = ['ecodesign.pageWeight', 'ecodesign.requests', 'ecodesign.domSize', 'ecodesign.lazyLoading',
    'ecodesign.webFonts', 'ecodesign.heavyAssets', 'ecodesign.autoplay', 'ecodesign.imageFormats', 'ecodesign.oversizedImages',
    'ecodesign.imageDimensions', 'ecodesign.thirdParty'];

/**
 * Adds the checks to the registry: the web vitals and eco-design first, since they measure what the page
 * loaded, then axe-core, which adds its script to the page.
 */
export function registerChecks() {
    registry.add(CHECK_TYPE, 'webVitals', {
        evaluator: 'webVitals',
        run: async context => (await import('./webVitals')).runWebVitals(context)
    });
    ECODESIGN_EVALUATORS.forEach(evaluator => registry.add(CHECK_TYPE, evaluator, {
        evaluator,
        run: async context => (await import('./ecodesign')).runEcodesign(evaluator, context)
    }));
    registry.add(CHECK_TYPE, 'axe', {
        evaluator: 'axe',
        run: async context => (await import('./axe')).runAxe(context)
    });
}
