/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.links;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinksTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void sortsInternalAndExternalLinks() {
        String html = "<a href='/cms/render/live/en/sites/digitall/home/about.html'>a</a>"
                + "<a href='/en/sites/digitall/home/caf%C3%A9.html?x=1#top'>b</a>"
                + "<a href='https://www.example.com/blog'>c</a><a href='http://localhost:8230/sites/digitall/home.html'>d</a>"
                + "<a href='/vanity-url'>e</a><a href='#top'>f</a><a href='mailto:a@b.c'>g</a>"
                + "<a href='ftp://files.example.com/x'>h</a>";
        LinkExtractor.Links links = LinkExtractor.extract(Jsoup.parse(html), Set.of("localhost"));
        assertEquals(List.of("/sites/digitall/home/about", "/sites/digitall/home/café", "/sites/digitall/home"),
                List.copyOf(links.internalPaths));
        assertEquals(List.of("https://www.example.com/blog"), List.copyOf(links.externalUrls));
        assertEquals(1, links.unresolved);
    }

    @Test
    void readsALinkToAServerNameOfTheSiteAsInternal() {
        String html = "<a href='https://www.digitall.example/cms/render/default/en/sites/digitall/home.html'>a</a>";
        LinkExtractor.Links links = LinkExtractor.extract(Jsoup.parse(html), Set.of("www.digitall.example"));
        assertEquals(List.of("/sites/digitall/home"), List.copyOf(links.internalPaths));
        assertTrue(links.externalUrls.isEmpty());
    }

    @Test
    void refusesEveryPrivateAddress() throws Exception {
        for (String address : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "192.0.0.8", "::1", "fd00::1", "fe80::1", "224.0.0.1", "::ffff:10.0.0.1"}) {
            assertFalse(ExternalLinkChecker.isPublicAddress(InetAddress.getByName(address)), address);
        }
        assertTrue(ExternalLinkChecker.isPublicAddress(InetAddress.getByName("93.184.216.34")));
    }

    @Test
    void blocksAPrivateHostAndAnotherScheme() {
        assertEquals(ExternalLinkChecker.BLOCKED,
                ExternalLinkChecker.checkOne(null, "http://127.0.0.1:8080/admin", TIMEOUT).state());
        assertEquals(ExternalLinkChecker.BLOCKED,
                ExternalLinkChecker.checkOne(null, "http://localhost/", TIMEOUT).state());
        assertEquals(ExternalLinkChecker.BLOCKED,
                ExternalLinkChecker.checkOne(null, "http://192.168.10.10/admin", TIMEOUT).state());
        assertEquals(ExternalLinkChecker.BLOCKED,
                ExternalLinkChecker.checkOne(null, "http://[::1]:8080/", TIMEOUT).state());
        assertEquals(ExternalLinkChecker.BLOCKED,
                ExternalLinkChecker.checkOne(null, "file:///etc/passwd", TIMEOUT).state());
    }

    @Test
    void readsAStatus() {
        assertEquals(ExternalLinkChecker.BROKEN, ExternalLinkChecker.stateOf(404));
        assertEquals(ExternalLinkChecker.BROKEN, ExternalLinkChecker.stateOf(410));
        assertEquals(ExternalLinkChecker.NOT_VERIFIED, ExternalLinkChecker.stateOf(403));
        assertEquals(ExternalLinkChecker.OK, ExternalLinkChecker.stateOf(301));
        assertEquals(ExternalLinkChecker.UNREACHABLE, ExternalLinkChecker.stateOf(503));
    }

    @Test
    void checksNoExternalLinkOfAPrivateAddress() {
        String html = "<a href='http://10.0.0.5/admin'>a</a><a href='http://169.254.169.254/latest/meta-data'>b</a>";
        LinkCheck.ExternalLinks links = LinkCheck.external(Jsoup.parse(html), Set.of("localhost"));
        assertEquals(2, links.found());
        assertEquals(List.of("http://10.0.0.5/admin", "http://169.254.169.254/latest/meta-data"), links.blocked());
        assertEquals(0, links.ok());
    }
}
