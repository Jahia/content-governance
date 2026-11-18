/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.rules;

/**
 * The rules that read the HTML that Jahia renders. Each rule has an id, the WCAG 2.2 success
 * criterion it checks ({@code none} when no criterion applies) and its scope.
 *
 * <p>The ids are provisional: the catalog of rules names them later. {@code docs/page-quality/html-rules.md}
 * lists them for a reader.</p>
 */
public enum HtmlRule {

    PAGE_LANGUAGE_MISSING("page-language-missing", "3.1.1", Scope.PAGE),
    PAGE_LANGUAGE_MISMATCH("page-language-mismatch", "3.1.1", Scope.PAGE),
    PAGE_TITLE_MISSING("page-title-missing", "2.4.2", Scope.PAGE),
    PAGE_DESCRIPTION_MISSING("page-description-missing", "none", Scope.PAGE),
    IMAGE_ALT_MISSING("image-alt-missing", "1.1.1", Scope.ITEM),
    HEADING_LEVEL_SKIPPED("heading-level-skipped", "1.3.1", Scope.ITEM),
    HEADING_EMPTY("heading-empty", "2.4.6", Scope.ITEM),
    HEADING_H1_MISSING("heading-h1-missing", "1.3.1", Scope.PAGE),
    HEADING_H1_MULTIPLE("heading-h1-multiple", "1.3.1", Scope.PAGE),
    LINK_NAME_MISSING("link-name-missing", "2.4.4", Scope.ITEM),
    LINK_TEXT_VAGUE("link-text-vague", "2.4.4", Scope.ITEM),
    BUTTON_NAME_MISSING("button-name-missing", "4.1.2", Scope.ITEM),
    FORM_LABEL_MISSING("form-label-missing", "1.3.1", Scope.ITEM),
    FRAME_TITLE_MISSING("frame-title-missing", "4.1.2", Scope.ITEM),
    TABLE_HEADER_MISSING("table-header-missing", "1.3.1", Scope.ITEM),
    DUPLICATE_ID("duplicate-id", "none", Scope.PAGE);

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

    /** Whether the rule runs on HTML of this scope: a rule of the whole page never runs on one item. */
    public boolean runsOn(Scope rendered) {
        return scope == Scope.ITEM || rendered == Scope.PAGE;
    }
}
