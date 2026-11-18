/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
/**
 * The four tools of the editorial frame for AI agents: {@code frame.get}, {@code frame.check},
 * {@code skill.list} and {@code skill.read}.
 *
 * <p>The only package of the module that names a type of mcp-server, which is an optional module.
 * The bundle imports the SPI package of mcp-server dynamically, and each tool is a delayed
 * component: its class loads when mcp-server asks for the tool, and never without mcp-server.
 * A class outside this package must not name a class of this package.</p>
 */
package org.jahia.pm.modules.editorialframe.internal.tools;
