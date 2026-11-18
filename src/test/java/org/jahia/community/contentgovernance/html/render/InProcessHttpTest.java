/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.render;

import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InProcessHttpTest {

    private static HttpServletRequest request() {
        return InProcessHttp.request(new InProcessHttp.Target("/cms/render", "/default/en/sites/digitall/home.html",
                "www.digitall.example", null, Locale.FRENCH));
    }

    @Test
    void describesAGetOnTheServerOfTheSite() {
        HttpServletRequest request = request();
        assertEquals("GET", request.getMethod());
        assertEquals("www.digitall.example", request.getServerName());
        assertEquals(80, request.getServerPort());
        assertEquals("/default/en/sites/digitall/home.html", request.getPathInfo());
        assertEquals(Locale.FRENCH, request.getLocale());
        assertNull(request.getHeader("Cookie"));
        assertNull(request.getQueryString());
        assertFalse(request.isSecure());
        assertNull(request.getRemoteUser());
    }

    @Test
    void keepsTheAttributesOfTheRequestAndOfItsSession() {
        HttpServletRequest request = request();
        request.setAttribute("a", "1");
        assertEquals("1", request.getAttribute("a"));
        request.setAttribute("a", null);
        assertNull(request.getAttribute("a"));

        assertNull(request.getSession(false));
        HttpSession session = request.getSession();
        assertSame(session, request.getSession(false));
        assertTrue(request.isRequestedSessionIdValid());
        session.setAttribute("b", 2);
        assertEquals(2, session.getAttribute("b"));
        session.removeAttribute("b");
        assertNull(session.getAttribute("b"));
        assertEquals(60, session.getMaxInactiveInterval());
    }

    @Test
    void refusesAnAsynchronousRequest() {
        HttpServletRequest request = request();
        assertThrows(IllegalStateException.class, request::startAsync);
    }

    @Test
    void keepsTheStatusAndTheTypeOfTheResponse() throws IOException {
        HttpServletResponse response = InProcessHttp.response(Locale.ENGLISH);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        response.setStatus(404);
        response.setContentType("text/html");
        assertEquals(404, response.getStatus());
        assertEquals("text/html", response.getContentType());
        response.sendRedirect("/x");
        assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
        assertEquals("/x", response.encodeURL("/x"));
        assertFalse(response.isCommitted());
        assertEquals(response, response);
    }
}
