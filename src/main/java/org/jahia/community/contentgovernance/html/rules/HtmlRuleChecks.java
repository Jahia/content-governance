package org.jahia.community.contentgovernance.html.rules;

import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One check per {@link HtmlRule}, under the id of the rule. The checks of one input share one run of
 * {@link HtmlRules#check}.
 */
@Component(immediate = true)
public class HtmlRuleChecks {

    /** The fact of the parsed HTML, a jsoup {@link Document}. Without it, a check parses the text of the input. */
    public static final String DOCUMENT = "content-governance:document";
    /** The fact of what the HTML holds, a {@link Scope}. Without it, the HTML is a whole page. */
    public static final String HTML_SCOPE = "content-governance:htmlScope";
    private static final String SHARED = "content-governance:htmlFindings";

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        Map<String, ContentCheck> checks = new LinkedHashMap<>();
        for (HtmlRule rule : HtmlRule.values()) {
            checks.put(rule.id(), new Check(rule));
        }
        services.open(context, checks, false);
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    /** The findings of every HTML rule on one input. */
    private record Found(List<HtmlFinding> findings) {
    }

    /** The check of one HTML rule. */
    static final class Check implements ContentCheck {
        private final HtmlRule htmlRule;

        Check(HtmlRule htmlRule) {
            this.htmlRule = htmlRule;
        }

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.PAGE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            return input.shared(SHARED, Found.class, () -> found(input)).findings().stream()
                    .filter(finding -> finding.rule() == htmlRule)
                    .map(finding -> Finding.of(rule, finding.element()))
                    .toList();
        }
    }

    /** Here {@code Scope} is the scope of the HTML. Inside {@link Check}, the same name is the scope of a check. */
    private static Found found(CheckInput input) {
        Document html = input.fact(DOCUMENT) instanceof Document document ? document
                : Jsoup.parse(input.text() == null ? "" : input.text());
        Scope scope = input.fact(HTML_SCOPE) instanceof Scope rendered ? rendered : Scope.PAGE;
        return new Found(HtmlRules.check(html, scope, input.language()));
    }
}
