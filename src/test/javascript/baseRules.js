import {readdirSync, readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {ruleBook} from '../../javascript/audit/rules.js';

const FOLDER = fileURLToPath(new URL('../../main/resources/content-rules/', import.meta.url));

/**
 * A value of a frontmatter line: a flag, a number, or the text itself.
 *
 * @param {string} text the text after the colon
 * @returns {boolean|number|string} the value
 */
const valueOf = text => {
    if (text === 'true' || text === 'false') {
        return text === 'true';
    }
    const number = Number(text);
    return Number.isNaN(number) ? text : number;
};

/**
 * The frontmatter of a rule file. The files of the module hold one `key: value` per line.
 *
 * @param {string} text the text of the file
 * @returns {object} the keys of the frontmatter
 */
const frontmatter = text => {
    const rule = {};
    for (const line of text.split('---\n')[1].split('\n')) {
        const colon = line.indexOf(':');
        if (colon > 0) {
            rule[line.slice(0, colon).trim()] = valueOf(line.slice(colon + 1).trim());
        }
    }
    return rule;
};

/** The rules that the module ships, as a site that changed none applies them. */
const SHIPPED = readdirSync(FOLDER).filter(name => name.endsWith('.md')).sort()
    .map(name => frontmatter(readFileSync(FOLDER + name, 'utf8')));

/** The rules of a site that changed none: the rule files of the module. */
export const BASE_RULES = ruleBook(SHIPPED);

/**
 * The base rules with one rule changed, as a site changes it.
 *
 * @param {string} id the id of the rule
 * @param {object} change the fields that change, such as {minimum: 40}
 * @returns {{get: function(string): object}} the rules
 */
export const rulesWith = (id, change) => ruleBook(SHIPPED.map(rule => (rule.id === id ? {...rule, ...change} : rule)));
