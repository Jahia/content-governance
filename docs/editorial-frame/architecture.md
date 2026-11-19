# How the editorial frame sits in the module

The editorial frame is a part of the Content Governance bundle that the three sections do not use
yet. It has its own Java packages, its own content types and its own endpoints. Until version 1.12
a separate module named editorial-frame held this code, and the move changed no behaviour.
[`guide.md`](guide.md) states what the frame holds and who reads it.

The diagram shows who calls the frame, and which workspace each path reads or writes.

```
ai-assistant, other modules ──► EditorialFrameService ─┬─► FrameReader, checkers ─► JCR (live workspace)
mcp-server (optional) ──► the four tools ──────────────┤
                                                       └─► skill registry: site nodes, group nodes, module folders
/modules/editorial-frame/import, setup, voice ──► servlets ─► JCR (default workspace)
```

## Code

Java, under `org.jahia.pm.modules.editorialframe`. The packages keep the names they had in the
module editorial-frame, so a reader of the frame needs no change.

| Package | Holds |
|---|---|
| `api` (exported at version 0.1.0) | `EditorialFrameService` and the records that it gives: `Frame`, `FrameContext`, `FrameRequest`, `WritingRule`, `RuleViolation`, `GlossaryTerm`, `Persona` and the skill records. |
| `internal` | `EditorialFrameServiceImpl`, the component that publishes the service. |
| `internal.frame` | `FrameReader` reads the frame of a site. `ContextBuilder` and `Essentials` build the text for a model. `RuleChecker` and `GlossaryChecker` check a text. |
| `internal.skills` | The skill registry: `JcrSkills` for the nodes of a site, `BundleSkills` for the folders of the modules, and `GeneratedFrameSkill` for the skill `brand-frame`. |
| `internal.importer` | The import of a zip of skills, rules and glossary terms. |
| `internal.web` | The three servlets: `ImportServlet`, `SetupServlet` and `VoiceServlet`. |
| `internal.tools` | The four tools for AI agents. This package alone names a type of mcp-server. |

## What the bundle declares for the frame

| Item | Where | Why |
|---|---|---|
| Content types `efnt:*` and `efmix:*` | `src/main/resources/META-INF/editorial-frame.cnd` | Jahia reads every CND file of `META-INF`. The frame keeps its own file and its own namespaces. |
| Labels of the content types | `src/main/resources/resources/content-governance.properties` | Jahia reads the labels of a type from the resource bundle of the module that declares it. The labels exist in English only. |
| Skills `article-brief` and `seo-review` | `src/main/resources/skills/`, named by the manifest header `Jahia-Skills` | The skill registry reads the folder that this header names, in every active module. |
| CSRF exemption of the three endpoints | `META-INF/configurations/org.jahia.modules.jahiacsrfguard-editorial-frame.cfg` | Each endpoint protects itself, as the file states. The other servlets of the module stay under the CSRF guard. |
| YAML parser | `snakeyaml`, embedded in the bundle | The frontmatter of `SKILL.md` and the rule files of an import are YAML. |
| No import of `org.yaml.snakeyaml` | An execution of `build-helper-maven-plugin` in `pom.xml` | See [The embedded YAML parser](#the-embedded-yaml-parser). |

The version of the exported package `api` is fixed at 0.1.0 in `pom.xml`. A module built against
editorial-frame imports the range `[0.1,1)`. The framework does not wire an import that is outside
the exported version, and it shows no error for an optional import.

The values of the package are Java records. A record compares by value, so two rules with the same
components are equal. The constructors of `SkillSummary` and `WritingRule` that took nine
parameters are gone, and every other constructor and accessor kept its signature. No known reader
called the two removed constructors, so the version stayed 0.1.0.

## The tools and the optional module mcp-server

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

## The embedded YAML parser

The bundle embeds snakeyaml 2.2 and must load every class of it from that copy. The manifest of
snakeyaml imports 16 of its own 21 packages. The Jahia Maven plugin copies these imports into the
import list that it generates for the module, as optional imports. Jahia 8.2.4.0 exports snakeyaml
2.6.0. The framework then wires these 16 packages to the copy of Jahia, and leaves the 5 others to
the embedded copy. The frame service fails at start with a `LinkageError`, when it reads the first
`SKILL.md`.

An execution of `build-helper-maven-plugin` removes the snakeyaml entries from the generated list
before the bundle plugin reads it. The jar must not import a package of `org.yaml.snakeyaml`.

## Do not run the module editorial-frame beside this one

Both modules declare the content types `efnt:*` and `efmix:*`, export the same Java package and
register servlets on the same paths. Uninstall editorial-frame before you install this version, as
the root [`README.md`](../../README.md) states.

## Known gaps

- The skills that the module ships now carry the module name `content-governance` in their
  `source`. In the module editorial-frame the name was `editorial-frame`.
- No Cypress spec covers the frame. Its tests are the unit tests under
  `src/test/java/org/jahia/pm/modules/editorialframe`.
- The frame writes the folder `contents/ai-assistant`, and its endpoints stay under
  `/modules/editorial-frame/`. Both names date from the module editorial-frame.
