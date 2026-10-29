package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.compliance.model.ComplianceIssue;
import org.jahia.community.contentgovernance.compliance.model.ComplianceReport;
import org.jahia.community.contentgovernance.compliance.model.IssueOrigin;
import org.jahia.community.contentgovernance.compliance.model.LocationKind;
import org.jahia.community.contentgovernance.compliance.model.LocationReport;
import org.jahia.community.contentgovernance.compliance.model.ScanStatus;
import org.jahia.community.contentgovernance.compliance.model.SiteComplianceReport;
import org.jahia.community.contentgovernance.compliance.model.TermUsage;
import org.jahia.community.contentgovernance.compliance.service.spi.ComplianceScanService;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.SelectorType;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link ComplianceScanService}: walks the page or site tree in the caller's session. Each
 * call keeps its state in its own {@code Scan}; the component itself holds none.
 */
@Component(service = ComplianceScanService.class, immediate = true)
public class ComplianceScanServiceImpl implements ComplianceScanService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceScanServiceImpl.class);

    private static final String PAGE_TYPE = "jnt:page";
    private static final String NODE_REFERENCE_TYPE = "jmix:nodeReference";
    private static final String LIST_TYPE = "jmix:list";
    private static final String REFERENCE_PROPERTY = "j:node";
    /** Prefixed properties that hold text a visitor reads; every other system-prefixed property is technical. */
    private static final Set<String> TEXT_SYSTEM_PROPERTIES = Set.of("jcr:title", "jcr:description");
    private static final Set<String> SYSTEM_PREFIXES = Set.of("jcr", "j", "jmix", "jnt", "mix", "nt", "rep");

    private static final String CONTENT_FOLDER_TYPE = "jnt:contentFolder";

    /** State of one scan. Not shared between calls. */
    private static final class Scan {
        final TermMatcher matcher;
        final Locale uiLocale;
        final boolean followReferences;
        final int maxNodes;
        final int maxIssues;
        final Set<String> visited = new HashSet<>();
        /** Where the issues go: one list for a page scan, one per location for a site scan. */
        List<ComplianceIssue> issues = new ArrayList<>();
        int issueCount;
        int scanned;
        /** Locations a site scan could not read. */
        int skipped;
        boolean truncated;

        Scan(TermMatcher matcher, Locale uiLocale, boolean followReferences, int maxNodes, int maxIssues) {
            this.matcher = matcher;
            this.uiLocale = uiLocale;
            this.followReferences = followReferences;
            this.maxNodes = maxNodes;
            this.maxIssues = maxIssues;
        }

        boolean full() {
            if (scanned >= maxNodes || issueCount >= maxIssues) {
                truncated = true;
                return true;
            }
            return false;
        }
    }

    @Override
    public ComplianceReport scan(JCRNodeWrapper page, Locale uiLocale, Collection<String> renderedPaths)
            throws RepositoryException {
        JCRSessionWrapper session = page.getSession();
        String language = session.getLocale().toString();
        String sitePath = page.getResolveSite().getPath();
        GlossaryReader.Glossaries glossaries = GlossaryReader.read(session, sitePath, language);
        TermMatcher matcher = new TermMatcher(glossaries.terms());
        if (matcher.isEmpty()) {
            return new ComplianceReport(page.getPath(), language, ScanStatus.NO_GLOSSARY, 0, false,
                    glossaries.glossaries(), List.of());
        }

        Scan scan = new Scan(matcher, uiLocale, true, MAX_SCANNED_NODES, MAX_ISSUES);
        walk(page, IssueOrigin.PAGE, null, scan, true);
        for (JCRNodeWrapper area : ancestorAreas(page, sitePath)) {
            try {
                walk(area, IssueOrigin.SHARED, area.getParent().getPath(), scan, false);
            } catch (RepositoryException e) {
                logger.debug("Ancestor area {} not readable, skipped: {}", area.getPath(), e.toString());
            }
        }
        for (String path : renderedRoots(page.getPath(), sitePath, renderedPaths)) {
            if (scan.full()) {
                break;
            }
            // The paths come from the browser: one that is malformed or unreadable is skipped, not fatal.
            try {
                JCRNodeWrapper node = session.getNode(path);
                if (!node.isNodeType(PAGE_TYPE)) {
                    walk(node, IssueOrigin.SHARED, null, scan, false);
                }
            } catch (RepositoryException | IllegalArgumentException e) {
                logger.debug("Rendered path skipped: {}", e.toString());
            }
        }
        ScanStatus status = scan.issues.isEmpty() ? ScanStatus.SAFE : ScanStatus.ISSUES;
        return new ComplianceReport(page.getPath(), language, status, scan.scanned, scan.truncated,
                glossaries.glossaries(), List.copyOf(scan.issues));
    }

    @Override
    public SiteComplianceReport scanSite(JCRSiteNode site, Locale uiLocale) throws RepositoryException {
        JCRSessionWrapper session = site.getSession();
        String language = session.getLocale().toString();
        String sitePath = site.getPath();
        GlossaryReader.Glossaries glossaries = GlossaryReader.read(session, sitePath, language);
        TermMatcher matcher = new TermMatcher(glossaries.terms());
        if (matcher.isEmpty()) {
            return new SiteComplianceReport(site.getSiteKey(), language, ScanStatus.NO_GLOSSARY, 0, 0, 0, false,
                    glossaries.glossaries(), List.of(), List.of());
        }

        Scan scan = new Scan(matcher, uiLocale, false, MAX_SITE_SCANNED_NODES, MAX_SITE_ISSUES);
        List<LocationReport> locations = new ArrayList<>();
        int pages = 0;
        for (JCRNodeWrapper page : sitePages(session, sitePath)) {
            if (scan.full()) {
                break;
            }
            pages++;
            scanLocation(page, LocationKind.PAGE, scan, locations);
        }
        if (session.nodeExists(sitePath + "/contents")) {
            scanContentItems(session.getNode(sitePath + "/contents"), scan, locations);
        }
        ScanStatus status = locations.isEmpty() ? ScanStatus.SAFE : ScanStatus.ISSUES;
        return new SiteComplianceReport(site.getSiteKey(), language, status, pages, scan.scanned, scan.skipped,
                scan.truncated, glossaries.glossaries(), List.copyOf(locations), termUsages(locations));
    }

    private static List<JCRNodeWrapper> sitePages(JCRSessionWrapper session, String sitePath) throws RepositoryException {
        // ISDESCENDANTNODE takes no bind variable: the path comes from the site node, quotes doubled anyway.
        Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [" + PAGE_TYPE
                + "] AS p WHERE ISDESCENDANTNODE(p, '" + sitePath.replace("'", "''") + "')", Query.JCR_SQL2);
        List<JCRNodeWrapper> pages = new ArrayList<>();
        for (JCRNodeIteratorWrapper it = (JCRNodeIteratorWrapper) query.execute().getNodes(); it.hasNext(); ) {
            pages.add((JCRNodeWrapper) it.nextNode());
        }
        pages.sort(Comparator.comparing(JCRNodeWrapper::getPath));
        return pages;
    }

    /** Every node of the content folders that is not a folder is a content item, scanned with its sub-content. */
    private void scanContentItems(JCRNodeWrapper folder, Scan scan, List<LocationReport> locations) throws RepositoryException {
        for (JCRNodeIteratorWrapper it = folder.getNodes(); it.hasNext() && !scan.full(); ) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            if (child.getName().startsWith("j:") || isGlossaryNode(child)) {
                continue;
            }
            if (child.isNodeType(CONTENT_FOLDER_TYPE)) {
                scanContentItems(child, scan, locations);
            } else {
                scanLocation(child, LocationKind.CONTENT, scan, locations);
            }
        }
    }

    private void scanLocation(JCRNodeWrapper node, LocationKind kind, Scan scan, List<LocationReport> locations)
            throws RepositoryException {
        List<ComplianceIssue> issues = new ArrayList<>();
        scan.issues = issues;
        try {
            walk(node, IssueOrigin.PAGE, null, scan, true);
        } catch (RepositoryException e) {
            // One unreadable node (a missing type definition, a provider down) must not fail the site report.
            scan.skipped++;
            logger.warn("Compliance scan skipped {}: {}", node.getPath(), e.toString());
            return;
        }
        if (!issues.isEmpty()) {
            locations.add(new LocationReport(node.getPath(), node.getIdentifier(), node.getDisplayableName(), kind,
                    node.getPrimaryNodeType().getLabel(scan.uiLocale), List.copyOf(issues)));
        }
    }

    private static List<TermUsage> termUsages(List<LocationReport> locations) {
        record Key(String glossaryPath, String term) {
        }
        Map<Key, ComplianceIssue> first = new LinkedHashMap<>();
        Map<Key, Integer> uses = new LinkedHashMap<>();
        Map<Key, Set<String>> where = new LinkedHashMap<>();
        for (LocationReport location : locations) {
            for (ComplianceIssue issue : location.issues()) {
                Key key = new Key(issue.glossaryPath(), issue.term());
                first.putIfAbsent(key, issue);
                uses.merge(key, 1, Integer::sum);
                where.computeIfAbsent(key, k -> new HashSet<>()).add(location.path());
            }
        }
        List<TermUsage> usages = new ArrayList<>();
        first.forEach((key, issue) -> usages.add(new TermUsage(issue.term(), issue.glossaryPath(), issue.reason(),
                issue.alternative(), uses.get(key), where.get(key).size())));
        usages.sort(Comparator.comparingInt(TermUsage::uses).reversed().thenComparing(TermUsage::term));
        return List.copyOf(usages);
    }

    /**
     * The areas the page shows from its ancestor pages: header and footer parented on the home page.
     * Locked chrome renders in the edit frame without any marker, so the frame cannot name it. An
     * area of an ancestor page counts when the page has no child of the same name: the page's own
     * {@code main} hides the home page's {@code main}, the home page's {@code siteFooter} is shown.
     * Stops at the site's home page.
     */
    private static List<JCRNodeWrapper> ancestorAreas(JCRNodeWrapper page, String sitePath) throws RepositoryException {
        List<JCRNodeWrapper> areas = new ArrayList<>();
        JCRNodeWrapper home = page.getResolveSite().getHome();
        if (home != null && home.getPath().equals(page.getPath())) {
            return areas;
        }
        JCRNodeWrapper ancestor = page.getParent();
        while (ancestor != null && ancestor.getPath().startsWith(sitePath + "/")) {
            if (ancestor.isNodeType(PAGE_TYPE)) {
                for (JCRNodeIteratorWrapper it = ancestor.getNodes(); it.hasNext(); ) {
                    JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
                    if (!child.getName().startsWith("j:") && child.isNodeType(LIST_TYPE) && !child.isNodeType(PAGE_TYPE)
                            && !page.hasNode(child.getName())) {
                        areas.add(child);
                    }
                }
            }
            if (home != null && ancestor.getPath().equals(home.getPath())) {
                break;
            }
            try {
                ancestor = ancestor.getParent();
            } catch (RepositoryException e) {
                // An ancestor the caller cannot read ends the climb: its chrome is not theirs to see.
                logger.debug("Ancestor of {} not readable: {}", ancestor.getPath(), e.toString());
                break;
            }
        }
        return areas;
    }

    /**
     * The rendered paths worth reading: in the page's site, outside the page's own tree (walked
     * already) and not an ancestor of the page.
     */
    static Set<String> renderedRoots(String pagePath, String sitePath, Collection<String> renderedPaths) {
        Set<String> roots = new LinkedHashSet<>();
        if (renderedPaths == null) {
            return roots;
        }
        for (String path : renderedPaths) {
            if (roots.size() >= MAX_RENDERED_PATHS) {
                break;
            }
            if (path == null || !path.startsWith(sitePath + "/") || path.contains("/../") || path.endsWith("/..")) {
                continue;
            }
            boolean inPage = path.equals(pagePath) || path.startsWith(pagePath + "/");
            boolean ancestor = pagePath.startsWith(path + "/");
            if (!inPage && !ancestor) {
                roots.add(path);
            }
        }
        return roots;
    }

    private void walk(JCRNodeWrapper node, IssueOrigin origin, String originPath, Scan scan, boolean root)
            throws RepositoryException {
        if (scan.full() || (!root && node.isNodeType(PAGE_TYPE)) || isGlossaryNode(node) || !scan.visited.add(node.getIdentifier())) {
            return;
        }
        scan.scanned++;
        scanProperties(node, origin, originPath, scan);

        if (scan.followReferences && node.isNodeType(NODE_REFERENCE_TYPE) && node.hasProperty(REFERENCE_PROPERTY)) {
            try {
                JCRNodeWrapper target = (JCRNodeWrapper) node.getProperty(REFERENCE_PROPERTY).getNode();
                if (target != null) {
                    walk(target, IssueOrigin.REFERENCE, origin == IssueOrigin.REFERENCE ? originPath : node.getPath(), scan, false);
                }
            } catch (ItemNotFoundException | PathNotFoundException e) {
                logger.debug("Reference {} points to a node the session cannot read", node.getPath());
            }
        }

        for (JCRNodeIteratorWrapper it = node.getNodes(); it.hasNext() && !scan.full(); ) {
            JCRNodeWrapper child = (JCRNodeWrapper) it.nextNode();
            // j:translation_*, j:acl, j:conditionalVisibility, vanityUrlMapping...: system children, no visitor text.
            if (!child.getName().startsWith("j:") && !child.isNodeType("jnt:vanityUrls")) {
                walk(child, origin, originPath, scan, false);
            }
        }
    }

    private static boolean isGlossaryNode(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(GlossaryReader.GLOSSARY_TYPE) || node.isNodeType(GlossaryReader.TERM_TYPE);
    }

    private void scanProperties(JCRNodeWrapper node, IssueOrigin origin, String originPath, Scan scan)
            throws RepositoryException {
        for (PropertyIterator it = node.getProperties(); it.hasNext(); ) {
            JCRPropertyWrapper property = (JCRPropertyWrapper) it.nextProperty();
            ExtendedPropertyDefinition definition = textDefinition(property);
            if (definition == null) {
                continue;
            }
            Value[] values = property.isMultiple() ? property.getValues() : new Value[]{property.getValue()};
            for (Value value : values) {
                String raw = value.getString();
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String text = definition.getSelector() == SelectorType.RICHTEXT ? PlainText.fromHtml(raw) : PlainText.collapse(raw);
                for (TermMatcher.Match match : scan.matcher.find(text)) {
                    if (scan.issueCount >= scan.maxIssues) {
                        scan.truncated = true;
                        return;
                    }
                    scan.issues.add(issue(node, property, definition, text, match, origin, originPath, scan.uiLocale));
                    scan.issueCount++;
                }
            }
        }
    }

    /** @return the definition of a property that holds visitor text, {@code null} for any other property */
    private static ExtendedPropertyDefinition textDefinition(JCRPropertyWrapper property) throws RepositoryException {
        if (property.getType() != PropertyType.STRING || !(property.getDefinition() instanceof ExtendedPropertyDefinition definition)) {
            return null;
        }
        String name = property.getName();
        int colon = name.indexOf(':');
        if (colon > 0 && SYSTEM_PREFIXES.contains(name.substring(0, colon)) && !TEXT_SYSTEM_PROPERTIES.contains(name)) {
            return null;
        }
        int selector = definition.getSelector();
        boolean textSelector = selector == SelectorType.SMALLTEXT || selector == SelectorType.TEXTAREA || selector == SelectorType.RICHTEXT;
        boolean constrained = definition.getValueConstraints() != null && definition.getValueConstraints().length > 0;
        return textSelector && !constrained && !definition.isHidden() ? definition : null;
    }

    private static ComplianceIssue issue(JCRNodeWrapper node, JCRPropertyWrapper property, ExtendedPropertyDefinition definition,
                                         String text, TermMatcher.Match match, IssueOrigin origin, String originPath,
                                         Locale uiLocale) throws RepositoryException {
        Excerpt excerpt = Excerpt.around(text, match.start(), match.end());
        ExtendedNodeType type = node.getPrimaryNodeType();
        return new ComplianceIssue(node.getPath(), node.getIdentifier(), node.getDisplayableName(), type.getLabel(uiLocale),
                property.getName(), definition.getLabel(uiLocale, type),
                match.term().term(), excerpt.match(), excerpt.before(), excerpt.after(),
                match.term().reason(), match.term().alternative(), match.term().glossaryPath(),
                origin, originPath);
    }
}
