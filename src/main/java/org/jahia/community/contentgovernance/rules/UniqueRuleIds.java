package org.jahia.community.contentgovernance.rules;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRStoreService;
import org.jahia.services.content.interceptor.PropertyInterceptor;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.query.Query;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refuses a rule id that another {@value ContentRules#TYPE} node of the same site already has, when a
 * rule is created or its id changes in the edit workspace. A rule id names one rule per site. In a site other
 * than {@code systemsite}, it also refuses the id of a rule that a module ships or that {@code systemsite}
 * holds: a site does not change the values of a rule, so {@link ContentRules} would ignore such a node.
 */
@Component(service = {}, immediate = true)
public class UniqueRuleIds implements PropertyInterceptor {

    private static final Pattern SITE = Pattern.compile("^(/sites/[A-Za-z0-9_.-]+)/");

    @Activate
    public void activate() {
        JCRStoreService.getInstance().addInterceptor(this);
    }

    @Deactivate
    public void deactivate() {
        JCRStoreService.getInstance().removeInterceptor(this);
    }

    @Override
    public boolean canApplyOnProperty(JCRNodeWrapper node, ExtendedPropertyDefinition definition) {
        return RuleValues.RULE_ID.equals(definition.getName())
                && ContentRules.TYPE.equals(definition.getDeclaringNodeType().getName());
    }

    @Override
    public Value beforeSetValue(JCRNodeWrapper node, String name, ExtendedPropertyDefinition definition, Value value)
            throws RepositoryException {
        Matcher site = SITE.matcher(node.getPath());
        if (value != null && site.find() && "default".equals(node.getSession().getWorkspace().getName())) {
            String id = value.getString();
            String other = holder(node, site.group(1), id);
            if (other != null) {
                throw new ConstraintViolationException("The rule " + id + " already exists at " + other);
            }
            if (!ContentDomains.SYSTEM_SITE.equals(site.group(1))) {
                refuseARuleOfTheSystemSite(node, id);
            }
        }
        return value;
    }

    /** Refuses the id of a rule that a module ships or that the system site holds, which an administrator changes there. */
    private static void refuseARuleOfTheSystemSite(JCRNodeWrapper node, String id) throws RepositoryException {
        String module = shippedBy(id);
        String other = module == null ? holder(node, ContentDomains.SYSTEM_SITE, id) : null;
        if (module != null || other != null) {
            throw new ConstraintViolationException("The rule " + id + " is a rule of the system site, "
                    + (module != null ? "which the module " + module + " ships" : "at " + other)
                    + ": an administrator changes it there, and a site switches it in its layout");
        }
    }

    /** The module that ships a rule of this id, or null. */
    static String shippedBy(String id) {
        for (ShippedRules.Shipment shipment : ShippedRules.all()) {
            if (shipment.rules().containsKey(id)) {
                return shipment.module();
            }
        }
        return null;
    }

    /** The path of another rule of the site with this id, or null. */
    private static String holder(JCRNodeWrapper node, String sitePath, String id) throws RepositoryException {
        Query query = node.getSession().getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [" + ContentRules.TYPE + "] WHERE ISDESCENDANTNODE('" + sitePath + "') AND ["
                        + RuleValues.RULE_ID + "] = '" + id.replace("'", "''") + "'", Query.JCR_SQL2);
        NodeIterator found = query.execute().getNodes();
        while (found.hasNext()) {
            String path;
            try {
                JCRNodeWrapper other = (JCRNodeWrapper) found.nextNode();
                path = other.getIdentifier().equals(node.getIdentifier()) ? null : other.getPath();
            } catch (InvalidItemStateException | ItemNotFoundException e) {
                // A rule that this session removed and has not saved yet no longer counts.
                path = null;
            }
            if (path != null && node.getSession().itemExists(path)) {
                return path;
            }
        }
        return null;
    }

    @Override
    public Value[] beforeSetValues(JCRNodeWrapper node, String name, ExtendedPropertyDefinition definition, Value[] values) {
        return values;
    }

    @Override
    public void beforeRemove(JCRNodeWrapper node, String name, ExtendedPropertyDefinition definition) {
        // A rule id is mandatory, so the node type refuses its removal.
    }

    @Override
    public Value afterGetValue(JCRPropertyWrapper property, Value storedValue) {
        return storedValue;
    }

    @Override
    public Value[] afterGetValues(JCRPropertyWrapper property, Value[] storedValues) {
        return storedValues;
    }
}
