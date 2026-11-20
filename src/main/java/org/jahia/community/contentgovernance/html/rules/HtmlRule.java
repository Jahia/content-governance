/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

/**
 * The rules that read the HTML that Jahia renders. Each rule has an id, the WCAG 2.2 success
 * criterion it checks ({@code none} when no criterion applies) and its scope.
 *
 * <p>Each id is the id of a rule of the catalog, which a module ships in {@code content-rules}, and the id
 * of the check that applies it. {@code docs/page-quality/html-rules.md} lists them for a reader.</p>
 */
public enum HtmlRule {

    PAGE_LANGUAGE_MISSING("html.pageLanguageMissing", "3.1.1", Scope.PAGE),
    PAGE_LANGUAGE_MISMATCH("html.pageLanguageMismatch", "3.1.1", Scope.PAGE),
    PAGE_TITLE_MISSING("html.pageTitleMissing", "2.4.2", Scope.PAGE),
    PAGE_DESCRIPTION_MISSING("html.pageDescriptionMissing", "none", Scope.PAGE),
    IMAGE_ALT_MISSING("html.imageAltMissing", "1.1.1", Scope.ITEM),
    HEADING_LEVEL_SKIPPED("html.headingLevelSkipped", Wcag.INFO_AND_RELATIONSHIPS, Scope.ITEM),
    HEADING_EMPTY("html.headingEmpty", "2.4.6", Scope.ITEM),
    HEADING_H1_MISSING("html.headingH1Missing", Wcag.INFO_AND_RELATIONSHIPS, Scope.PAGE),
    HEADING_H1_MULTIPLE("html.headingH1Multiple", Wcag.INFO_AND_RELATIONSHIPS, Scope.PAGE),
    LINK_NAME_MISSING("html.linkNameMissing", "2.4.4", Scope.ITEM),
    LINK_TEXT_VAGUE("html.linkTextVague", "2.4.4", Scope.ITEM),
    BUTTON_NAME_MISSING("html.buttonNameMissing", "4.1.2", Scope.ITEM),
    FORM_LABEL_MISSING("html.formLabelMissing", Wcag.INFO_AND_RELATIONSHIPS, Scope.ITEM),
    FRAME_TITLE_MISSING("html.frameTitleMissing", "4.1.2", Scope.ITEM),
    TABLE_HEADER_MISSING("html.tableHeaderMissing", Wcag.INFO_AND_RELATIONSHIPS, Scope.ITEM),
    DUPLICATE_ID("html.duplicateId", "none", Scope.PAGE);

    /** A WCAG criterion that several rules check. An enum constant cannot read a static field of its own enum. */
    private static final class Wcag {
        static final String INFO_AND_RELATIONSHIPS = "1.3.1";
    }

    private final String id;
    private final String wcag;
    private final Scope scope;

    HtmlRule(String id, String wcag, Scope scope) {
        this.id = id;
        this.wcag = wcag;
        this.scope = scope;
    }

    public String id() {
        return id;
    }

    public String wcag() {
        return wcag;
    }

    public Scope scope() {
        return scope;
    }

    /**
     * @param id the id of a rule of the catalog, such as {@code html.imageAltMissing}
     * @return the HTML rule of the id, or null when no HTML rule has it
     */
    public static HtmlRule byId(String id) {
        for (HtmlRule rule : values()) {
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        return null;
    }

    /** Whether the rule runs on HTML of this scope: a rule of the whole page never runs on one item. */
    public boolean runsOn(Scope rendered) {
        return scope == Scope.ITEM || rendered == Scope.PAGE;
    }
}
