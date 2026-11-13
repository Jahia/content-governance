/**
 * Items with a React key each, taken from what they hold rather than from their index:
 * `keyOf(item)`, plus a rank that keeps apart two items giving the same key.
 *
 * @param {Array} items the items of a list
 * @param {Function} keyOf what names an item (a title, a selector, a line of text)
 * @returns {Array<{item: *, key: string}>} the items in their order, each with its key
 */
export function withKeys(items, keyOf) {
    const seen = new Map();
    return items.map(item => {
        const base = keyOf(item);
        const rank = seen.get(base) || 0;
        seen.set(base, rank + 1);
        return {item, key: `${base}#${rank}`};
    });
}
