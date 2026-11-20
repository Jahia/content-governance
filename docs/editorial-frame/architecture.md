# How the editorial frame sits in the module

The editorial frame is the part of the Content Governance bundle that holds the writing rules and
the translation glossary of a brand, and checks texts against them with no model. It has its own
Java packages, its own content types and its own tool. Until version 1.12 a separate module named
editorial-frame held this code. [`guide.md`](guide.md) states what the frame holds and who reads
it.

The diagram shows who calls the frame, and which workspace each path reads.

```
ai-assistant, other modules ──► EditorialFrameService ──► FrameReader, checkers ──► JCR (live workspace)
mcp-server (optional) ──► frame.check ──────────────────┘
```

## Code

Java, under `org.jahia.pm.modules.editorialframe`. The packages keep the names they had in the
module editorial-frame.

| Package | Holds |
|---|---|
| `api` (exported at version 1.0.0) | `EditorialFrameService` and the records that it gives: `Frame`, `WritingRule`, `RuleViolation` and `GlossaryTerm`. |
| `internal` | `EditorialFrameServiceImpl`, the component that publishes the service. |
| `internal.frame` | `FrameReader` reads the rules and the glossary of a site. `RuleChecker` and `GlossaryChecker` check a text. |
| `internal.tools` | The tool `frame.check` for AI agents. This package alone names a type of mcp-server. |

## What the bundle declares for the frame

| Item | Where | Why |
|---|---|---|
| Content types `efnt:*` and `efmix:*` | `src/main/resources/META-INF/editorial-frame.cnd` | Jahia reads every CND file of `META-INF`. The frame keeps its own file and its own namespaces. |
| Labels of the content types | `src/main/resources/resources/content-governance.properties` | Jahia reads the labels of a type from the resource bundle of the module that declares it. The labels exist in English only. |

The version of the exported package `api` is fixed at 1.0.0 in `pom.xml`. Version 0.1.0 also gave
the text of the frame for a model, and the parts of the frame that only a model reads. They moved to
ai-assistant, which exports `org.jahia.modules.aiassistant.frame.api`. A module built against 0.1.0
imports the range `[0.1,1)`: the framework does not wire it to 1.0.0, and the module runs without
the frame instead of calling a method that is gone.

The values of the package are Java records. A record compares by value, so two rules with the same
components are equal.

## The tool and the optional module mcp-server

The module mcp-server serves tools to AI agents. A tool is an OSGi service of the type
`JahiaMcpTool`, which mcp-server exports in the package `org.jahia.modules.mcp.spi.tool`.
Content Governance starts without mcp-server, for example on a site that uses the checks only.
Three settings make this work:

- `jahia-depends` names `mcp-server=optional`, so Jahia starts mcp-server first when it is there.
- The bundle imports the package of mcp-server with `DynamicImport-Package`. A static optional
  import stays unwired when mcp-server is installed after the module, because Jahia does not
  refresh the module then.
- Each tool is a delayed component. The component runtime registers the service by its name, and
  loads the class of the tool only when mcp-server asks for the service.

Two rules keep this true:

- A tool component never sets `immediate = true`. An immediate component loads its class when the
  bundle starts, and the load fails without mcp-server.
- A class outside `internal.tools` never names a class of `internal.tools`.

## Do not run the module editorial-frame beside this one

Both modules declare the content types `efnt:*` and `efmix:*`, and export the same Java package.
Uninstall editorial-frame before you install this version, as the root [`README.md`](../../README.md)
states.

## Known gaps

- No Cypress spec covers the frame. Its tests are the unit tests under
  `src/test/java/org/jahia/pm/modules/editorialframe`.
