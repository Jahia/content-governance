// What the tab Report shows when the status of the report could not be read. Only an answer of the
// server says whether a model writes the report: a refused or failed call says nothing about it.

/** The access that a 403 of the server answers: the caller may not see the report of the site. */
const NO_ACCESS = 403;

/** The state of the panel after a failed status call: no access on a 403, else unreachable with a retry. */
export const panelStateAfter = error => (error?.code === NO_ACCESS
    ? {status: {enabled: false, reason: 'noAccess'}, phase: 'idle'}
    : {status: null, phase: 'unreachable'});

/** Whether the dashboard offers the tab after a failed status call: yes, unless the server denied access. */
export const tabOfferedAfter = error => error?.code !== NO_ACCESS;
