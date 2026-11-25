/**
 * The tabs of other modules that a subdomain of the layout holds. A module ships a subdomain whose id is the key of
 * its tab (docs/drawer-entries.md): the tab then shows as the section of that subdomain, in the domain where the
 * site places it, and not in the section bar. Example: ai-assistant ships the subdomain `aiAssistantVoice` in
 * Compliance, and its Voice tab shows as a section of Compliance.
 */

/**
 * @param {Array<{subdomains: Array<{id: string}>}>} domains the domains of the governance panel (drawer/layout.js)
 * @param {Array<{id: string}>} tabs the tabs of other modules that the site keeps on
 * @returns {Map<string, object>} the tabs that a subdomain of a domain holds, by id
 */
export function heldTabs(domains, tabs) {
    const subdomains = new Set(domains.flatMap(domain => domain.subdomains.map(subdomain => subdomain.id)));
    return new Map(tabs.filter(tab => subdomains.has(tab.id)).map(tab => [tab.id, tab]));
}

/**
 * The domain without the subdomains that hold a tab: what the checks of the module read and count.
 *
 * @param {{subdomains: Array<{id: string}>}} domain a domain of the layout
 * @param {Map<string, object>|Set<string>} held what `heldTabs` gives, or the ids of its tabs
 * @returns {object} the domain, the same object when it holds no tab
 */
export function withoutHeld(domain, held) {
    const own = domain.subdomains.filter(subdomain => !held.has(subdomain.id));
    return own.length === domain.subdomains.length ? domain : {...domain, subdomains: own};
}

/**
 * The tabs that a domain holds and that the page can use: they report findings.
 *
 * @param {{subdomains: Array<{id: string}>}} domain a domain of the layout
 * @param {Map<string, object>} held what `heldTabs` gives
 * @returns {Array<string>} their ids, in the order of the layout
 */
export const usableHeldIn = (domain, held) => domain.subdomains
    .filter(subdomain => held.has(subdomain.id) && !held.get(subdomain.id).unavailable)
    .map(subdomain => subdomain.id);

/**
 * The sections of a domain in the order of the layout: the sections that the checks of the module give, and a
 * section for each tab that a subdomain holds, with the count that the tab reports.
 *
 * @param {{subdomains: Array<{id: string, label: string, help: ?string}>}} domain a domain of the layout
 * @param {Array<{id: string}>} own the sections of `withoutHeld(domain)` (drawer/sections.js)
 * @param {Map<string, object>} held what `heldTabs` gives
 * @param {Object<string, number>} counts the count that each tab reported
 * @param {function(string): boolean} isSerious whether the findings of a tab hold a critical or serious one
 * @returns {Array<object>} the sections; the section of a tab has `entry`, the tab as `heldTabs` gives it
 */
export function withHeldSections(domain, own, held, counts, isSerious) {
    const byId = new Map(own.map(section => [section.id, section]));
    return domain.subdomains.map(subdomain => {
        const tab = held.get(subdomain.id);
        if (!tab) {
            return byId.get(subdomain.id);
        }
        const count = typeof counts[subdomain.id] === 'number' ? counts[subdomain.id] : null;
        return {
            id: subdomain.id,
            label: subdomain.label,
            help: subdomain.help,
            count: tab.unavailable ? null : count,
            serious: !tab.unavailable && isSerious(subdomain.id),
            unavailable: false,
            entry: tab
        };
    });
}
