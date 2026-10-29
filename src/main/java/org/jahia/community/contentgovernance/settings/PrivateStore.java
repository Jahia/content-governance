package org.jahia.community.contentgovernance.settings;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

/**
 * The nodes a site keeps the module's own state in: the site settings, the GEO scans and schedules,
 * the BotRank binding. The module reads and writes them with system sessions only, after its own
 * permission checks, so they do not take the permissions of the site they sit under.
 *
 * <p>Thread-safety: stateless; the node belongs to the caller's session.</p>
 */
public final class PrivateStore {

    private PrivateStore() {
    }

    /**
     * Stops {@code node} from inheriting the permissions of its parents, granting nothing in their
     * place: only system sessions reach it afterwards. The caller saves the session.
     *
     * @param node a module state node, from a system session
     * @return true when the node was changed, false when it was already private
     * @throws RepositoryException when the node could not be made private, or on a repository failure
     */
    public static boolean protect(JCRNodeWrapper node) throws RepositoryException {
        if (node.getAclInheritanceBreak()) {
            return false;
        }
        node.setAclInheritanceBreak(true);
        // Jahia logs and returns false rather than throwing when it cannot change the node: read
        // the flag back so a failure never goes unnoticed.
        if (!node.getAclInheritanceBreak()) {
            throw new RepositoryException("could not make " + node.getPath() + " private to the module");
        }
        return true;
    }
}
