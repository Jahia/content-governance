<%@ page contentType="text/html; charset=UTF-8" language="java" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--
  One non-authorised term, as jContent's preview shows it (the preview renders the default view of
  content outside pages). Terms are never placed on pages. Labels are the type's own, from the
  module's resource bundles; every value is escaped, since glossary editors write them.
--%>
<template:addResources type="css" resources="glossary-term.css"/>
<c:set var="term" value="${currentNode.properties['term'].string}"/>
<c:set var="matchMode" value="${currentNode.properties['matchMode'].string}"/>
<c:set var="reason" value="${currentNode.properties['reason'].string}"/>
<c:set var="alternative" value="${currentNode.properties['alternative'].string}"/>
<article class="cg-glossary-term">
    <p class="cg-glossary-term__type"><fmt:message key="ccs_glossaryTerm"/></p>
    <h2 class="cg-glossary-term__term">${fn:escapeXml(term)}</h2>
    <dl class="cg-glossary-term__fields">
        <c:if test="${not empty matchMode}">
            <div class="cg-glossary-term__field">
                <dt><fmt:message key="ccs_glossaryTerm.matchMode"/></dt>
                <dd><fmt:message key="ccs_glossaryTerm.matchMode.${fn:escapeXml(matchMode)}"/></dd>
            </div>
        </c:if>
        <c:if test="${not empty reason}">
            <div class="cg-glossary-term__field">
                <dt><fmt:message key="ccs_glossaryTerm.reason"/></dt>
                <dd class="cg-glossary-term__reason">${fn:escapeXml(reason)}</dd>
            </div>
        </c:if>
        <c:if test="${not empty alternative}">
            <div class="cg-glossary-term__field">
                <dt><fmt:message key="ccs_glossaryTerm.alternative"/></dt>
                <dd class="cg-glossary-term__alternative">${fn:escapeXml(alternative)}</dd>
            </div>
        </c:if>
    </dl>
</article>
