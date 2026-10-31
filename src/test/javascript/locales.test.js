import {readFileSync, readdirSync} from 'node:fs';
import {describe, expect, it} from 'vitest';

// Every language file holds every key of en.json with the same placeholders: init.js loads one file
// only, so a key missing there shows as a raw key, and a dropped {{placeholder}} loses a value.
const DIR = new URL('../../main/resources/javascript/locales/', import.meta.url);
const PLACEHOLDER = /\{\{\s*[^}]+?\s*\}\}/g;
const read = file => JSON.parse(readFileSync(new URL(file, DIR), 'utf8'))['content-governance'];
const flat = (value, prefix = '') => (value && typeof value === 'object' ?
    Object.entries(value).flatMap(([key, v]) => flat(v, prefix ? `${prefix}.${key}` : key)) :
    [[prefix, value]]);
const placeholders = text => (text.match(PLACEHOLDER) || []).sort((a, b) => a.localeCompare(b));

const english = new Map(flat(read('en.json')));
const others = readdirSync(DIR).filter(file => file.endsWith('.json') && file !== 'en.json');

describe('locale files', () => {
    it('ships English, French, German and Spanish', () => {
        expect(others.sort((a, b) => a.localeCompare(b))).toEqual(['de.json', 'es.json', 'fr.json']);
    });

    it.each(others)('%s has the keys of en.json and their placeholders', file => {
        const translated = new Map(flat(read(file)));
        expect([...translated.keys()].sort((a, b) => a.localeCompare(b))).toEqual([...english.keys()].sort((a, b) => a.localeCompare(b)));
        const wrong = [...english].filter(([key, text]) => {
            const other = translated.get(key);
            if (typeof other !== 'string' || other.trim() === '') {
                return true;
            }
            // The singular of a plural pair may leave the number out ("L'adresse la plus citée")
            const expected = english.has(`${key}_plural`) || english.has(`${key}_other`) ?
                placeholders(text).filter(p => !/\{\{\s*count\s*\}\}/.test(p) || placeholders(other).includes(p)) :
                placeholders(text);
            return placeholders(other).join() !== expected.join();
        }).map(([key]) => key);
        expect(wrong).toEqual([]);
    });
});
