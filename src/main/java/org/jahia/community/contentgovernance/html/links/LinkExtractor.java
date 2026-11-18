/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.links;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sorts the links of rendered HTML into internal links, which name a node of Jahia, and external
 * links, which name another web site.
 */
final class LinkExtractor {

    private static final Pattern JCR_PATH = Pattern.compile("(/sites/[A-Za-z0-9_-]+(?:/[^?#]*)?)");

    /** The links of one render. */
    static final class Links {
        final Set<String> internalPaths = new LinkedHashSet<>();
        final Set<String> externalUrls = new LinkedHashSet<>();
        /** Internal links with no JCR path, such as a vanity URL, which the check does not read. */
        int unresolved;
    }

    private LinkExtractor() {
    }

    /**
     * @param localHosts lower-case host names that serve this Jahia, such as {@code localhost}: an
     *                   absolute link to one of them is an internal link
     */
    static Links extract(Document html, Set<String> localHosts) {
        Links links = new Links();
        for (Element anchor : html.select("a[href]")) {
            String href = anchor.attr("href").trim();
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")
                    || href.startsWith("javascript:")) {
                continue;
            }
            URI uri;
            try {
                uri = new URI(href.replace(" ", "%20"));
            } catch (URISyntaxException e) {
                continue;
            }
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean external = scheme != null && uri.getHost() != null
                    && !localHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
            if (external) {
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    links.externalUrls.add(uri.toString());
                }
                continue;
            }
            String path = jcrPath(uri.getRawPath());
            if (path == null) {
                links.unresolved++;
            } else {
                links.internalPaths.add(path);
            }
        }
        return links;
    }

    /** The JCR path inside a Jahia URL, such as /cms/render/live/en/sites/digitall/home.html. */
    static String jcrPath(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        Matcher matcher = JCR_PATH.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        String found = matcher.group(1);
        if (found.endsWith(".html")) {
            found = found.substring(0, found.length() - ".html".length());
        }
        while (found.endsWith("/")) {
            found = found.substring(0, found.length() - 1);
        }
        return found;
    }
}
