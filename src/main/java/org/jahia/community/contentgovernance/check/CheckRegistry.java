package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
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
     * @param unknownDomain the domain of the rule when it is not one of {@code RuleDomains.ALL} (empty for no
     *                      domain), or null for a rule of a known domain
     */
    public record Status(boolean active, String missingCheck, String needs, String unknownDomain) {

        /** The status of a rule of a known domain. */
        public Status(boolean active, String missingCheck, String needs) {
            this(active, missingCheck, needs, null);
        }
    }

    /** The result of one rule, with the rule as the site applies it. */
    private record Ran(ContentRule rule, CheckResult result) {
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
        if (!rule.hasKnownDomain()) {
            // A finding of a rule whose domain is unknown has no place in the drawer: the rule does not run
            return new Status(false, null, null, rule.domain() == null ? "" : rule.domain());
        }
        if (has(rule.evaluator())) {
            return new Status(true, null, null, null);
        }
        return new Status(false, rule.evaluator(), PROVIDERS.get(rule.evaluator()), null);
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
        return run(ContentRules.forPath(input.path()), input, skipped, null);
    }

    /**
     * Runs on the input each check of a rule of the site that it keeps on, as {@link #run(CheckInput, Set)}
     * does, and gives one result per rule that ran, passed or failed.
     *
     * @param rules the rules of the site of the input
     * @param input what to check
     * @return the results, in the order of the rules, each with at most {@value #MAX_FINDINGS} findings
     */
    public List<CheckResult> results(ContentRules rules, CheckInput input) {
        return apply(rules, input, Set.of(), null).stream().map(Ran::result).toList();
    }

    /**
     * {@link #run(CheckInput, Set)} on some rules of the site only, such as the SEO rules that GEO
     * readiness reports on each page of a site.
     *
     * @param rules   the rules of the site of the input, read once for many inputs
     * @param ruleIds the ids of the rules to run
     */
    public List<Checked> runRules(ContentRules rules, CheckInput input, Set<String> ruleIds) {
        return run(rules, input, Set.of(), ruleIds);
    }

    /** {@link #run(CheckInput, Set)} with the rules of the site. */
    List<Checked> run(ContentRules rules, CheckInput input, Set<String> skipped) {
        return run(rules, input, skipped, null);
    }

    /** The findings of {@link #apply}, each with its rule. */
    private List<Checked> run(ContentRules rules, CheckInput input, Set<String> skipped, Set<String> ruleIds) {
        List<Checked> out = new ArrayList<>();
        for (Ran ran : apply(rules, input, skipped, ruleIds)) {
            ran.result().findings().forEach(finding -> out.add(new Checked(ran.rule(), finding)));
        }
        return out;
    }

    /** The run on the rules of the site whose id {@code ruleIds} holds, or on every rule when it is null. */
    private List<Ran> apply(ContentRules rules, CheckInput input, Set<String> skipped, Set<String> ruleIds) {
        Scope scope = input.scope();
        List<Ran> out = new ArrayList<>();
        for (ContentRule rule : rules.all()) {
            boolean wanted = rule.enabled() && rule.hasKnownDomain() && !skipped.contains(rule.evaluator())
                    && (ruleIds == null || ruleIds.contains(rule.id()));
            Registered registered = wanted ? find(rule.evaluator()) : null;
            if (registered == null || registered.browser() || !registered.check().scopes().contains(scope)) {
                continue;
            }
            try {
                CheckResult result = registered.check().result(input, rule);
                if (result == null) {
                    logger.warn("The check {} gave no result on the rule {} at {}", registered.id(), rule.id(), input.path());
                    continue;
                }
                // The result belongs to the rule that ran, whatever id the check wrote
                List<Finding> found = result.findings().stream().limit(MAX_FINDINGS).toList();
                out.add(new Ran(rule, new CheckResult(rule.id(), result.passed(), result.values(), found)));
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
