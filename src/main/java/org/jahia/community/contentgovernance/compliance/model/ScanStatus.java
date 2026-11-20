package org.jahia.community.contentgovernance.compliance.model;

/**
 * Outcome of a scan.
 */
public enum ScanStatus {
    /** At least one rule applies to the language and no content breaks any. */
    SAFE,
    /** At least one content breaks a rule. */
    ISSUES,
    /** No rule of the evaluator pattern applies to the language: nothing was checked. */
    NO_GLOSSARY
}
