// react-redux is the host's (a Module Federation singleton with import:false), never installed: unit tests
// read an empty store.
export const useSelector = selector => selector({});
