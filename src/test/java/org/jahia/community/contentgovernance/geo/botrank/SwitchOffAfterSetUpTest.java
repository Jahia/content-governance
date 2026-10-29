package org.jahia.community.contentgovernance.geo.botrank;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A binding found when the module first sets up its state is switched off, with a note. */
class SwitchOffAfterSetUpTest {

    @Test
    void switchesOffAnEnabledSync() throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper enabled = mock(JCRPropertyWrapper.class);
        when(node.hasProperty("brEnabled")).thenReturn(true);
        when(node.getProperty("brEnabled")).thenReturn(enabled);
        when(enabled.getBoolean()).thenReturn(true);
        CitationStore.switchOffAfterSetUp(node);
        verify(node).setProperty("brEnabled", false);
        verify(node).setProperty("brStatus", CitationStore.STATUS_FAILED);
        verify(node).setProperty(eq("brMessage"), anyString());
    }

    @Test
    void leavesASyncThatIsOff() throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.hasProperty("brEnabled")).thenReturn(false);
        CitationStore.switchOffAfterSetUp(node);
        verify(node, never()).setProperty("brEnabled", false);
    }
}
