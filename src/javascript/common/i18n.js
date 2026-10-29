import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';

/** The module's i18next namespace: every label of the module lives in it. */
export const NS = 'content-governance';

/**
 * `useTranslation` scoped to one part of the module: `useGovernanceTranslation('geo')` returns a
 * `t` that reads `geo.<key>`. Implemented here rather than with react-i18next's `keyPrefix`,
 * which depends on the i18next copy the host elects as the shared singleton.
 *
 * @param {string} [prefix] the key prefix (`common`, `drawer`, `settings`, `audit`, `geo`, `compliance`)
 * @returns {{t: Function, i18n: object}}
 */
export function useGovernanceTranslation(prefix) {
    const {t, i18n} = useTranslation(NS);
    const scoped = useCallback((key, options) => t(prefix ? `${prefix}.${key}` : key, options), [t, prefix]);
    return {t: scoped, i18n};
}
