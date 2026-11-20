package org.jahia.community.contentgovernance.compliance.model;

/**
 * How often one rule is broken across a site.
 *
 * @param ruleId      the id of the rule
 * @param term        the pattern of the rule
 * @param folder      path of the folder that holds the rule, may be {@code null}
 * @param reason      the advice of the rule, may be {@code null}
 * @param alternative the replacement that the rule names, may be {@code null}
 * @param uses        number of uses
 * @param locations   number of pages and content items that use it
 */
public record TermUsage(String ruleId, String term, String folder, String reason, String alternative, int uses,
                        int locations) {
}
