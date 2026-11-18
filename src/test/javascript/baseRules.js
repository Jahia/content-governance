import catalog from '../../main/resources/META-INF/content-rules/catalog.json';
import {ruleBook} from '../../javascript/audit/rules.js';

/** The rules of a site that changed none: the catalog file of the module. */
export const BASE_RULES = ruleBook(catalog.rules);

/**
 * The base rules with one rule changed, as a site changes it.
 *
 * @param {string} id the id of the rule
 * @param {object} change the fields that change, such as {minimum: 40}
 * @returns {{get: function(string): object}} the rules
 */
export const rulesWith = (id, change) => ruleBook(catalog.rules.map(rule => (rule.id === id ? {...rule, ...change} : rule)));
