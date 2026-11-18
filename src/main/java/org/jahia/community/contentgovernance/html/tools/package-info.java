/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
/**
 * The two tools of the HTML check for AI agents: {@code audit.accessibility} and {@code audit.links}.
 *
 * <p>This package names types of mcp-server, which is an optional module. The bundle imports the
 * SPI package of mcp-server dynamically, and each tool is a delayed component: its class loads when
 * mcp-server asks for the tool, and never without mcp-server. A class outside this package must not
 * name a class of this package.</p>
 */
package org.jahia.community.contentgovernance.html.tools;
