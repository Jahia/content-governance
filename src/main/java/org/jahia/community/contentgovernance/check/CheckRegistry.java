package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.ContentCheck.Scope;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Every {@link ContentCheck} that a started module registers, by the id that the evaluator of a rule
 * names, and the run of these checks on the rules of a site.
 *
 * <p>Thread safety: the list of checks is copy-on-write, so a run reads it while a bundle starts or
 * stops.</p>
 */
@Component(service = CheckRegistry.class, immediate = true)
public class CheckRegistry {

    /** The service property of a check that the drawer runs in the browser: the server only knows its id. */
    public static final String RUNTIME = "check.runtime";
    /** The value of {@value #RUNTIME} for a check that runs in the browser. */
    public static final String BROWSER = "browser";
    /** The most findings that one check gives on one input. */
    static final int MAX_FINDINGS = 50;
    /** The module that registers a check which Content Governance does not register itself. */
    private static final Map<String, String> PROVIDERS = Map.of(ContentRule.MODEL, "ai-assistant");
    private static final Logger logger = LoggerFactory.getLogger(CheckRegistry.class);

    private final List<Registered> checks = new CopyOnWriteArrayList<>();

    private record Registered(String id, ContentCheck check, boolean browser) {
    }

    /**
     * Whether a rule can run.
     *
     * @param active       true when a started module registers the check that the rule names
     * @param missingCheck the id of the check that no module registers, or null for an active rule
     * @param needs        the module that registers the missing check, or null when it is not known
     */
    public record Status(boolean active, String missingCheck, String needs) {
    }

    /**
     * One finding, with the rule as the site applies it.
     *
     * @param rule    the rule that the content breaks
     * @param finding what the check found
     */
    public record Checked(ContentRule rule, Finding finding) {
    }

    @Reference(service = ContentCheck.class, cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC)
    protected void bindCheck(ContentCheck check, Map<String, Object> properties) {
        Object id = properties.get(ContentCheck.ID);
        if (!(id instanceof String checkId) || checkId.isBlank()) {
            logger.warn("The check {} has no {} property, so no rule can name it", check.getClass().getName(),
                    ContentCheck.ID);
            return;
        }
        checks.add(new Registered(checkId.trim(), check, BROWSER.equals(properties.get(RUNTIME))));
    }

    protected void unbindCheck(ContentCheck check, Map<String, Object> properties) {
        Object id = properties.get(ContentCheck.ID);
        String checkId = id instanceof String text ? text.trim() : null;
        // One object may serve several ids, such as the checks that run in the browser.
        checks.removeIf(registered -> registered.check() == check && registered.id().equals(checkId));
    }

    /**
     * @param checkId the evaluator of a rule
     * @return true when a started module registers a check with this id
     */
    public boolean has(String checkId) {
        return find(checkId) != null;
    }

    /**
     * @param rule a rule of a site
     * @return whether the check that the rule names is registered, and which one is missing
     */
    public Status status(ContentRule rule) {
        if (has(rule.evaluator())) {
            return new Status(true, null, null);
        }
        return new Status(false, rule.evaluator(), PROVIDERS.get(rule.evaluator()));
    }

    /**
     * @param rule a rule of a site
     * @return true when a finding of the rule stops the publication: never for a rule of the browser
     */
    public boolean blocking(ContentRule rule) {
        Registered registered = find(rule.evaluator());
        return rule.blocking() && (registered == null || !registered.browser());
    }

    /**
     * Runs on the input each check of a rule that the site of the input keeps on. A check that runs in
     * the browser, or not in the scope of the input, does not run. A check that fails is logged and
     * skipped.
     *
     * @param input   what to check, whose path names the site
     * @param skipped the evaluators that the caller applies itself
     * @return the findings, in the order of the rules
     */
    public List<Checked> run(CheckInput input, Set<String> skipped) {
        return run(ContentRules.forPath(input.path()), input, skipped);
    }

    /**
     * Runs on the input the checks of the given rules only, as {@link #run(CheckInput, Set)} does.
     *
     * @param input   what to check, whose path names the site
     * @param ruleIds the ids of the rules to apply
     * @return the findings, in the order of the rules
     */
    public List<Checked> runRules(CheckInput input, Set<String> ruleIds) {
        return runRules(ContentRules.forPath(input.path()), input, ruleIds);
    }

    /** {@link #runRules(CheckInput, Set)} with the rules of the site. */
    public List<Checked> runRules(ContentRules rules, CheckInput input, Set<String> ruleIds) {
        return run(rules, input, rule -> ruleIds.contains(rule.id()));
    }

    /** {@link #run(CheckInput, Set)} with the rules of the site. */
    List<Checked> run(ContentRules rules, CheckInput input, Set<String> skipped) {
        return run(rules, input, rule -> !skipped.contains(rule.evaluator()));
    }

    private List<Checked> run(ContentRules rules, CheckInput input, Predicate<ContentRule> applies) {
        Scope scope = input.scope();
        List<Checked> out = new ArrayList<>();
        for (ContentRule rule : rules.all()) {
            Registered registered = rule.enabled() && applies.test(rule) ? find(rule.evaluator()) : null;
            if (registered == null || registered.browser() || !registered.check().scopes().contains(scope)) {
                continue;
            }
            try {
                List<Finding> found = registered.check().check(input, rule);
                found.stream().limit(MAX_FINDINGS).forEach(finding -> out.add(new Checked(rule, finding)));
            } catch (RuntimeException e) {
                logger.warn("The check {} failed on the rule {} at {}", registered.id(), rule.id(), input.path(), e);
            }
        }
        return out;
    }

    private Registered find(String checkId) {
        if (checkId == null) {
            return null;
        }
        for (Registered registered : checks) {
            if (registered.id().equals(checkId)) {
                return registered;
            }
        }
        return null;
    }
}
