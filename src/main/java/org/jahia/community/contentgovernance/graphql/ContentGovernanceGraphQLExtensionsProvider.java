package org.jahia.community.contentgovernance.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

/**
 * Registers this bundle with graphql-dxm-provider so its {@code @GraphQLTypeExtension} classes are
 * scanned. Marker component.
 */
@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class ContentGovernanceGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
