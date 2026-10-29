package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRStoreService;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.UrlResource;

import java.net.URL;

/**
 * Takes over the compliance glossary types ({@code ccs:glossary}, {@code ccs:glossaryTerm}) when they
 * are still registered to Content Compliance Scan, the module this one replaces, and that module is
 * no longer installed.
 *
 * <p>Jahia keeps the types of an uninstalled module registered to it, so its content stays valid,
 * and ignores the same types when another module declares them. jContent then hides the types from
 * "New content" on sites that do not enable the old module, and shows their names untranslated.
 * The types are moved with the same definitions, read from this bundle, so existing glossaries stay
 * valid. Every cluster node moves them in its own registry; only the processing server writes the
 * shared database and repository.</p>
 *
 * <p>Thread-safety: runs once, at activation.</p>
 */
@Component(immediate = true)
public class GlossaryTypeOwner {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryTypeOwner.class);

    /** The module Content Governance replaces, which registered the glossary types first. */
    static final String PREVIOUS_OWNER = "content-compliance-scan";
    static final String GLOSSARY_TYPE = "ccs:glossary";

    @Activate
    protected void activate() {
        try {
            if (!mustTakeOver(owner(), installed(PREVIOUS_OWNER))) {
                return;
            }
            takeOver(SiteFeatures.MODULE);
        } catch (Exception e) {
            // Never stop the module for this: the glossaries keep working, only their labels and the
            // "New content" entry are missing until the types are moved.
            logger.warn("Could not take over the compliance glossary types from {}: {}", PREVIOUS_OWNER, e.toString());
        }
    }

    /**
     * True when the glossary types are registered to the previous module and that module is gone.
     *
     * @param currentOwner      the module the glossary type is registered to, or null
     * @param previousInstalled whether the previous module is installed
     * @return whether this module should take the types over
     */
    static boolean mustTakeOver(String currentOwner, boolean previousInstalled) {
        return PREVIOUS_OWNER.equals(currentOwner) && !previousInstalled;
    }

    private static String owner() {
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        if (!registry.hasNodeType(GLOSSARY_TYPE)) {
            return null;
        }
        try {
            ExtendedNodeType type = registry.getNodeType(GLOSSARY_TYPE);
            return type.getSystemId();
        } catch (javax.jcr.nodetype.NoSuchNodeTypeException e) {
            return null;
        }
    }

    /** Whether a version of the module is installed on this Jahia. */
    private static boolean installed(String moduleId) {
        return ServicesRegistry.getInstance().getJahiaTemplateManagerService().getAnyDeployedTemplatePackage(moduleId) != null;
    }

    private void takeOver(String own) throws Exception {
        // This bundle's class loader serves its own resources first.
        URL cnd = getClass().getClassLoader().getResource("META-INF/definitions.cnd");
        if (cnd == null) {
            logger.warn("No definitions in {}: the compliance glossary types stay with {}", own, PREVIOUS_OWNER);
            return;
        }
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        registry.unregisterNodeTypes(PREVIOUS_OWNER);
        registry.addDefinitionsFile(new UrlResource(cnd), own);
        if (SettingsBean.getInstance().isProcessingServer()) {
            // The previous module has no type left at this point, so undeploying it unregisters nothing
            // in the repository and only drops its stored definitions.
            JCRStoreService.getInstance().deployDefinitions(own);
            JCRStoreService.getInstance().undeployDefinitions(PREVIOUS_OWNER);
        }
        registry.flushLabels();
        logger.info("Compliance glossary types taken over from {}: now registered to {}", PREVIOUS_OWNER, owner());
    }
}
