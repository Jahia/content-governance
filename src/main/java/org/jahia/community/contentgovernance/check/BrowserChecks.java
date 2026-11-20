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
 * The rules that need a browser, because they measure what a browser loads and runs: the eco-design of
 * the web vitals. The server registers their ids so that their
 * rules are active, and never runs them. A browser rule never blocks, and the tools for agents never
 * report it.
 */
@Component(immediate = true)
public class BrowserChecks {

    /** The ids of the checks that {@code webVitals.js} and {@code ecodesign.js} run. */
    static final List<String> IDS = List.of(
            "ecodesign.autoplay", "ecodesign.imageDimensions", "ecodesign.imageFormats", "ecodesign.lazyLoading",
            "ecodesign.oversizedImages");

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
