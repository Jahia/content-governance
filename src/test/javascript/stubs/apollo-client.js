// @apollo/client is the host's (a Module Federation singleton with import:false), never installed:
// unit tests get the template tag only, and pass a fake client to the code under test.
export const gql = (strings, ...values) => strings.reduce((query, part, i) => query + part + (values[i] ?? ''), '');
