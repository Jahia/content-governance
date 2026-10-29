package org.jahia.community.contentgovernance.compliance.model;

/**
 * Outcome of a scan.
 */
public enum ScanStatus {
    /** At least one glossary applies to the language and no content uses any of its terms. */
    SAFE,
    /** At least one content uses a non-authorised term. */
    ISSUES,
    /** No glossary with terms exists for the language: nothing was checked. */
    NO_GLOSSARY
}
