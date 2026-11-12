import {describe, expect, it} from 'vitest';
import {languageDisplayName, toLocaleTag} from '../../javascript/common/locale.js';

describe('toLocaleTag', () => {
    it('turns a Jahia locale into the tag the browser formatters take', () => {
        expect(toLocaleTag('zh_CN')).toBe('zh-CN');
        expect(toLocaleTag('pt_BR')).toBe('pt-BR');
        expect(toLocaleTag('fr')).toBe('fr');
        expect(toLocaleTag('en-US')).toBe('en-US');
    });

    it('keeps the script of a Java locale', () => {
        expect(toLocaleTag('sr_RS_#Latn')).toBe('sr-Latn-RS');
    });

    it('gives the browser language, never an error, for what it cannot read', () => {
        expect(toLocaleTag(undefined)).toBeUndefined();
        expect(toLocaleTag('')).toBeUndefined();
        expect(toLocaleTag('no_NO_NY')).toBeUndefined();
    });

    it('is needed: the formatters refuse a Jahia locale as it is', () => {
        expect(() => new Date(0).toLocaleString('zh_CN')).toThrow(RangeError);
        expect(() => new Intl.DisplayNames(['zh_CN'], {type: 'language'})).toThrow(RangeError);
    });

    it('formats a date in a Jahia locale without throwing', () => {
        expect(() => new Date(0).toLocaleString(toLocaleTag('zh_CN'))).not.toThrow();
        expect(() => new Intl.NumberFormat(toLocaleTag('zh_TW')).format(1473)).not.toThrow();
    });
});

describe('languageDisplayName', () => {
    it('names a Jahia locale in the editor language, a Jahia locale too', () => {
        expect(languageDisplayName('zh_CN', 'en')).toBe('Chinese (China)');
        expect(languageDisplayName('fr', 'zh_CN')).toBe('法语');
    });

    it('falls back to the code, or the fallback given, when the browser cannot name it', () => {
        expect(languageDisplayName('no_NO_NY', 'en')).toBe('no_NO_NY');
        expect(languageDisplayName('no_NO_NY', 'en', null)).toBeNull();
    });
});
