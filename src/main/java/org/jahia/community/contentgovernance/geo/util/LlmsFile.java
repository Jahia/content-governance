package org.jahia.community.contentgovernance.geo.util;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

/**
 * The llms.txt the {@code llms} module stores on the site node and serves. Content Governance only
 * reads it: the file is written in that module's own screen.
 */
public final class LlmsFile {

    /** The mixin the llms module adds to the site node. */
    public static final String MIXIN = "jmix:llms";
    /** The property holding the file. */
    public static final String PROPERTY = "j:llms";
    /** The text the llms module puts in a new file, before anyone writes it. */
    public static final String PLACEHOLDER = "# Title";

    private LlmsFile() {
    }

    /**
     * @param site the site node, read in the workspace whose file is wanted (live for the published one)
     * @return the stored file, or null when the site has none
     * @throws RepositoryException when the node cannot be read
     */
    public static String of(JCRNodeWrapper site) throws RepositoryException {
        return site.isNodeType(MIXIN) && site.hasProperty(PROPERTY) ? site.getProperty(PROPERTY).getString() : null;
    }
}
