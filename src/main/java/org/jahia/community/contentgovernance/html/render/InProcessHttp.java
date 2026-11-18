/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.community.contentgovernance.html.render;

import org.jahia.bin.Jahia;
import org.jahia.bin.listeners.JahiaContextLoaderListener;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ReadListener;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A request and a response for a render that no HTTP request carries, such as a scheduled scan or
 * a tool call of an AI agent.
 *
 * <p>The request says GET on the render servlet of this server, for the user, the locale and the
 * server name given. It has no header, no cookie and no parameter, and its session lives as long as
 * the request. A dispatch to a JSP goes through the servlet context of the Jahia webapp, as a real
 * request does. The response keeps what a view writes, and nothing reaches a client.</p>
 *
 * <p>Both objects are dynamic proxies, so they implement every method of the servlet API that Jahia
 * runs on.</p>
 */
final class InProcessHttp {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private InProcessHttp() {
    }

    /** What the request names: the servlet path, the path after it, the server name, the user and the locale. */
    record Target(String servletPath, String pathInfo, String serverName, Principal user, Locale locale) {
    }

    /** A GET request on the render servlet for the target. */
    static HttpServletRequest request(Target target) {
        return (HttpServletRequest) Proxy.newProxyInstance(InProcessHttp.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, new RequestHandler(target));
    }

    /** A response that keeps the output and the status, and sends nothing. */
    static HttpServletResponse response(Locale locale) {
        return (HttpServletResponse) Proxy.newProxyInstance(InProcessHttp.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, new ResponseHandler(locale));
    }

    private static ServletContext servletContext() {
        return JahiaContextLoaderListener.getServletContext();
    }

    /** The default answer of a method that this class does not name, from its return type. */
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return -1;
        }
        if (type == long.class) {
            return -1L;
        }
        if (type == Enumeration.class) {
            return Collections.emptyEnumeration();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (type == Collection.class) {
            return List.of();
        }
        return null;
    }

    private static final class RequestHandler implements InvocationHandler {
        private final Target target;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private HttpSession session;

        RequestHandler(Target target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getAttribute":
                    return attributes.get((String) args[0]);
                case "getAttributeNames":
                    return Collections.enumeration(List.copyOf(attributes.keySet()));
                case "setAttribute":
                    if (args[1] == null) {
                        attributes.remove((String) args[0]);
                    } else {
                        attributes.put((String) args[0], args[1]);
                    }
                    return null;
                case "removeAttribute":
                    attributes.remove((String) args[0]);
                    return null;
                case "getMethod":
                    return "GET";
                case "getProtocol":
                    return "HTTP/1.1";
                case "getScheme":
                    return "http";
                case "getServerName":
                case "getLocalName":
                case "getRemoteHost":
                    return target.serverName();
                case "getServerPort":
                case "getLocalPort":
                case "getRemotePort":
                    return 80;
                case "getLocalAddr":
                case "getRemoteAddr":
                    return "127.0.0.1";
                case "getContextPath":
                    return Jahia.getContextPath();
                case "getServletPath":
                    return target.servletPath();
                case "getPathInfo":
                    return target.pathInfo();
                case "getPathTranslated":
                    return null;
                case "getRequestURI":
                    return requestUri();
                case "getRequestURL":
                    return new StringBuffer("http://" + target.serverName() + requestUri());
                case "getQueryString":
                    return null;
                case "getLocale":
                    return target.locale();
                case "getLocales":
                    return Collections.enumeration(List.of(target.locale()));
                case "getCharacterEncoding":
                    return UTF_8;
                case "setCharacterEncoding":
                    return null;
                case "getParameterMap":
                    return Map.of();
                case "getParameterValues":
                case "getParameter":
                case "getHeader":
                case "getContentType":
                case "getCookies":
                case "getAuthType":
                    return null;
                case "getUserPrincipal":
                    return target.user();
                case "getRemoteUser":
                    return target.user() == null ? null : target.user().getName();
                case "isUserInRole":
                case "isSecure":
                case "isAsyncStarted":
                case "isAsyncSupported":
                case "isRequestedSessionIdFromCookie":
                case "isRequestedSessionIdFromURL":
                case "isRequestedSessionIdFromUrl":
                    return false;
                case "isRequestedSessionIdValid":
                    return session != null;
                case "getRequestedSessionId":
                    return session == null ? null : session.getId();
                case "getSession":
                    return session(args == null || args.length == 0 || (Boolean) args[0]);
                case "changeSessionId":
                    return session(true).getId();
                case "getServletContext":
                    return servletContext();
                case "getRequestDispatcher":
                    return dispatcher((String) args[0]);
                case "getDispatcherType":
                    return DispatcherType.REQUEST;
                case "getInputStream":
                    return emptyInput();
                case "getReader":
                    return new BufferedReader(new StringReader(""));
                case "getRealPath":
                    return servletContext().getRealPath((String) args[0]);
                case "toString":
                    return "InProcessHttpRequest[" + requestUri() + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    if (method.getName().startsWith("startAsync") || "upgrade".equals(method.getName())
                            || "authenticate".equals(method.getName()) || "login".equals(method.getName())) {
                        throw new IllegalStateException("A request rendered in process does not support "
                                + method.getName() + ".");
                    }
                    return defaultValue(method.getReturnType());
            }
        }

        private String requestUri() {
            return Jahia.getContextPath() + target.servletPath() + (target.pathInfo() == null ? "" : target.pathInfo());
        }

        private RequestDispatcher dispatcher(String path) {
            if (path == null) {
                return null;
            }
            String absolute = path.startsWith("/") ? path : target.servletPath() + "/" + path;
            return servletContext().getRequestDispatcher(absolute);
        }

        private HttpSession session(boolean create) {
            if (session == null && create) {
                session = (HttpSession) Proxy.newProxyInstance(InProcessHttp.class.getClassLoader(),
                        new Class<?>[]{HttpSession.class}, new SessionHandler());
            }
            return session;
        }
    }

    private static ServletInputStream emptyInput() {
        return new ServletInputStream() {
            @Override public boolean isFinished() { return true; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) { /* nothing to read */ }
            @Override public int read() { return -1; }
        };
    }

    private static final class SessionHandler implements InvocationHandler {
        private final String id = UUID.randomUUID().toString();
        private final long created = System.currentTimeMillis();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getId":
                    return id;
                case "getCreationTime":
                case "getLastAccessedTime":
                    return created;
                case "getAttribute":
                case "getValue":
                    return attributes.get((String) args[0]);
                case "getAttributeNames":
                    return Collections.enumeration(List.copyOf(attributes.keySet()));
                case "getValueNames":
                    return attributes.keySet().toArray(new String[0]);
                case "setAttribute":
                case "putValue":
                    if (args[1] == null) {
                        attributes.remove((String) args[0]);
                    } else {
                        attributes.put((String) args[0], args[1]);
                    }
                    return null;
                case "removeAttribute":
                case "removeValue":
                    attributes.remove((String) args[0]);
                    return null;
                case "getServletContext":
                    return servletContext();
                case "getMaxInactiveInterval":
                    return 60;
                case "isNew":
                    return true;
                case "invalidate":
                    attributes.clear();
                    return null;
                case "toString":
                    return "InProcessHttpSession[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static final class ResponseHandler implements InvocationHandler {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final PrintWriter writer = new PrintWriter(new OutputStreamWriter(body, StandardCharsets.UTF_8), true);
        private final ServletOutputStream output = new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener writeListener) { /* writes never block */ }
            @Override public void write(int b) { body.write(b); }
        };
        private Locale locale;
        private int status = HttpServletResponse.SC_OK;
        private String contentType;

        ResponseHandler(Locale locale) {
            this.locale = locale;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getWriter":
                    return writer;
                case "getOutputStream":
                    return output;
                case "getStatus":
                    return status;
                case "setStatus":
                case "sendError":
                    status = (Integer) args[0];
                    return null;
                case "sendRedirect":
                    status = HttpServletResponse.SC_FOUND;
                    return null;
                case "getContentType":
                    return contentType;
                case "setContentType":
                    contentType = (String) args[0];
                    return null;
                case "getCharacterEncoding":
                    return UTF_8;
                case "getLocale":
                    return locale;
                case "setLocale":
                    locale = (Locale) args[0];
                    return null;
                case "encodeURL":
                case "encodeUrl":
                case "encodeRedirectURL":
                case "encodeRedirectUrl":
                    return args[0];
                case "isCommitted":
                    return false;
                case "getBufferSize":
                    return 8192;
                case "toString":
                    return "InProcessHttpResponse[" + status + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
