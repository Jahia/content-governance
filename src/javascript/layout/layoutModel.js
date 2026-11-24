/**
 * The draft of the Rules & domains screen, and the moves that change it. The draft is what the screen saves as the
 * layout of the site: the domains in their order, each with its subdomains in their order, each with the ids of its
 * rules in their order, and the ids that are off. `docs/content-rules/catalog.md`, section "The layout of a site",
 * states the JSON.
 *
 * Every function returns a new draft and leaves the one it gets as it is.
 */

/** The levels of the hierarchy, from the top. */
export const DOMAIN = 'domain';
export const SUBDOMAIN = 'subdomain';
export const RULE = 'rule';

/** The level that holds the items of a level. */
const PARENT_LEVEL = {[SUBDOMAIN]: DOMAIN, [RULE]: SUBDOMAIN};

/**
 * The draft that the screen starts from.
 *
 * @param {Array<object>} domains `contentDomains` with `includeOff`, in the order of the site: `{id, switchedOn,
 *   subdomains: [{id, switchedOn}]}`
 * @param {Array<object>} rules `contentRules`: `{id, subdomain, order, switchedOn}`; a rule whose subdomain is not
 *   among the subdomains is left out, since the layout cannot place it
 * @param {Array<string>} switchedOff the ids that the layout of the site switches off, such as the panels
 * @returns {{domains: Array<object>, off: Set<string>}} the draft
 */
export function draftOf(domains, rules, switchedOff = []) {
    const off = new Set(switchedOff);
    const bySubdomain = new Map();
    domains.forEach(domain => domain.subdomains.forEach(subdomain => bySubdomain.set(subdomain.id, [])));
    [...rules].sort((a, b) => a.order - b.order).forEach(rule => {
        if (bySubdomain.has(rule.subdomain)) {
            bySubdomain.get(rule.subdomain).push(rule.id);
            if (!rule.switchedOn) {
                off.add(rule.id);
            }
        }
    });
    return {
        domains: domains.map(domain => {
            if (!domain.switchedOn) {
                off.add(domain.id);
            }
            return {
                id: domain.id,
                subdomains: domain.subdomains.map(subdomain => {
                    if (!subdomain.switchedOn) {
                        off.add(subdomain.id);
                    }
                    return {id: subdomain.id, rules: bySubdomain.get(subdomain.id)};
                })
            };
        }),
        off
    };
}

/**
 * @param {object} draft the draft
 * @returns {{domains: Array<object>, off: Array<string>}} the layout that `saveSiteLayout` takes, as JSON
 */
export const toLayout = draft => ({
    domains: draft.domains.map(domain => ({
        id: domain.id,
        subdomains: domain.subdomains.map(subdomain => ({id: subdomain.id, rules: [...subdomain.rules]}))
    })),
    off: [...draft.off].sort((a, b) => a.localeCompare(b))
});

/** True when two drafts save the same layout. */
export const sameLayout = (a, b) => JSON.stringify(toLayout(a)) === JSON.stringify(toLayout(b));

/** The draft with the item of this id switched on or off. */
export function toggle(draft, id, on) {
    const off = new Set(draft.off);
    if (on) {
        off.delete(id);
    } else {
        off.add(id);
    }
    return {...draft, off};
}

/** Moves the item at `from` of a list to `to`, which counts the places in the list without the item. */
const moved = (list, from, to) => {
    const out = [...list];
    const [item] = out.splice(from, 1);
    out.splice(Math.max(0, Math.min(to, out.length)), 0, item);
    return out;
};

/** The index of an item in a subdomain: the subdomain itself at `s`, or a rule among its rules. */
const indexIn = (level, subdomain, s, id) => {
    if (level === SUBDOMAIN) {
        return subdomain.id === id ? s : -1;
    }
    return subdomain.rules.indexOf(id);
};

/**
 * Where an item is: the index of its domain, of its subdomain and of itself.
 *
 * @returns {{domain: number, subdomain: number, index: number}|null} null when the draft has no such item
 */
export function find(draft, level, id) {
    if (level === DOMAIN) {
        const d = draft.domains.findIndex(domain => domain.id === id);
        return d < 0 ? null : {domain: d, subdomain: -1, index: d};
    }
    for (let d = 0; d < draft.domains.length; d++) {
        const subdomains = draft.domains[d].subdomains;
        for (let s = 0; s < subdomains.length; s++) {
            const index = indexIn(level, subdomains[s], s, id);
            if (index >= 0) {
                return {domain: d, subdomain: s, index};
            }
        }
    }
    return null;
}

/** The parent of a subdomain or a rule at a place that `find` gives: its domain or its subdomain. */
const parentAt = (draft, level, at) => (level === SUBDOMAIN ? draft.domains[at.domain] :
    draft.domains[at.domain].subdomains[at.subdomain]);

/**
 * Moves an item. A domain moves among the domains; a subdomain moves into a domain, a rule into a subdomain.
 *
 * @param {object} draft the draft
 * @param {string} level DOMAIN, SUBDOMAIN or RULE
 * @param {string} id the id of the item
 * @param {object} target `{index}` for a domain; `{parent, index}` for a subdomain or a rule, where `parent` is the
 *   id of the domain or the subdomain, and `index` the place among its items once the item left its old place.
 *   An index past the end puts the item last.
 * @returns {object} the draft, the same one when the item or the parent is missing
 */
export function move(draft, level, id, target) {
    const at = find(draft, level, id);
    if (!at) {
        return draft;
    }
    if (level === DOMAIN) {
        return {...draft, domains: moved(draft.domains, at.index, target.index)};
    }
    const to = find(draft, PARENT_LEVEL[level], target.parent);
    if (!to) {
        return draft;
    }
    const domains = draft.domains.map(domain => ({...domain, subdomains: domain.subdomains.map(s => ({...s, rules: [...s.rules]}))}));
    if (level === SUBDOMAIN) {
        const [item] = domains[at.domain].subdomains.splice(at.index, 1);
        const list = domains[to.domain].subdomains;
        list.splice(Math.max(0, Math.min(target.index, list.length)), 0, item);
    } else {
        const [item] = domains[at.domain].subdomains[at.subdomain].rules.splice(at.index, 1);
        const list = domains[to.domain].subdomains[to.subdomain].rules;
        list.splice(Math.max(0, Math.min(target.index, list.length)), 0, item);
    }
    return {...draft, domains};
}

/** Moves an item before another item of its level. */
function dropBefore(draft, item, over) {
    const from = find(draft, item.level, item.id);
    const at = find(draft, over.level, over.id);
    if (!from || !at) {
        return draft;
    }
    if (item.level === DOMAIN) {
        return move(draft, DOMAIN, item.id, {index: at.index > from.index ? at.index - 1 : at.index});
    }
    const parent = parentAt(draft, item.level, at);
    const sameParent = parentAt(draft, item.level, from).id === parent.id;
    return move(draft, item.level, item.id, {parent: parent.id, index: sameParent && at.index > from.index ? at.index - 1 : at.index});
}

/**
 * Moves an item before another item of its level, the way a drop on that item does. A subdomain dropped on a domain,
 * or a rule dropped on a subdomain, goes last in it.
 *
 * @param {object} draft the draft
 * @param {{level: string, id: string}} item what moves
 * @param {{level: string, id: string}} over where it drops
 * @returns {object} the draft, the same one for a drop that means nothing, such as a domain on a rule
 */
export function drop(draft, item, over) {
    if (item.level === over.level) {
        return item.id === over.id ? draft : dropBefore(draft, item, over);
    }
    if (PARENT_LEVEL[item.level] === over.level) {
        return move(draft, item.level, item.id, {parent: over.id, index: Number.MAX_SAFE_INTEGER});
    }
    return draft;
}

/**
 * The moves that the "Move to" menu of an item offers, from the keyboard: up, down, and into each other parent.
 *
 * @param {object} draft the draft
 * @param {string} level DOMAIN, SUBDOMAIN or RULE
 * @param {string} id the id of the item
 * @returns {{up: boolean, down: boolean, parents: Array<string>}} whether it can go up or down, and the ids of the
 *   other domains (for a subdomain) or the other subdomains (for a rule) that it can go into
 */
export function moveOptions(draft, level, id) {
    const at = find(draft, level, id);
    if (!at) {
        return {up: false, down: false, parents: []};
    }
    if (level === DOMAIN) {
        return {up: at.index > 0, down: at.index < draft.domains.length - 1, parents: []};
    }
    if (level === SUBDOMAIN) {
        const siblings = draft.domains[at.domain].subdomains;
        return {
            up: at.index > 0,
            down: at.index < siblings.length - 1,
            parents: draft.domains.filter((d, index) => index !== at.domain).map(d => d.id)
        };
    }
    const current = draft.domains[at.domain].subdomains[at.subdomain];
    return {
        up: at.index > 0,
        down: at.index < current.rules.length - 1,
        parents: draft.domains.flatMap(d => d.subdomains).filter(s => s.id !== current.id).map(s => s.id)
    };
}

/**
 * Moves an item one place up or down among its siblings.
 *
 * @param {number} step -1 for up, 1 for down
 */
export function shift(draft, level, id, step) {
    const at = find(draft, level, id);
    if (!at) {
        return draft;
    }
    if (level === DOMAIN) {
        return move(draft, DOMAIN, id, {index: at.index + step});
    }
    return move(draft, level, id, {parent: parentAt(draft, level, at).id, index: Math.max(0, at.index + step)});
}
