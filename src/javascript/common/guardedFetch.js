/**
 * fetch for the module's servlets, which sit under Jahia's CSRF guard. The guard's own page script
 * only covers XMLHttpRequest to *.do, so this sends the token itself (jahia-csrf-guard 4.x):
 *
 * - the session's master token is read once from /modules/CsrfServlet, with the page tokens it knows;
 * - each request sends the page token of its address, or the master token the first time, and keeps
 *   the page token the guard answers with (the master token stops working for an address once it has
 *   one);
 * - a refusal (a redirect to /error.html) rotates every token of the session: the tokens are loaded
 *   again, other tabs are told to drop theirs, and the request is sent once more. A second refusal
 *   comes back as a 403, which callers already report as an error.
 *
 * Page tokens do not change between requests, so parallel requests share them safely.
 */
const MASTER_TOKEN = /var\s+[\w$]+\s*=\s*'([A-Za-z][A-Za-z0-9_-]*)'\s*,\s*[\w$]+\s*=\s*'([A-Za-z0-9-]{8,})'/;
const REQUESTED_WITH = {'X-Requested-With': 'XMLHttpRequest'};

const channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('content-governance-csrf');
let tokens = null;
let loading = null;

if (channel) {
    channel.onmessage = () => {
        tokens = null;
    };
}

const guardServlet = () => `${globalThis.contextJsParameters?.contextPath || ''}/modules/CsrfServlet`;

/** The session's tokens, or no name when the guard gives none (switched off): requests then go without. */
async function load() {
    const script = await fetch(guardServlet(), {credentials: 'same-origin', cache: 'no-store'}).catch(() => null);
    const match = script?.ok ? MASTER_TOKEN.exec(await script.text()) : null;
    if (!match) {
        return {name: null, pages: {}};
    }

    const [, name, master] = match;
    const pages = await fetch(guardServlet(), {
        method: 'POST',
        credentials: 'same-origin',
        headers: {...REQUESTED_WITH, [name]: master}
    }).then(res => (res.ok ? res.json() : {}), () => ({}));
    return {name, master, pages: pages.pageTokens || {}};
}

function currentTokens() {
    if (tokens) {
        return Promise.resolve(tokens);
    }

    loading ||= load()
        .then(loaded => {
            tokens = loaded;
            return loaded;
        })
        .finally(() => {
            loading = null;
        });
    return loading;
}

/** The guard answers {"pageTokens": {address: token}} in its header. Ignored once the tokens were reloaded. */
function remember(sent, address, res) {
    const answer = sent.name && res.headers.get(sent.name);
    if (!answer || sent !== tokens) {
        return;
    }

    try {
        Object.assign(sent.pages, JSON.parse(answer).pageTokens || {});
    } catch {
        sent.pages[address] = answer;
    }
}

async function send(url, init) {
    const sent = await currentTokens();
    const address = new URL(url, globalThis.location.origin).pathname;
    // The master token works once per address: the first answer brings the address's page token.
    // While that first request is out, another request to the address waits for it instead of being
    // refused (the GEO dashboard sends several to one servlet at once). The check and the claim below
    // have no await between them, so parallel calls cannot both pass.
    while (sent.name && !sent.pages[address] && sent.firstUse?.[address]) {
        await sent.firstUse[address];
    }
    let done = null;
    if (sent.name && !sent.pages[address]) {
        sent.firstUse ||= {};
        sent.firstUse[address] = new Promise(resolve => {
            done = resolve;
        });
    }
    try {
        const res = await fetch(url, {
            ...init,
            credentials: 'same-origin',
            redirect: 'manual',
            headers: {
                ...init.headers,
                ...REQUESTED_WITH,
                ...(sent.name ? {[sent.name]: sent.pages[address] || sent.master} : {})
            }
        });
        if (res.type === 'opaqueredirect') {
            return {sent};
        }

        remember(sent, address, res);
        return {res};
    } finally {
        if (done) {
            delete sent.firstUse[address];
            done();
        }
    }
}

export async function guardedFetch(url, init = {}) {
    const first = await send(url, init);
    if (first.res) {
        return first.res;
    }

    if (tokens === first.sent) {
        tokens = null;
    }

    channel?.postMessage('rotated');
    const second = await send(url, init);
    return second.res || new Response(JSON.stringify({error: 'refused'}), {
        status: 403,
        headers: {'Content-Type': 'application/json'}
    });
}
