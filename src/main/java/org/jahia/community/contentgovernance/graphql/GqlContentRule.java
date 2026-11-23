package org.jahia.community.contentgovernance.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.RuleValues;

import java.util.List;

/**
 * One rule of the catalog as a site applies it, for the analyzers of the drawer.
 */
@GraphQLName("ContentGovernanceRule")
@GraphQLDescription("A rule of the catalog of Content Governance, as a site applies it")
public class GqlContentRule {

    private final ContentRule rule;
    private final CheckRegistry.Status status;
    private final boolean blocking;
    private final String language;
    private final boolean isNew;

    GqlContentRule(ContentRule rule, CheckRegistry.Status status, boolean blocking) {
        this(rule, status, blocking, null, false);
    }

    /**
     * @param language the language of the texts, such as fr: the interface language of the editor, or null
     *                 for English
     * @param isNew    true when the site saved a layout that does not place the rule
     */
    GqlContentRule(ContentRule rule, CheckRegistry.Status status, boolean blocking, String language, boolean isNew) {
        this.rule = rule;
        this.status = status;
        this.blocking = blocking;
        this.language = language;
        this.isNew = isNew;
    }

    /** One plural form of the title of a finding. */
    @GraphQLName("ContentGovernanceRuleTextForm")
    @GraphQLDescription("One plural form of the title of a finding, as Intl.PluralRules names it")
    public static class TextForm {
        private final String form;
        private final String text;

        TextForm(String form, String text) {
            this.form = form;
            this.text = text;
        }

        @GraphQLField
        @GraphQLNonNull
        @GraphQLDescription("zero, one, two, few, many or other")
        public String getForm() {
            return form;
        }

        @GraphQLField
        @GraphQLNonNull
        @GraphQLDescription("The text, with values such as {{count}}, {{value}}, {{min}} and {{max}}")
        public String getText() {
            return text;
        }
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The id that each finding of the rule carries, such as seo.title")
    public String getId() {
        return rule.id();
    }

    @GraphQLField
    @GraphQLDescription("The id of the subdomain of the rule on the site, such as seo: its findings show in it. contentDomains gives the hierarchy. Null when the rule names none and no default applies")
    public String getSubdomain() {
        return rule.subdomain();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The place of the rule in its subdomain on the site: a lower order comes first")
    public int getOrder() {
        return rule.order();
    }

    @GraphQLField
    @GraphQLName("isNew")
    @GraphQLNonNull
    @GraphQLDescription("True when the site saved a layout that does not place the rule: a module shipped it after the save")
    public boolean isNew() {
        return isNew;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The keys of the sites that the rule applies to, or * for all sites")
    public List<String> getSites() {
        return rule.sites();
    }

    @GraphQLField
    @GraphQLDescription("The subdomain of the rule when no module and no node declares it (empty when the rule names none): the rule then never runs. Null for a rule placed in a subdomain of the site")
    public String getUnknownSubdomain() {
        return status.unknownSubdomain();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The title of a finding, in the language of the call or in English, one text per plural form. Empty when the rule has no title")
    public List<TextForm> getTitle() {
        return RuleValues.pluralForms(rule.text(RuleValues.TITLE, language)).entrySet().stream()
                .map(form -> new TextForm(form.getKey(), form.getValue()))
                .toList();
    }

    @GraphQLField
    @GraphQLDescription("A neutral name of what the rule checks, such as Title length, in the language of the call or in English, or null")
    public String getLabel() {
        return rule.text(RuleValues.LABEL, language);
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What checks the rule: pattern, threshold, presence, model, or the id of a check that a module registers")
    public String getEvaluator() {
        return rule.evaluator();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("False when no started module registers the check that the evaluator names: the rule then never runs. A rule of the browser is active: the drawer finds its check")
    public boolean isActive() {
        return status.active();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Where the check of the rule runs: browser for a check that the drawer runs on the preview of the page, else server")
    public String getRuntime() {
        return rule.inBrowser() ? ContentRule.BROWSER : ContentRule.SERVER;
    }

    @GraphQLField
    @GraphQLDescription("The id of the check that no started module registers, or null for an active rule")
    public String getMissingCheck() {
        return status.missingCheck();
    }

    @GraphQLField
    @GraphQLDescription("The module that registers the missing check, such as ai-assistant for the evaluator model, or null when it is not known")
    public String getNeeds() {
        return status.needs();
    }

    @GraphQLField
    @GraphQLDescription("True when a finding of the rule stops the publication of the content. A rule that the browser checks never blocks")
    public boolean isBlocking() {
        return blocking;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("critical, serious, moderate or minor")
    public String getSeverity() {
        return rule.severity();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("False when the rule does not run on the site: the site, or its default, switched off the rule, its subdomain or its domain")
    public boolean isEnabled() {
        return rule.enabled();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The role of the person who fixes a finding: editor, developer or administrator")
    public String getFixer() {
        return rule.fixer();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What to do about a finding, in the language of the call or in English")
    public String getAdvice() {
        String advice = rule.text(RuleValues.ADVICE, language);
        return advice == null ? "" : advice;
    }

    @GraphQLField
    @GraphQLDescription("The unit of the limits, or null when the rule has none")
    public String getUnit() {
        return rule.unit();
    }

    @GraphQLField
    @GraphQLDescription("The lowest value that passes, or null")
    public Double getMinimum() {
        return rule.minimum();
    }

    @GraphQLField
    @GraphQLDescription("The highest value that passes, or null")
    public Double getMaximum() {
        return rule.maximum();
    }
}
