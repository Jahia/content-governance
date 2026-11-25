import {describe, expect, it} from 'vitest';
import {rememberPanel, rememberedState} from '../../javascript/drawer/drawerMemory.js';

const fakeStorage = () => {
    const values = new Map();
    return {
        getItem: key => (values.has(key) ? values.get(key) : null),
        setItem: (key, value) => values.set(key, String(value)),
        removeItem: key => values.delete(key)
    };
};

const blockedStorage = {
    getItem: () => {
        throw new Error('blocked');
    },
    setItem: () => {
        throw new Error('blocked');
    },
    removeItem: () => {
        throw new Error('blocked');
    }
};

describe('drawerMemory', () => {
    it('starts closed in a new tab', () => {
        expect(rememberedState(fakeStorage())).toBeNull();
    });

    it('reopens the next page on the panel that was in view, with no context', () => {
        const storage = fakeStorage();
        rememberPanel('chat', storage);
        expect(rememberedState(storage)).toEqual({panel: 'chat', contexts: {}});
    });

    it('keeps the drawer closed on the next page once it was closed', () => {
        const storage = fakeStorage();
        rememberPanel('governance', storage);
        rememberPanel(null, storage);
        expect(rememberedState(storage)).toBeNull();
    });

    it('starts closed when the browser blocks the storage', () => {
        expect(() => rememberPanel('chat', blockedStorage)).not.toThrow();
        expect(rememberedState(blockedStorage)).toBeNull();
    });

    it('starts closed where there is no storage', () => {
        expect(rememberedState(null)).toBeNull();
        expect(() => rememberPanel('chat', null)).not.toThrow();
    });
});
