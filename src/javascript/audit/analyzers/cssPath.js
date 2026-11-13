/** Stable CSS selector for an element of the preview document (for highlight after the audit). */
export function cssPath(el) {
    const doc = el.ownerDocument;
    const win = doc.defaultView;
    const escape = id => (win && win.CSS && win.CSS.escape ? win.CSS.escape(id) : id);
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && node !== doc.body) {
        if (node.id) {
            parts.unshift(`#${escape(node.id)}`);
            break;
        }

        let selector = node.tagName.toLowerCase();
        const parent = node.parentElement;
        if (parent) {
            const siblings = Array.from(parent.children).filter(c => c.tagName === node.tagName);
            if (siblings.length > 1) {
                selector += `:nth-of-type(${siblings.indexOf(node) + 1})`;
            }
        }

        parts.unshift(selector);
        node = parent;
    }

    return parts.join(' > ');
}
