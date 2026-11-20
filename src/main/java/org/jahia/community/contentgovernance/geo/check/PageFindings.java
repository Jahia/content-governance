package org.jahia.community.contentgovernance.geo.check;

import org.jahia.community.contentgovernance.check.CheckRegistry;
import org.jahia.community.contentgovernance.html.HtmlCheck;
import org.jahia.community.contentgovernance.html.rules.Scope;
import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import java.util.Set;

/**
 * The rules of the page checks that the HTML a crawler fetched breaks, as the check service of the drawer
 * and of {@code frame.check} finds them. {@link GeoScore} reads them from the report, under
 * {@value #KEY}.
 */
public final class PageFindings {

    /** The key of the report that holds the ids of the rules that the page breaks. */
    public static final String KEY = "pageFindings";
    /** The rules of the page checks that GEO readiness shows. */
    static final Set<String> RULE_IDS = Set.of("seo.title", "seo.metaDescription", "seo.canonical",
            "seo.structuredData", "html.pageLanguageMissing", "html.headingH1Missing", "html.headingH1Multiple",
            "html.imageAltMissing");

    private PageFindings() {
    }

    /**
     * @param path     the path of the page, which names its site
     * @param language the language of the page, such as {@code en}
     * @param html     the HTML that the crawler fetched
     * @return the ids of the rules that the HTML breaks, or null when the check service is not started
     */
    public static JSONArray of(String path, String language, String html) {
        Bundle bundle = FrameworkUtil.getBundle(PageFindings.class);
        BundleContext context = bundle == null ? null : bundle.getBundleContext();
        ServiceReference<CheckRegistry> reference = context == null ? null : context.getServiceReference(CheckRegistry.class);
        CheckRegistry checks = reference == null ? null : context.getService(reference);
        if (checks == null) {
            return null;
        }
        try {
            JSONArray out = new JSONArray();
            checks.runRules(HtmlCheck.input(path, language, Jsoup.parse(html), Scope.PAGE), RULE_IDS).stream()
                    .map(checked -> checked.rule().id())
                    .distinct()
                    .forEach(out::put);
            return out;
        } finally {
            context.ungetService(reference);
        }
    }
}
