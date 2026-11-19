package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The rules that the started modules ship. A module opts in with the manifest header {@value #HEADER},
 * whose value names a folder of the bundle, such as {@code content-rules}. Each {@code .md} file of that
 * folder, at any depth, is one {@link RuleFile}.
 *
 * <p>The seeder copies these rules into each site, where the rule nodes are the rules that the checks
 * apply. A shipped rule only fills in for a rule that a site has no node of.</p>
 *
 * <p>Thread safety: the registry is a concurrent map, written by the bundle tracker of {@link RuleSeeder}.</p>
 */
public final class ShippedRules {

    public static final String HEADER = "Jahia-Content-Rules";
    /** The folder of the rules of this module, which its manifest names. */
    static final String OWN_FOLDER = "content-rules";
    private static final String SUFFIX = ".md";
    private static final Logger logger = LoggerFactory.getLogger(ShippedRules.class);
    private static final Map<String, Shipment> BY_MODULE = new ConcurrentHashMap<>();

    /**
     * The rules of one module.
     *
     * @param module   the symbolic name of the bundle, which is the id of the module
     * @param rules    the rules by id, in the order of their files
     * @param complete false when a file of the folder could not be read: the seeder then removes no rule
     */
    public record Shipment(String module, Map<String, RuleFile> rules, boolean complete) {
    }

    private ShippedRules() {
    }

    /** Registers the rules of a module, and replaces the rules it shipped before. */
    static void put(Shipment shipment) {
        BY_MODULE.put(shipment.module(), shipment);
    }

    static void remove(String module) {
        BY_MODULE.remove(module);
    }

    /** The shipment of a module, or null when the module ships no rule or is not started. */
    static Shipment of(String module) {
        return SiteFeatures.MODULE.equals(module) ? own() : BY_MODULE.get(module);
    }

    /** The shipments of the modules in the list, this module first and then by module id. */
    static List<Shipment> forModules(Collection<String> modules) {
        List<Shipment> out = new ArrayList<>();
        out.add(own());
        new TreeMap<>(BY_MODULE).forEach((module, shipment) -> {
            if (!SiteFeatures.MODULE.equals(module) && modules.contains(module)) {
                out.add(shipment);
            }
        });
        return out;
    }

    /** The shipments of every started module, this module first. */
    static List<Shipment> all() {
        return forModules(BY_MODULE.keySet());
    }

    /**
     * The rules of this module. Before the bundle tracker has seen it, and in a unit test, they are read
     * from the bundle of the class or from the class path.
     */
    static Shipment own() {
        return BY_MODULE.computeIfAbsent(SiteFeatures.MODULE, module -> {
            Bundle bundle = FrameworkUtil.getBundle(ShippedRules.class);
            return bundle != null ? load(bundle, OWN_FOLDER) : loadFromClassPath(module);
        });
    }

    /**
     * The rules of a bundle, or null when its manifest has no {@value #HEADER}.
     */
    static Shipment load(Bundle bundle) {
        String folder = bundle.getHeaders().get(HEADER);
        return folder == null || folder.isBlank() ? null : load(bundle, folder.trim());
    }

    private static Shipment load(Bundle bundle, String folder) {
        String root = withoutSlashes(folder);
        Enumeration<URL> found = bundle.findEntries(root, "*" + SUFFIX, true);
        List<URL> files = new ArrayList<>();
        while (found != null && found.hasMoreElements()) {
            files.add(found.nextElement());
        }
        files.sort(Comparator.comparing(URL::getPath));
        Reader reader = new Reader(bundle.getSymbolicName());
        for (URL file : files) {
            String path = file.getPath();
            try (InputStream in = file.openStream()) {
                reader.add(new String(in.readAllBytes(), StandardCharsets.UTF_8), path.substring(path.lastIndexOf('/') + 1));
            } catch (IOException e) {
                reader.fail(path, e.getMessage());
            }
        }
        return reader.shipment();
    }

    /** The folder of a header with no slash at its start or its end: {@code /rules/} gives {@code rules}. */
    static String withoutSlashes(String folder) {
        int start = 0;
        int end = folder.length();
        while (start < end && folder.charAt(start) == '/') {
            start++;
        }
        while (end > start && folder.charAt(end - 1) == '/') {
            end--;
        }
        return folder.substring(start, end);
    }

    /** The rules of a folder of files, for a unit test. */
    static Shipment load(String module, Path folder) {
        Reader reader = new Reader(module);
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(SUFFIX)).sorted().toList()) {
                reader.add(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The rules of " + folder + " could not be read", e);
        }
        return reader.shipment();
    }

    private static Shipment loadFromClassPath(String module) {
        URL url = ShippedRules.class.getResource("/" + OWN_FOLDER);
        if (url == null || !"file".equals(url.getProtocol())) {
            logger.warn("The rules of {} are not on the class path", module);
            return new Shipment(module, Map.of(), false);
        }
        try {
            return load(module, Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The class path has the folder " + url, e);
        }
    }

    /** Collects the rules of one module, file after file. A file that fails is logged and skipped. */
    private static final class Reader {
        private final String module;
        private final Map<String, RuleFile> rules = new LinkedHashMap<>();
        private boolean complete = true;

        Reader(String module) {
            this.module = module;
        }

        void add(String text, String fileName) {
            try {
                RuleFile rule = RuleFile.parse(text, fileName);
                if (rules.putIfAbsent(rule.id(), rule) != null) {
                    fail(fileName, "the id " + rule.id() + " is already taken");
                }
            } catch (IllegalArgumentException e) {
                fail(fileName, e.getMessage());
            }
        }

        void fail(String fileName, String reason) {
            complete = false;
            logger.warn("The rule file {} of the module {} is skipped: {}", fileName, module, reason);
        }

        Shipment shipment() {
            return new Shipment(module, Collections.unmodifiableMap(rules), complete);
        }
    }
}
