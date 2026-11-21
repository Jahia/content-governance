import {createContext} from 'react';

/**
 * Marks an element of the page by its CSS selector, in the page under edit or in the preview, and answers
 * whether the page has it. The Page quality section gives it to every tab, so that a finding of any rule
 * with a locator offers "Highlight on the page".
 */
export const HighlightContext = createContext(null);
