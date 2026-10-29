package org.jahia.community.contentgovernance.compliance.service.internal;

import org.jahia.community.contentgovernance.compliance.model.Glossary;
import org.jahia.community.contentgovernance.compliance.model.GlossaryTerm;
import org.jahia.community.contentgovernance.compliance.model.MatchMode;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the glossaries of one language of a site.
 */
final class GlossaryReader {

    static final String GLOSSARY_TYPE = "ccs:glossary";
    static final String TERM_TYPE = "ccs:glossaryTerm";

    /** The glossaries of a language and their terms. */
    record Glossaries(List<Glossary> glossaries, List<GlossaryTerm> terms) {
    }

    private GlossaryReader() {
    }

    /**
     * @param session  session of the scan
     * @param sitePath path of the site, {@code /sites/<siteKey>}
     * @param language language code the glossaries must declare
     * @return the glossaries under {@code <sitePath>/contents} for the language, in path order
     */
    static Glossaries read(JCRSessionWrapper session, String sitePath, String language) throws RepositoryException {
        QueryManager qm = session.getWorkspace().getQueryManager();
        // ISDESCENDANTNODE takes no bind variable: the path comes from the site node, quotes doubled anyway.
        String root = (sitePath + "/contents").replace("'", "''");
        Query query = qm.createQuery("SELECT * FROM [" + GLOSSARY_TYPE + "] AS g WHERE ISDESCENDANTNODE(g, '" + root
                + "') AND g.[language] = $language", Query.JCR_SQL2);
        query.bindValue("language", session.getValueFactory().createValue(language));
        List<Glossary> glossaries = new ArrayList<>();
        List<GlossaryTerm> terms = new ArrayList<>();
        for (JCRNodeIteratorWrapper it = (JCRNodeIteratorWrapper) query.execute().getNodes(); it.hasNext(); ) {
            JCRNodeWrapper glossary = (JCRNodeWrapper) it.nextNode();
            int count = 0;
            for (JCRNodeIteratorWrapper children = glossary.getNodes(); children.hasNext(); ) {
                JCRNodeWrapper child = (JCRNodeWrapper) children.nextNode();
                if (child.isNodeType(TERM_TYPE)) {
                    String term = child.getPropertyAsString("term");
                    if (term != null && !term.isBlank()) {
                        terms.add(new GlossaryTerm(term.trim(), MatchMode.fromValue(child.getPropertyAsString("matchMode")),
                                child.getPropertyAsString("reason"), child.getPropertyAsString("alternative"),
                                child.getPath(), glossary.getPath()));
                        count++;
                    }
                }
            }
            glossaries.add(new Glossary(glossary.getPath(), glossary.getIdentifier(), glossary.getDisplayableName(), count));
        }
        glossaries.sort(Comparator.comparing(Glossary::path));
        terms.sort(Comparator.comparing(GlossaryTerm::glossaryPath));
        return new Glossaries(List.copyOf(glossaries), List.copyOf(terms));
    }
}
