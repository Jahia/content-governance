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
    private static final String TO_STRING = "toString";
    private static final String HASH_CODE = "hashCode";
    private static final String EQUALS = "equals";

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

    /** {@code setAttribute} of a request or a session: a null value removes the attribute. */
    private static Object setAttribute(Map<String, Object> attributes, Object[] args) {
        if (args[1] == null) {
            attributes.remove(args[0]);
        } else {
            attributes.put((String) args[0], args[1]);
        }
        return null;
    }

    private static Object removeAttribute(Map<String, Object> attributes, Object[] args) {
        attributes.remove(args[0]);
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
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAttribute" -> attributes.get(args[0]);
                case "getAttributeNames" -> Collections.enumeration(List.copyOf(attributes.keySet()));
                case "setAttribute" -> setAttribute(attributes, args);
                case "removeAttribute" -> removeAttribute(attributes, args);
                case "isRequestedSessionIdValid" -> session != null;
                case "getRequestedSessionId" -> session == null ? null : session.getId();
                case "getSession" -> session(args == null || args.length == 0 || (Boolean) args[0]);
                case "changeSessionId" -> session(true).getId();
                case "getServletContext" -> servletContext();
                case "getRequestDispatcher" -> dispatcher((String) args[0]);
                case "getDispatcherType" -> DispatcherType.REQUEST;
                case "getInputStream" -> emptyInput();
                case "getReader" -> new BufferedReader(new StringReader(""));
                case "getRealPath" -> servletContext().getRealPath((String) args[0]);
                case TO_STRING -> "InProcessHttpRequest[" + requestUri() + "]";
                case HASH_CODE -> System.identityHashCode(proxy);
                case EQUALS -> proxy == args[0];
                default -> describe(method);
            };
        }

        /** What the request states about itself: its address, its user, its language and its encoding. */
        private Object describe(Method method) {
            return switch (method.getName()) {
                case "getMethod" -> "GET";
                case "getProtocol" -> "HTTP/1.1";
                case "getScheme" -> "http";
                case "getServerName", "getLocalName", "getRemoteHost" -> target.serverName();
                case "getServerPort", "getLocalPort", "getRemotePort" -> 80;
                case "getLocalAddr", "getRemoteAddr" -> "127.0.0.1";
                case "getContextPath" -> Jahia.getContextPath();
                case "getServletPath" -> target.servletPath();
                case "getPathInfo" -> target.pathInfo();
                case "getRequestURI" -> requestUri();
                case "getRequestURL" -> new StringBuffer("http://" + target.serverName() + requestUri());
                case "getLocale" -> target.locale();
                case "getLocales" -> Collections.enumeration(List.of(target.locale()));
                case "getCharacterEncoding" -> UTF_8;
                case "getParameterMap" -> Map.of();
                case "getUserPrincipal" -> target.user();
                case "getRemoteUser" -> target.user() == null ? null : target.user().getName();
                case "getPathTranslated", "getQueryString", "setCharacterEncoding", "getParameterValues",
                     "getParameter", "getHeader", "getContentType", "getCookies", "getAuthType" -> null;
                case "isUserInRole", "isSecure", "isAsyncStarted", "isAsyncSupported", "isRequestedSessionIdFromCookie",
                     "isRequestedSessionIdFromURL", "isRequestedSessionIdFromUrl" -> false;
                default -> fallback(method);
            };
        }

        private static Object fallback(Method method) {
            String name = method.getName();
            if (name.startsWith("startAsync") || "upgrade".equals(name) || "authenticate".equals(name)
                    || "login".equals(name)) {
                throw new IllegalStateException("A request rendered in process does not support " + name + ".");
            }
            return defaultValue(method.getReturnType());
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

        private static ServletInputStream emptyInput() {
            return new ServletInputStream() {
                @Override public boolean isFinished() { return true; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { /* nothing to read */ }
                @Override public int read() { return -1; }
            };
        }
    }

    private static final class SessionHandler implements InvocationHandler {
        private final String id = UUID.randomUUID().toString();
        private final long created = System.currentTimeMillis();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getId" -> id;
                case "getCreationTime", "getLastAccessedTime" -> created;
                case "getAttribute", "getValue" -> attributes.get(args[0]);
                case "getAttributeNames" -> Collections.enumeration(List.copyOf(attributes.keySet()));
                case "getValueNames" -> attributes.keySet().toArray(new String[0]);
                case "setAttribute", "putValue" -> setAttribute(attributes, args);
                case "removeAttribute", "removeValue" -> removeAttribute(attributes, args);
                case "getServletContext" -> servletContext();
                case "getMaxInactiveInterval" -> 60;
                case "isNew" -> true;
                case "invalidate" -> {
                    attributes.clear();
                    yield null;
                }
                case TO_STRING -> "InProcessHttpSession[" + id + "]";
                case HASH_CODE -> System.identityHashCode(proxy);
                case EQUALS -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
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
            return switch (method.getName()) {
                case "getWriter" -> writer;
                case "getOutputStream" -> output;
                case "getStatus" -> status;
                case "setStatus", "sendError" -> {
                    status = (Integer) args[0];
                    yield null;
                }
                case "sendRedirect" -> {
                    status = HttpServletResponse.SC_FOUND;
                    yield null;
                }
                case "getContentType" -> contentType;
                case "setContentType" -> {
                    contentType = (String) args[0];
                    yield null;
                }
                case "getCharacterEncoding" -> UTF_8;
                case "getLocale" -> locale;
                case "setLocale" -> {
                    locale = (Locale) args[0];
                    yield null;
                }
                case "encodeURL", "encodeUrl", "encodeRedirectURL", "encodeRedirectUrl" -> args[0];
                case "isCommitted" -> false;
                case "getBufferSize" -> 8192;
                case TO_STRING -> "InProcessHttpResponse[" + status + "]";
                case HASH_CODE -> System.identityHashCode(proxy);
                case EQUALS -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }
    }
}
