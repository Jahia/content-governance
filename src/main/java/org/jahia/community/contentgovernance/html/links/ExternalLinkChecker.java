/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.links;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Checks external links from the Jahia server. A page author writes the links, so a check must not
 * let a page make the server call an internal address. The checker refuses every host that resolves
 * to a private, loopback, link-local, multicast or unique local address, as
 * {@code geo.util.FetchGuard} refuses them for the GEO fetches, and it follows no redirect. It reads
 * the status of each answer, never its body.
 *
 * <p>The HTTP client resolves the name again, so a name that answers differently twice (DNS
 * rebinding) passes. {@code FetchGuard} accepts the same limit.</p>
 */
final class ExternalLinkChecker {

    static final String OK = "ok";
    static final String BROKEN = "broken";
    static final String NOT_VERIFIED = "not_verified";
    static final String UNREACHABLE = "unreachable";
    static final String BLOCKED = "blocked";

    /** The result of one link: its state ({@code ok}, {@code broken}, ...) and the HTTP status, or 0. */
    record Result(String url, String state, int status) {
    }

    private static final int PARALLEL_REQUESTS = 4;
    private static final String USER_AGENT = "Jahia-Content-Governance-LinkCheck";

    private ExternalLinkChecker() {
    }

    static List<Result> check(List<String> urls, Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS, runnable -> {
            Thread thread = new Thread(runnable, "content-governance-link-check");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<Result>> futures = new ArrayList<>();
            for (String url : urls) {
                futures.add(CompletableFuture.supplyAsync(() -> checkOne(client, url, timeout), pool));
            }
            List<Result> results = new ArrayList<>();
            for (CompletableFuture<Result> future : futures) {
                results.add(future.join());
            }
            return results;
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static Result checkOne(HttpClient client, String url, Duration timeout) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return new Result(url, BROKEN, 0);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                || !isPublicHost(uri.getHost())) {
            return new Result(url, BLOCKED, 0);
        }
        try {
            int status = send(client, uri, "HEAD", timeout);
            if (status == 405 || status == 501) {
                status = send(client, uri, "GET", timeout);
            }
            return new Result(url, stateOf(status), status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(url, UNREACHABLE, 0);
        } catch (IOException | RuntimeException e) {
            return new Result(url, UNREACHABLE, 0);
        }
    }

    private static int send(HttpClient client, URI uri, String method, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        // Only the status counts: closing the body at once stops a large or endless download.
        response.body().close();
        return response.statusCode();
    }

    /** 404 and 410 prove a broken link. A 401, 403 or 429 does not, because a site can refuse a script. */
    static String stateOf(int status) {
        if (status == 404 || status == 410) {
            return BROKEN;
        }
        if (status == 401 || status == 403 || status == 429) {
            return NOT_VERIFIED;
        }
        if (status >= 500) {
            return UNREACHABLE;
        }
        return OK;
    }

    /** True when the host resolves, and every address it resolves to is public. */
    static boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 0.0.0.0/8, carrier-grade NAT 100.64.0.0/10, and 192.0.0.0/24.
            return first != 0 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (bytes[2] & 0xff) == 0);
        }
        if (address instanceof Inet6Address) {
            // Unique local addresses fc00::/7.
            return (bytes[0] & 0xfe) != 0xfc;
        }
        return false;
    }
}
