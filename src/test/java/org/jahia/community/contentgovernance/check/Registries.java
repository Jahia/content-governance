package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.CheckInput;
import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.jahia.community.contentgovernance.check.spi.Finding;
import org.jahia.community.contentgovernance.rules.ContentRules;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The registry as the tests of another package drive it, with no OSGi framework. */
public final class Registries {

    private Registries() {
    }

    public static void bind(CheckRegistry registry, ContentCheck check, String id) {
        registry.bindCheck(check, Map.of(ContentCheck.ID, id));
    }

    public static List<Finding> run(CheckRegistry registry, ContentRules rules, CheckInput input) {
        return registry.run(rules, input, Set.of()).stream().map(CheckRegistry.Checked::finding).toList();
    }
}
