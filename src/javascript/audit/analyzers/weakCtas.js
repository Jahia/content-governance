/**
 * The weak calls to action of a page, for the SEO assist of the AI: links and buttons whose label says
 * nothing about where they lead.
 */

// Link / button labels that say nothing about the destination or the action.
// Compared after stripping decorations ("Read More +", "En savoir plus »").
const GENERIC_ANCHORS = /^(click here|here|read more|more|learn more|link|see more|view more|submit|send|go|ok|continue|next|cliquez ici|ici|en savoir plus|lire la suite|plus|voir plus|découvrir|envoyer|soumettre|valider|continuer|suivant)$/i;
const MAX_WEAK_CTAS = 10;

const DECORATION = /[\W_]/;

/**
 * A link or button label without what decorates it at either end ("Read More +" gives "Read More").
 * A loop, not a pattern anchored at the end: that one backtracks on a long label (Sonar S5852).
 *
 * @param {string} [text] the label as the page shows it
 * @returns {string} the label, its inner white space collapsed
 */
export const normalizeLabel = text => {
    const label = (text || '').replaceAll(/\s+/g, ' ');
    let start = 0;
    let end = label.length;
    while (start < end && DECORATION.test(label[start])) {
        start++;
    }
    while (end > start && DECORATION.test(label[end - 1])) {
        end--;
    }
    return label.slice(start, end);
};

/**
 * Weak calls to action: links and buttons whose label is generic. Returns
 * distinct labels with a sample of the surrounding text, for the AI assist.
 */
export function collectWeakCtas(doc) {
    const controls = Array.from(doc.querySelectorAll('a[href], button, input[type="submit"], input[type="button"]'));
    const seen = new Map();
    controls.forEach(el => {
        const raw = el.tagName === 'INPUT' ? el.value : el.textContent;
        const label = normalizeLabel(raw);
        if (!label || !GENERIC_ANCHORS.test(label)) {
            return;
        }

        const key = label.toLowerCase();
        if (seen.has(key)) {
            seen.get(key).count++;
            return;
        }

        // Walk up until the block carries real text beyond the label itself
        // (the link's own wrapper usually contains nothing else)
        let block = el.parentElement;
        while (block && block !== doc.body && (block.textContent || '').replaceAll(/\s+/g, ' ').trim().length < label.length + 40) {
            block = block.parentElement;
        }

        seen.set(key, {
            text: (raw || '').replaceAll(/\s+/g, ' ').trim(),
            count: 1,
            target: el.getAttribute('href') || '',
            context: block ? (block.textContent || '').replaceAll(/\s+/g, ' ').trim().slice(0, 300) : ''
        });
    });

    return Array.from(seen.values()).slice(0, MAX_WEAK_CTAS);
}
