package org.jahia.community.contentgovernance.check;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.CheckResult;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.ContentCheck.Scope;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.RuleSetCheck;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** The most findings that one rule gives on one input. */
    public static final int MAX_FINDINGS = 50;

    private static final ObjectMapper JSON = new ObjectMapper();
    /** The module that registers a check which Content Governance does not register itself. */
    private static final Map<String, String> PROVIDERS = Map.of(ContentRule.MODEL, "ai-assistant");
    private static final Logger logger = LoggerFactory.getLogger(CheckRegistry.class);

    private final List<Registered> checks = new CopyOnWriteArrayList<>();

    private record Registered(String id, ContentCheck check) {
    }

    /**
     * Whether a rule can run.
     *
     * @param active       true when a started module registers the check that the rule names, or when the rule
     *                     runs in the browser, where the drawer finds its check
     * @param missingCheck the id of the check that no module registers, or null for an active rule
     * @param needs        the module that registers the missing check, or null when it is not known
     * @param unknownSubdomain the subdomain of a rule that no module and no node declares (empty when the rule
     *                         names none), or null for a rule placed in a subdomain of the site
     */
    public record Status(boolean active, String missingCheck, String needs, String unknownSubdomain) {

        /** The status of a rule placed in a subdomain of the site. */
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
        checks.add(new Registered(checkId.trim(), check));
    }

    protected void unbindCheck(ContentCheck check, Map<String, Object> properties) {
        Object id = properties.get(ContentCheck.ID);
        String checkId = id instanceof String text ? text.trim() : null;
        // One object may serve several ids, such as the checks of one family.
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
     * @return whether the check that the rule names is registered, and which one is missing. A rule of the
     * browser is active: the server cannot see the checks that the drawer registers.
     */
    public Status status(ContentRule rule) {
        if (!rule.placed()) {
            // A finding of a rule whose subdomain is unknown has no place in the drawer: the rule does not run
            return new Status(false, null, null, rule.subdomain() == null ? "" : rule.subdomain());
        }
        if (rule.inBrowser() || has(rule.evaluator())) {
            return new Status(true, null, null, null);
        }
        return new Status(false, rule.evaluator(), PROVIDERS.get(rule.evaluator()), null);
    }

    /**
     * @param rule a rule of a site
     * @return true when a finding of the rule stops the publication: never for a rule of the browser
     */
    public boolean blocking(ContentRule rule) {
        return rule.blocking() && !rule.inBrowser();
    }

    /**
     * Runs on the input each check of a rule that the site of the input keeps on. A rule of the browser,
     * or a check not in the scope of the input, does not run. A {@link RuleSetCheck} runs once, on all its
     * rules. A check that fails is logged and skipped.
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
     * readiness reports on each page of a site, or the rules of Compliance on each text of a site.
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

    /**
     * @param rule  a rule of a site
     * @param scope where the check would run
     * @return true when a run on an input of the scope applies the rule: the site keeps it on, it is placed in
     * a subdomain of the site, it does not run in the browser, and a started module registers its check in
     * that scope
     */
    public boolean runs(ContentRule rule, Scope scope) {
        return runner(rule, scope) != null;
    }

    /** The check that applies the rule in the scope, or null when the rule does not run there. */
    private Registered runner(ContentRule rule, Scope scope) {
        Registered registered = rule.enabled() && rule.placed() && !rule.inBrowser() ? find(rule.evaluator()) : null;
        return registered != null && registered.check().scopes().contains(scope) ? registered : null;
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
        List<Running> running = new ArrayList<>();
        for (ContentRule rule : rules.all()) {
            boolean wanted = !skipped.contains(rule.evaluator()) && (ruleIds == null || ruleIds.contains(rule.id()));
            Registered registered = wanted ? runner(rule, input.scope()) : null;
            if (registered != null) {
                running.add(new Running(rule, registered));
            }
        }
        Map<String, CheckResult> together = together(input, running);
        List<Ran> out = new ArrayList<>();
        for (Running one : running) {
            CheckResult result = one.registered().check() instanceof RuleSetCheck
                    ? together.get(one.rule().id()) : alone(input, one);
            Ran ran = result == null ? null : kept(one, result, input);
            if (ran != null) {
                out.add(ran);
            }
        }
        return out;
    }

    /** A rule that a run applies, with its check. */
    private record Running(ContentRule rule, Registered registered) {
    }

    /** The result of a check that applies one rule, or null when the check failed or gave none. */
    private static CheckResult alone(CheckInput input, Running one) {
        try {
            CheckResult result = one.registered().check().result(input, one.rule());
            if (result == null) {
                logger.warn("The check {} gave no result on the rule {} at {}", one.registered().id(), one.rule().id(),
                        input.path());
            }
            return result;
        } catch (RuntimeException e) {
            logger.warn("The check {} failed on the rule {} at {}", one.registered().id(), one.rule().id(), input.path(), e);
            return null;
        }
    }

    /**
     * Applies each {@link RuleSetCheck} once to its rules of the run, and gives each of these rules a result
     * with no value, which passes when the rule has no finding.
     *
     * @return the results, by the id of their rule, with none for the rules of a check that failed; a finding
     * that names a rule of another check is dropped
     */
    private static Map<String, CheckResult> together(CheckInput input, List<Running> running) {
        Map<Registered, List<ContentRule>> sets = new LinkedHashMap<>();
        for (Running one : running) {
            if (one.registered().check() instanceof RuleSetCheck) {
                sets.computeIfAbsent(one.registered(), registered -> new ArrayList<>()).add(one.rule());
            }
        }
        Map<String, CheckResult> byRule = new HashMap<>();
        sets.forEach((registered, setRules) -> {
            Map<String, List<Finding>> found = new HashMap<>();
            setRules.forEach(rule -> found.put(rule.id(), new ArrayList<>()));
            try {
                for (Finding finding : ((RuleSetCheck) registered.check()).check(input, setRules)) {
                    List<Finding> ofRule = found.get(finding.ruleId());
                    if (ofRule != null) {
                        ofRule.add(finding);
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("The check {} failed on its {} rules at {}", registered.id(), setRules.size(), input.path(), e);
                return;
            }
            setRules.forEach(rule -> byRule.put(rule.id(), CheckResult.of(rule, Map.of(), found.get(rule.id()))));
        });
        return byRule;
    }

    /**
     * The result of the rule as the registry keeps it, or null when JSON cannot write its values. The result
     * belongs to the rule that ran, whatever id the check wrote, keeps its first {@value #MAX_FINDINGS}
     * findings, and fails when it has a finding.
     */
    private static Ran kept(Running one, CheckResult result, CheckInput input) {
        List<Finding> found = result.findings().stream().limit(MAX_FINDINGS).toList();
        CheckResult kept = new CheckResult(one.rule().id(), result.passed() && found.isEmpty(), result.values(), found);
        if (!writable(kept)) {
            logger.warn("The check {} gave values that JSON cannot write on the rule {} at {}", one.registered().id(),
                    one.rule().id(), input.path());
            return null;
        }
        return new Ran(one.rule(), kept);
    }

    /** True when JSON writes the values of the result and of each finding, as the GraphQL API sends them. */
    private static boolean writable(CheckResult result) {
        try {
            JSON.writeValueAsString(result.values());
            for (Finding finding : result.findings()) {
                JSON.writeValueAsString(finding.values());
            }
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
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
