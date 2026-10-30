import {moduleUrl} from '../../common/url';

const ENDPOINT = moduleUrl('/geo/site-files');

async function call(body) {
    const res = await fetch(ENDPOINT, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: JSON.stringify(body)
    });
    if (!res.ok) {
        const err = new Error('siteFiles');
        err.code = res.status;
        throw err;
    }
    return res.json();
}

/** The llms.txt the llms module publishes for the site, read from live. Writes nothing. */
export const readLlms = ({path, language}) => call({action: 'readLlms', path, language});

/** Merges the decisions server-side and returns current + proposed. Writes nothing. */
export const previewRobots = ({path, language, decisions}) =>
    call({action: 'previewRobots', path, language, decisions});

/** Writes exactly `content`. Only ever called after the editor has seen the diff. */
export const applyRobots = ({path, language, content}) =>
    call({action: 'applyRobots', path, language, content});
