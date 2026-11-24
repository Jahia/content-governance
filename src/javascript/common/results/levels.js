/**
 * The level of a result card: the one scale every check of the drawer shows, whatever scale its
 * check uses. A site's rules keep their four severities; only the drawer folds them into three.
 *
 * Example: a GEO check of severity `important` and a page rule of severity `moderate` both show as
 * Moderate.
 */
const LEVEL_OF = {
    // Page rules, axe-core impacts and compliance rules
    critical: 'serious',
    serious: 'serious',
    moderate: 'moderate',
    minor: 'minor',
    // GEO checks (critical is above)
    important: 'moderate',
    advisory: 'minor'
};

/** The levels a card can take: the three of a finding, then a check that passed and one to verify by hand. */
export const LEVELS = ['serious', 'moderate', 'minor', 'passed', 'review'];

/**
 * @param {?string} severity the severity of a finding in its check's scale
 * @returns {string} its level: `serious`, `moderate` or `minor` (`minor` when unknown)
 */
export const levelOf = severity => LEVEL_OF[severity] || 'minor';

/** The order of the levels, most serious first, for sorting cards. */
export const LEVEL_RANK = {serious: 0, moderate: 1, minor: 2, review: 3, passed: 4};
