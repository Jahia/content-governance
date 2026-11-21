package org.jahia.community.contentgovernance.rules;

import org.jahia.community.contentgovernance.rules.DomainValues.Level;
import org.jahia.community.contentgovernance.settings.SiteFeatures;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The rules that the started modules ship. A module opts in with the manifest header {@value #HEADER},
 * whose value names a folder of the bundle, such as {@code content-rules}. Each {@code .md} file of that
 * folder, at any depth, is one {@link RuleFile}, except in its subfolders {@code domains} and
 * {@code subdomains}, where each file is one {@link DomainFile}.
 *
 * <p>A subdomain names a domain, and a rule names subdomains, that this module, Content Governance or
 * another started module declares: a file that names another one is skipped.</p>
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
     * The rules, the domains and the subdomains of one module.
     *
     * @param module     the symbolic name of the bundle, which is the id of the module
     * @param rules      the rules by id, in the order of their files
     * @param domains    the domains by id, in the order of their files
     * @param subdomains the subdomains by id, in the order of their files
     * @param complete   false when a file of the folder could not be read: the seeder then removes nothing
     */
    public record Shipment(String module, Map<String, RuleFile> rules, Map<String, DomainFile> domains,
                           Map<String, DomainFile> subdomains, boolean complete) {

        /** The rules of a module that ships no domain, for a test. */
        Shipment(String module, Map<String, RuleFile> rules, boolean complete) {
            this(module, rules, Map.of(), Map.of(), complete);
        }

        /** The domains or the subdomains. */
        public Map<String, DomainFile> of(Level level) {
            return level == Level.DOMAIN ? domains : subdomains;
        }
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

    /**
     * The shipments of the modules in the list, this module first and then by module id. A site lists the
     * modules it installs: a module that it does not install ships nothing to it, this module included.
     */
    static List<Shipment> forModules(Collection<String> modules) {
        Shipment own = own();
        List<Shipment> out = new ArrayList<>();
        if (modules.contains(own.module())) {
            out.add(own);
        }
        new TreeMap<>(BY_MODULE).forEach((module, shipment) -> {
            if (!SiteFeatures.MODULE.equals(module) && modules.contains(module)) {
                out.add(shipment);
            }
        });
        return out;
    }

    /** The shipments of every started module, this module first. */
    static List<Shipment> all() {
        own();
        return forModules(BY_MODULE.keySet());
    }

    /** The module that ships a domain or a subdomain of an id, or null when no started module ships it. */
    static String moduleOf(Level level, String id) {
        for (Shipment shipment : all()) {
            if (shipment.of(level).containsKey(id)) {
                return shipment.module();
            }
        }
        return null;
    }

    /**
     * The rules of this module. Before the bundle tracker has seen it, and in a unit test, they are read
     * from the bundle of the class or from the class path.
     */
    static Shipment own() {
        return BY_MODULE.computeIfAbsent(SiteFeatures.MODULE, module -> {
            Bundle bundle = FrameworkUtil.getBundle(ShippedRules.class);
            return bundle != null ? load(bundle, OWN_FOLDER, false) : loadFromClassPath(module);
        });
    }

    /**
     * The rules of a bundle, or null when its manifest has no {@value #HEADER}.
     */
    static Shipment load(Bundle bundle) {
        String folder = bundle.getHeaders().get(HEADER);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        return load(bundle, folder.trim(), !SiteFeatures.MODULE.equals(bundle.getSymbolicName()));
    }

    /**
     * @param others true when the domains and the subdomains of the other modules count: false for this
     *               module, which the others build on
     */
    private static Shipment load(Bundle bundle, String folder, boolean others) {
        String root = withoutSlashes(folder);
        Enumeration<URL> found = bundle.findEntries(root, "*" + SUFFIX, true);
        List<URL> files = new ArrayList<>();
        while (found != null && found.hasMoreElements()) {
            files.add(found.nextElement());
        }
        files.sort(Comparator.comparing(URL::getPath));
        Reader reader = new Reader(bundle.getSymbolicName());
        String prefix = "/" + root + "/";
        for (URL file : files) {
            String path = file.getPath();
            String relative = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
            try (InputStream in = file.openStream()) {
                reader.add(new String(in.readAllBytes(), StandardCharsets.UTF_8), relative);
            } catch (IOException e) {
                reader.fail(path, e.getMessage());
            }
        }
        return reader.shipment(others);
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
                String relative = folder.relativize(file).toString().replace(File.separatorChar, '/');
                reader.add(Files.readString(file, StandardCharsets.UTF_8), relative);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The rules of " + folder + " could not be read", e);
        }
        return reader.shipment(!SiteFeatures.MODULE.equals(module));
    }

    private static Shipment loadFromClassPath(String module) {
        URL url = ShippedRules.class.getResource("/" + OWN_FOLDER);
        if (url == null || !"file".equals(url.getProtocol())) {
            logger.warn("The rules of {} are not on the class path", module);
            return new Shipment(module, Map.of(), Map.of(), Map.of(), false);
        }
        try {
            return load(module, Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The class path has the folder " + url, e);
        }
    }

    /**
     * The level of a file from its path under the rule folder: a file of the subfolder {@code domains} or
     * {@code subdomains} is a domain or a subdomain, and any other file is a rule.
     *
     * @param relative the path of the file under the rule folder, such as {@code subdomains/seo.md}
     * @return the level, or null for a rule
     */
    static Level levelOf(String relative) {
        for (Level level : Level.values()) {
            if (relative.startsWith(level.folder() + "/")) {
                return level;
            }
        }
        return null;
    }

    /** Collects the rules, domains and subdomains of one module, file after file. A file that fails is logged and skipped. */
    private static final class Reader {
        private final String module;
        private final Map<String, RuleFile> rules = new LinkedHashMap<>();
        private final Map<Level, Map<String, DomainFile>> domains = Map.of(Level.DOMAIN, new LinkedHashMap<>(),
                Level.SUBDOMAIN, new LinkedHashMap<>());
        private final Map<String, String> fileOf = new LinkedHashMap<>();
        private boolean complete = true;

        Reader(String module) {
            this.module = module;
        }

        /** @param relative the path of the file under the rule folder */
        void add(String text, String relative) {
            String fileName = relative.substring(relative.lastIndexOf('/') + 1);
            Level level = levelOf(relative);
            try {
                if (level == null) {
                    RuleFile rule = RuleFile.parse(text, fileName);
                    if (rules.putIfAbsent(rule.id(), rule) != null) {
                        fail(fileName, "the id " + rule.id() + " is already taken");
                    }
                    fileOf.put(rule.id(), fileName);
                } else {
                    DomainFile domain = DomainFile.parse(text, fileName, level);
                    if (domains.get(level).putIfAbsent(domain.id(), domain) != null) {
                        fail(fileName, "the id " + domain.id() + " is already taken");
                    }
                }
            } catch (IllegalArgumentException e) {
                fail(fileName, e.getMessage());
            }
        }

        void fail(String fileName, String reason) {
            complete = false;
            logger.warn("The file {} of the module {} is skipped: {}", fileName, module, reason);
        }

        /**
         * The shipment, without a subdomain of an undeclared domain and without a rule that names an
         * undeclared subdomain.
         *
         * @param others true when the declarations of the other modules count
         */
        Shipment shipment(boolean others) {
            Map<String, DomainFile> subdomains = domains.get(Level.SUBDOMAIN);
            Set<String> knownDomains = declared(Level.DOMAIN, others);
            subdomains.values().removeIf(subdomain -> {
                Object parent = subdomain.values().get(DomainValues.DOMAIN);
                boolean unknown = !knownDomains.contains(String.valueOf(parent));
                if (unknown) {
                    fail(subdomain.id() + SUFFIX, "the domain " + parent + " is not declared");
                }
                return unknown;
            });
            Set<String> knownSubdomains = declared(Level.SUBDOMAIN, others);
            rules.values().removeIf(rule -> {
                List<String> unknown = RuleValues.strings(rule.values(), RuleValues.SUBDOMAINS).stream()
                        .filter(id -> !knownSubdomains.contains(id)).toList();
                if (!unknown.isEmpty()) {
                    fail(fileOf.get(rule.id()), "the subdomains " + unknown + " are not declared");
                }
                return !unknown.isEmpty();
            });
            return new Shipment(module, Collections.unmodifiableMap(rules),
                    Collections.unmodifiableMap(domains.get(Level.DOMAIN)), Collections.unmodifiableMap(subdomains),
                    complete);
        }

        private Set<String> declared(Level level, boolean others) {
            Set<String> ids = new HashSet<>(domains.get(level).keySet());
            if (others) {
                ids.addAll(declaredElsewhere(level, module));
            }
            return ids;
        }

        /**
         * The domains or the subdomains that this module and the other started modules declare, but for the
         * module of the shipment, whose own ones the reader adds.
         */
        private static Set<String> declaredElsewhere(Level level, String module) {
            Set<String> ids = new HashSet<>(own().of(level).keySet());
            BY_MODULE.forEach((other, shipment) -> {
                if (!other.equals(module)) {
                    ids.addAll(shipment.of(level).keySet());
                }
            });
            return ids;
        }
    }
}
