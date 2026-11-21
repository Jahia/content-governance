import {readdirSync, readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {ruleBook} from '../../javascript/audit/rules.js';

const FOLDER = fileURLToPath(new URL('../../main/resources/content-rules/', import.meta.url));

/**
 * A value of a frontmatter line: a quoted text, a list such as `[seo, geo.score]`, a flag, a number, or the
 * text itself.
 *
 * @param {string} text the text after the colon
 * @returns {boolean|number|string|Array<string>} the value
 */
const valueOf = text => {
    if (text.startsWith('[')) {
        return text.slice(1, -1).split(',').map(item => item.trim()).filter(Boolean);
    }
    if (text.startsWith('"')) {
        return JSON.parse(text);
    }
    if (text === 'true' || text === 'false') {
        return text === 'true';
    }
    const number = Number(text);
    return Number.isNaN(number) ? text : number;
};

/**
 * The frontmatter of a rule file. The files of the module hold one `key: value` per line, and the texts
 * of a rule as maps of languages, then of plural forms, indented by two spaces at each level.
 *
 * @param {string} text the text of the file
 * @returns {object} the keys of the frontmatter, and `body`
 */
const frontmatter = text => {
    const [, head, ...rest] = text.split('---\n');
    const rule = {body: rest.join('---\n').trim()};
    const path = [];
    for (const line of head.split('\n')) {
        const colon = line.indexOf(':');
        if (colon <= 0) {
            continue;
        }
        const depth = (line.length - line.trimStart().length) / 2;
        const key = line.slice(0, colon).trim();
        const raw = line.slice(colon + 1).trim();
        path.length = depth;
        const parent = path.reduce((map, name) => map[name], rule);
        if (raw === '') {
            parent[key] = {};
            path.push(key);
        } else {
            parent[key] = valueOf(raw);
        }
    }
    return rule;
};

/** The forms of a title, as the GraphQL field `title` gives them. */
const formsOf = title => {
    if (!title) {
        return [];
    }
    return typeof title === 'string' ? [{form: 'other', text: title}] : Object.entries(title).map(([form, text]) => ({form, text}));
};

/** The rule of a file as `contentRules(siteKey, language)` gives it: its texts in the language, else in English. */
const inLanguage = (rule, language) => {
    const {body, title, label, advice, ...values} = rule;
    return {
        ...values,
        title: formsOf(title?.[language] || title?.en),
        label: label?.[language] || label?.en || null,
        advice: advice?.[language] || body || ''
    };
};

/** The rule files of the module. */
const FILES = readdirSync(FOLDER).filter(name => name.endsWith('.md')).sort()
    .map(name => frontmatter(readFileSync(FOLDER + name, 'utf8')));

const shipped = language => FILES.map(rule => inLanguage(rule, language));

/** The rules of a site that changed none: the rule files of the module, worded in English. */
export const BASE_RULES = ruleBook(shipped('en'));

/**
 * The rules of a site that changed none, worded in a language.
 *
 * @param {string} language a language such as fr
 * @returns {object} the rules
 */
export const rulesIn = language => ruleBook(shipped(language), language);

/**
 * The base rules with one rule changed, as a site changes it.
 *
 * @param {string} id the id of the rule
 * @param {object} change the fields that change, such as {minimum: 40}
 * @returns {{get: function(string): object}} the rules
 */
export const rulesWith = (id, change) => ruleBook(shipped('en').map(rule => (rule.id === id ? {...rule, ...change} : rule)));
