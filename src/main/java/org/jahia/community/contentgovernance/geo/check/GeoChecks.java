package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.check.CheckServices;
import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.jahia.community.contentgovernance.rules.ContentRules;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks of GEO readiness that need code of their own, under the ids that their rules name as
 * evaluator. A check reads the report of the fetches of one page, the fact {@value #REPORT}, and gives a
 * finding when {@link GeoScore} fails it. An input with no report gives no finding.
 *
 * <p>The rules of GEO readiness whose evaluator is {@code threshold}, such as {@code seo.title}, need no
 * check of their own.</p>
 */
@Component(immediate = true)
public class GeoChecks {

    /** The fact of the report of the fetches of one page, a {@link JSONObject}. */
    public static final String REPORT = "content-governance:geoReport";
    /** The ids of the checks, which are the ids of their rules. */
    public static final List<String> IDS = List.of("geo.fetched", "geo.reachable", "geo.policyMatchesReality",
            "geo.noClientRedirect", "geo.namedInRobots", "geo.guestReadable", "geo.headingOutline",
            "geo.dateModified", "geo.robotsPresent", "geo.llmsPresent");
    private static final String SHARED = "content-governance:geoScore";

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        Map<String, ContentCheck> checks = new LinkedHashMap<>();
        IDS.forEach(id -> checks.put(id, new Check(id)));
        services.open(context, checks);
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    /** The checks that {@link GeoScore} computed on one report. */
    private record Scored(JSONArray checks) {
    }

    /** The check of one GEO rule. */
    static final class Check implements ContentCheck {
        private final String checkId;

        Check(String checkId) {
            this.checkId = checkId;
        }

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.PAGE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            if (!(input.fact(REPORT) instanceof JSONObject report)) {
                return List.of();
            }
            JSONArray scored = input.shared(SHARED, Scored.class,
                    () -> new Scored(GeoScore.compute(report, ContentRules.forPath(input.path())).getJSONArray("checks")))
                    .checks();
            List<Finding> out = new ArrayList<>();
            for (int i = 0; i < scored.length(); i++) {
                JSONObject check = scored.getJSONObject(i);
                if (checkId.equals(check.optString("rule")) && !check.optBoolean("passed", true)) {
                    out.add(Finding.of(rule, check.isNull("value") ? "" : String.valueOf(check.get("value"))));
                }
            }
            return out;
        }
    }
}
