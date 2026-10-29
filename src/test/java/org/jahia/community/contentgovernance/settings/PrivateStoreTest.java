package org.jahia.community.contentgovernance.settings;

import org.jahia.services.content.JCRNodeWrapper;
import org.junit.jupiter.api.Test;

import javax.jcr.RepositoryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateStoreTest {

    @Test
    void leavesAPrivateNodeAlone() throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getAclInheritanceBreak()).thenReturn(true);
        assertThat(PrivateStore.protect(node)).isFalse();
        verify(node, never()).setAclInheritanceBreak(true);
    }

    @Test
    void makesANodePrivate() throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getAclInheritanceBreak()).thenReturn(false, true);
        assertThat(PrivateStore.protect(node)).isTrue();
        verify(node).setAclInheritanceBreak(true);
    }

    @Test
    void failsWhenTheNodeStaysShared() throws RepositoryException {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.getAclInheritanceBreak()).thenReturn(false);
        when(node.getPath()).thenReturn("/sites/a/content-governance");
        assertThatThrownBy(() -> PrivateStore.protect(node)).isInstanceOf(RepositoryException.class);
    }
}
