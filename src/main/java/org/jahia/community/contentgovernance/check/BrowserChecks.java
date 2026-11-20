package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.check.spi.Rule;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks that the analyzers of the drawer run in the browser, on the page that the editor sees. The
 * server registers their ids so that their rules are active, and never runs them.
 */
@Component(immediate = true)
public class BrowserChecks {

    /** The ids of the checks of {@code src/javascript/audit/analyzers}. */
    static final List<String> IDS = List.of(
            "ecodesign.autoplay", "ecodesign.imageDimensions", "ecodesign.imageFormats", "ecodesign.lazyLoading",
            "ecodesign.oversizedImages",
            "health.graphqlUnavailable", "health.missingTranslations", "health.placeholderText",
            "health.rawI18nKeys", "health.unpublishedChanges",
            "links.blankNoopener", "links.broken", "links.hardcodedUrls", "links.mixedContent",
            "readability.hardText", "readability.headingSkips", "readability.veryHardText",
            "seo.canonical", "seo.followLinks", "seo.genericAnchors", "seo.imageAlt", "seo.indexable", "seo.lang",
            "seo.langMatches", "seo.openGraph", "seo.singleH1", "seo.structuredData", "seo.structuredDataValid");

    private final CheckServices services = new CheckServices();

    @Activate
    void activate(BundleContext context) {
        Map<String, ContentCheck> checks = new LinkedHashMap<>();
        IDS.forEach(id -> checks.put(id, new InBrowser()));
        services.open(context, checks, true);
    }

    @Deactivate
    void deactivate() {
        services.close();
    }

    /** A check that runs in the browser: on the server it finds nothing. */
    private static final class InBrowser implements ContentCheck {

        @Override
        public Set<Scope> scopes() {
            return Set.of(Scope.PAGE);
        }

        @Override
        public List<Finding> check(CheckInput input, Rule rule) {
            return List.of();
        }
    }
}
