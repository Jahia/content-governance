package org.jahia.community.contentgovernance.check.spi;

/**
 * A rule as one site applies it: the values of its {@code ccs:contentRule} node, with the shipped values
 * for the properties that the node lacks.
 */
public interface Rule {

    /**
     * @return the id that each finding of the rule carries, such as {@code seo.title}
     */
    String id();

    /**
     * @return the id of the check that applies the rule
     */
    String evaluator();

    /**
     * @return critical, serious, moderate or minor
     */
    String severity();

    /**
     * @return true when a finding of the rule stops the publication of the content
     */
    boolean blocking();

    /**
     * @return what to do about a finding, in English, or null
     */
    String advice();

    /**
     * @return the unit of the limits, such as {@code characters}, or null
     */
    String unit();

    /**
     * @return the lowest value that passes, or null
     */
    Double minimum();

    /**
     * @return the highest value that passes, or null
     */
    Double maximum();

    /**
     * A property of the rule node, such as {@code pattern} for a check that reads its own settings.
     *
     * @param property the name of the property
     * @return a {@link String}, a {@link Boolean}, a {@link Double}, a {@link java.util.List} of strings
     * or a {@link java.time.LocalDate}, or null when the rule has no value
     */
    Object value(String property);
}
