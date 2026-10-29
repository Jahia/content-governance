package org.jahia.community.contentgovernance.compliance.service.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenderedRootsTest {

    private static final String SITE = "/sites/acme";
    private static final String PAGE = "/sites/acme/home/about";

    @Test
    void keepsOnlyPathsOfTheSiteOutsideThePageTreeAndNotItsAncestors() {
        assertThat(ComplianceScanServiceImpl.renderedRoots(PAGE, SITE, Arrays.asList(
                "/sites/acme/home/siteFooter",          // chrome on home: kept
                "/sites/acme/home/news/main/latest",    // a listing item elsewhere: kept
                "/sites/acme/home/about",               // the page itself: walked already
                "/sites/acme/home/about/main/text",     // under the page: walked already
                "/sites/acme/home",                     // an ancestor: would read its whole tree
                "/sites/acme",                          // the site node: an ancestor too
                "/sites/other/home/footer",             // another site
                "/sites/acmeother/home",                // a site whose key starts the same
                "/sites/acme/home/../../other",         // parent steps
                "/sites/acme/home/..",
                "relative/path",
                null)))
                .containsExactly("/sites/acme/home/siteFooter", "/sites/acme/home/news/main/latest");
    }

    @Test
    void keepsAtMostTheLimitAndNoDuplicates() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            paths.add("/sites/acme/home/news/item" + (i % 2500));
        }
        assertThat(ComplianceScanServiceImpl.renderedRoots(PAGE, SITE, paths))
                .hasSize(org.jahia.community.contentgovernance.compliance.service.spi.ComplianceScanService.MAX_RENDERED_PATHS)
                .doesNotHaveDuplicates();
    }

    @Test
    void noPathsMeansNoRoots() {
        assertThat(ComplianceScanServiceImpl.renderedRoots(PAGE, SITE, null)).isEmpty();
    }
}
