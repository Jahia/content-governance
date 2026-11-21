package org.jahia.community.contentgovernance.check;

import org.jahia.community.contentgovernance.check.spi.ContentCheck;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * The services of a family of checks that one component registers, such as one check per HTML rule.
 *
 * <p>Thread safety: not thread-safe. A component opens it in its activate method and closes it in its
 * deactivate method.</p>
 */
public final class CheckServices {

    private static final Logger logger = LoggerFactory.getLogger(CheckServices.class);

    private final List<ServiceRegistration<ContentCheck>> registrations = new ArrayList<>();

    /**
     * Registers each check under its id.
     *
     * @param context the context of this bundle
     * @param checks  the checks by id
     */
    // registerService takes a Dictionary, and OSGi core 7 has no FrameworkUtil.asDictionary.
    @SuppressWarnings("java:S1149")
    public void open(BundleContext context, Map<String, ContentCheck> checks) {
        checks.forEach((id, check) -> {
            Hashtable<String, Object> properties = new Hashtable<>();
            properties.put(ContentCheck.ID, id);
            registrations.add(context.registerService(ContentCheck.class, check, properties));
        });
    }

    /** Unregisters every check. A check that the framework already removed is skipped. */
    public void close() {
        for (ServiceRegistration<ContentCheck> registration : registrations) {
            try {
                registration.unregister();
            } catch (IllegalStateException e) {
                logger.debug("A check was already unregistered", e);
            }
        }
        registrations.clear();
    }
}
