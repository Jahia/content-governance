package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.compliance.model.ComplianceIssue;
import org.jahia.community.contentgovernance.compliance.model.IssueOrigin;
import org.jahia.community.contentgovernance.compliance.model.LocationKind;
import org.jahia.community.contentgovernance.compliance.model.LocationReport;
import org.jahia.community.contentgovernance.compliance.model.TermUsage;
import org.jahia.community.contentgovernance.rules.ContentRule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.jahia.community.contentgovernance.rules.RuleValues;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The limits of a scan on the issues of each value, and the terms of the site report. */
class ScanLimitsTest {

    private static final ContentRule CLICK = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "compliance.click-here",
            RuleValues.PATTERN, "click here", RuleValues.REPLACEMENT, "select"));
    private static final ContentRule PRICE = RuleValues.toRule(Map.of(RuleValues.RULE_ID, "acme.erpPrice",
            RuleValues.EVALUATOR, "acme.erpPrice", RuleValues.SUBDOMAIN, "writing", RuleValues.LABEL, "Prices from the ERP"));

    private static ComplianceScanServiceImpl.Scan scan(int maxIssues) {
        return new ComplianceScanServiceImpl.Scan(new ComplianceRules(ContentRules.base(), Set.of(), true, List.of()),
                null, "en", Locale.ENGLISH, false, 100, maxIssues);
    }

    /** A value with one finding of each rule, at the start of the text. */
    private static ValueFindings value(String text, boolean capped, ContentRule... rules) {
        List<PlacedFinding> found = new ArrayList<>();
        for (ContentRule rule : rules) {
            found.add(PlacedFinding.of(text, rule, Finding.of(rule, text.substring(0, 1))));
        }
        return new ValueFindings(text, found, capped);
    }

    private static ComplianceIssue issue(String path, PlacedFinding found) {
        return new ComplianceIssue(path, "id", "Name", "Text", "text", "Text", found.rule().id(), found.rule().severity(),
                found.term("en"), found.finding().text(), "", "", null, found.replacement(), found.rule().folder(),
                IssueOrigin.PAGE, null);
    }

    @Test
    void add_stopsAtTheLimitOfIssues_andCutsTheReport() throws RepositoryException {
        ComplianceScanServiceImpl.Scan scan = scan(3);

        assertThat(scan.add(value("ab", false, CLICK, PRICE), found -> issue("/a", found))).isTrue();
        assertThat(scan.truncated).isFalse();
        assertThat(scan.add(value("cd", false, CLICK, PRICE), found -> issue("/b", found))).isFalse();

        assertThat(scan.issueCount).isEqualTo(3);
        assertThat(scan.issues).extracting(ComplianceIssue::ruleId).containsExactly("compliance.click-here",
                "acme.erpPrice", "compliance.click-here");
        assertThat(scan.truncated).isTrue();
        assertThat(scan.full()).isTrue();
    }

    @Test
    void add_cutsTheReportForAValueThatARuleBreaksInAllThePlacesThatTheRegistryKeeps() throws RepositoryException {
        ComplianceScanServiceImpl.Scan scan = scan(1000);

        assertThat(scan.add(value("ab", true, CLICK), found -> issue("/a", found))).isTrue();

        assertThat(scan.issueCount).isEqualTo(1);
        assertThat(scan.truncated).isTrue();
    }

    @Test
    void add_cutsTheReportForAValueTooLongForTheRules() throws RepositoryException {
        ComplianceScanServiceImpl.Scan scan = scan(1000);

        scan.add(value("a".repeat(RuleChecker.MAX_TEXT + 1), false), found -> issue("/a", found));

        assertThat(scan.issueCount).isZero();
        assertThat(scan.truncated).isTrue();
    }

    @Test
    void termUsages_countTheUsesAndTheLocationsOfEachRule_withTheTermOfItsIssues() throws RepositoryException {
        ComplianceScanServiceImpl.Scan home = scan(1000);
        home.add(value("ab", false, CLICK, PRICE, CLICK), found -> issue("/sites/acme/home", found));
        ComplianceScanServiceImpl.Scan about = scan(1000);
        about.add(value("cd", false, PRICE), found -> issue("/sites/acme/home/about", found));
        List<LocationReport> locations = List.of(
                new LocationReport("/sites/acme/home", "home", "Home", LocationKind.PAGE, "Page", home.issues),
                new LocationReport("/sites/acme/home/about", "about", "About", LocationKind.PAGE, "Page", about.issues));

        List<TermUsage> usages = ComplianceScanServiceImpl.termUsages(locations);

        // A rule of another check counts like a term, under the label of its rule
        assertThat(usages).extracting(TermUsage::ruleId, TermUsage::term, TermUsage::alternative, TermUsage::uses,
                TermUsage::locations).containsExactly(
                tuple("acme.erpPrice", "Prices from the ERP", null, 2, 2),
                tuple("compliance.click-here", "click here", "select", 2, 1));
    }
}
