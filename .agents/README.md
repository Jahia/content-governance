# Content Governance - agent harness

Track 2 OSGi UI extension with Java services: Maven bundle, React 18, Webpack + Module Federation,
`@jahia/ui-extender`, servlets, a GraphQL extension and a CND. Generic Jahia rules live in AIStartupKit
(`CLAUDE.md`, skills `jahia-osgi-ui-extension`, `jahia-java-security`, `jahia-java-jcr`,
`jahia-java-osgi`); this file keeps what is specific to this module.

Merged on 2026-10-29 from three modules, whose history stays in their own repositories:
`Jahia/geo-readiness` (1.6.0-SNAPSHOT), `Jahia/page-audit` (1.7.0) and
`Jahia/content-compliance-scan` (1.0.1).

## Layout

| Path | What |
|---|---|
| `src/javascript/drawer/` | The header action and the one drawer: header, section bar, sections mounted on first visit; `useDock.js` docks it beside the page under jContent's header; `entries.js` and `FindingActions.jsx` read the tabs and finding actions other modules add (`docs/drawer-entries.md`) |
| `src/javascript/audit/` | Page quality section: preview frame, analyzers (`analyzers/`), tabs, shared audits (`auditHistory.js`), the Page quality dashboard (`dashboard/`) |
| `src/main/java/.../audit/` | Shared audits: `service/spi/AuditHistoryService` and its DS component, `AuditResults` (input check), `model/`, `graphql/` (`AuditAccess`, `AuditFields`, types) |
| `src/javascript/publish/` | The line at the top of jContent's Publish menu (`PublishCheckAction`) |
| `src/javascript/geo/` | GEO readiness section (`GeoSection`), its tabs, the GEO dashboard, the BotRank screen |
| `src/javascript/compliance/` | Compliance section and the compliance dashboard |
| `src/javascript/settings/` | Site Settings > Content governance: Features, BotRank |
| `src/javascript/common/` | i18n hook, settings query and hook, `SectionGate`, URL helper |
| `src/main/java/.../geo/spi/` | `GeoReportWriter` (exported, `1.0.0`), which another module implements to write the report of the GEO dashboard. Its contract is in `docs/geo-readiness/architecture.md`, "The GEO report writer" |
| `src/main/java/.../settings/` | `Features` (section and tab ids) and `SiteFeatures` (the server-side gate) |
| `src/main/java/.../graphql/` | `contentGovernance { siteSettings }`, `saveSiteSettings`, `complianceScan` |
| `src/main/java/.../{audit,geo,compliance}/` | Each section's server side, as in the original modules |
| `src/main/resources/ccs_glossaryTerm/html/` | The glossary term's default view, which jContent's Preview shows |
| `src/main/java/org/jahia/pm/modules/editorialframe/` | The editorial frame, moved from the module editorial-frame (1.12.0): the exported `api`, the service, the checkers of the writing rules and the glossary, and the tool `frame.check` for AI agents |
| `src/main/java/.../html/` | The HTML check on the server: the DS service `HtmlCheck`, `render/` (a page or one item rendered with no HTTP request), `rules/`, `links/` (the guard against internal addresses is in `ExternalLinkChecker`) and `tools/`, the two tools for AI agents. `tools/` is the only package of `html/` that names a type of mcp-server; `docs/page-quality/html-rules.md` lists the rules |
| `src/main/resources/META-INF/editorial-frame.cnd` | The content types of the frame (`efnt`, `efmix`) |

## Invariants

- **Every language handed to the browser's formatters goes through `common/locale.js`** (`toLocaleTag`,
  `languageDisplayName`): jContent gives the Jahia form (`zh_CN`), `Intl` and `toLocaleString` take
  `zh-CN` and throw `RangeError` on the other; the drawer stopped on a `zh_CN` page before 1.9.1.
  `languages.spec.ts` audits a `zh_CN` page.
- **In Java, a language becomes a locale through `util.Locales.of(language)`**, never
  `Locale.forLanguageTag`: that one reads `zh-CN` and answers the empty locale for Jahia's `zh_CN`
  without failing, and a `live` session opened with it finds no page. GEO readiness reported every
  published page of a `zh_CN` site as never published until 1.10.1 (30 call sites). `LocalesTest`
  and `languages.spec.ts` (a page published in `zh_CN`) keep it so.
- **axe-core is on its latest release whenever this module is worked on** (Stephane, 2026-10-31):
  `npm view axe-core version`, then raise the `axe-core` range in `package.json` to that version and
  refresh `yarn.lock`. Read the release notes and say in the changelog fragment what editors will see:
  a rule turned on by default or new violations raise the Accessibility tab's counts, false-positive
  fixes lower them. The audit runs by WCAG tag, so new rules need no code. Dependabot also proposes
  axe-core updates; do not wait for it.
- **The automated tests follow what axe-core proposes** (Stephane, 2026-10-31): in the same change,
  `tests/cypress/e2e/audit.spec.ts` gets a case in its fixture, and an entry in its table and
  `EXPECTED_RULES`, for every rule the release turns on by default or extends; run it on the new build
  and once with that case removed (it must fail). The spec also fails when the bundled engine is not
  the version `package.json` declares.
- React **18** only (jContent singleton). Moonstone **2.20.3**, the version jContent 3.7.1 and 3.8
  run: no API newer than that. Badge colours are `accent`, `success`, `danger` only.
- Module id, bundle symbolic name and i18n namespace: `content-governance` (`common/module.js`,
  `common/i18n.js`, `SiteFeatures.MODULE`). Rename all or none.
- Translations: one namespace, keys prefixed per part (`common`, `drawer`, `settings`, `audit`,
  `geo`, `compliance`), read through `useGovernanceTranslation(prefix)`, never `useTranslation`
  directly. `en.json`, `fr.json`, `de.json` and `es.json` keep the same keys and placeholders
  (`src/test/javascript/locales.test.js` fails otherwise), and `init.js` lists each file in `LOCALES`;
  a new key goes in all four in the same change. German and Spanish were machine-drafted (2026-10-31)
  and await a native review; jContent's own German labels (`Zusätzlich`, `Inhalts-Ordner`, `Live`)
  are reused where a text names a jContent menu, and the drawer is `Panel` in German.
- Feature ids (`audit`, `audit.seo`, `geo.citations`, `compliance`...) are stored per site and are
  part of the GraphQL API: never rename one. A site stores the ids it switched **off**, so a tab added
  later starts on. Add a tab in `Features.SECTIONS` and in the section's tab list together.
- Every endpoint checks the feature on the server: GEO servlets in `SiteScope.require`, compliance
  in `GqlComplianceScan.requireModuleEnabled`, BotRank in `CitationsAccess` (`geo.citations`).
  Client gates only hide.
- **The module uses no type of an AI module** (Jahia/content-governance#72). No class names a type
  or a package of genai-connector or of ai-assistant. The manifest imports nothing of either, in
  `Import-Package` or in `DynamicImport-Package` (`pom.xml`). A module that calls a model registers
  its features here: content checks (`check.spi`), drawer tabs (`docs/drawer-entries.md`) and the
  GEO report writer (`geo.spi`).
- Legacy storage, kept on purpose so the three old modules can be replaced in place: GEO scans and
  the BotRank binding under `/sites/<key>/geo-readiness`, BotRank factory PID
  `org.jahia.se.modules.georeadiness.botrank-<id>`, CND namespaces `ccs`/`ccsmix`. The old modules
  must be uninstalled before this one is installed (same CND types, same GraphQL field). Jahia then
  keeps the `ccs:` types under the old systemId and ignores ours, which hides them from New content;
  `compliance/service/internal/GlossaryTypeOwner` takes them over at start (1.1.0). Never take types
  from a module that is still installed.
- Settings live on `/sites/<key>/content-governance` (`nt:unstructured` + `jmix:nolive`,
  `disabledFeatures`).
- Module state nodes (`content-governance`, `geo-readiness` and its `botrank` child) do not inherit the
  site's permissions (`settings/PrivateStore`): every read and write goes through a system session after
  the module's own check. Never read or write them with the caller's session, and never trust a value
  read from them more than the check that wrote it. Their `j:acl` child is not a language: loops over
  the store's children filter with `SiteScope.isLanguage`. `ScanStore.protectAll` (called by both
  lifecycles at start, first one wins) sets up nodes found without it and switches off the GEO
  schedule and the BotRank sync they hold, with a note; a site administrator saves them again
  (decision 2026-10-29).
- Every GEO servlet's POST handler reads its body through `GeoServlet.jsonBody` / `isJsonRequest`
  (exact `application/json`, same-site browser requests only).
- Every servlet is under Jahia's CSRF guard (`org.jahia.modules.jahiacsrfguard-content-governance.cfg`,
  no first-line marker on purpose). Every call from the UI goes through `common/guardedFetch.js`,
  never a bare `fetch`; the servlets take no API token (decision 2026-10-30, browser only).
- Build with Maven **3.8** and Java 17 (`mise.toml`), always `clean install`.
- **The `Import-Package` instruction starts with `${jahia.plugin.projectPackageImport},`** (1.7.0): it
  carries the tag libraries the JSP views need, which the module's own instruction would otherwise
  drop; the term view then fails "Unable to load tag handler class". Check the jar imports
  `org.jahia.taglibs.template.include`.
- **Glossary terms have a default view** (`ccs_glossaryTerm/html/glossaryTerm.jsp`, CSS
  `css/glossary-term.css`): jContent's Preview renders it (never a `cm` view). Labels come from the
  type's own keys in the four bundles, every value is escaped; `compliance.spec.ts` renders it as
  jContent does.
- `robots` and `llms` are optional dependencies (`=optional`): only the GEO robots.txt editor and the
  read-only llms.txt tab need them. llms.txt is written in the `llms` module, never here.
- **The editorial frame keeps the names of the module editorial-frame** (1.12.0): the Java packages
  `org.jahia.pm.modules.editorialframe.*`, the namespaces `efnt` and `efmix` and the tool name. The
  export of `api` is at version `1.0.0`, since its methods for a model moved to ai-assistant: a
  reader built for `0.1.0` imports `[0.1,1)` and does not wire. `docs/editorial-frame/architecture.md`
  has the wiring.
- **`mcp-server` is optional, and only `editorialframe.internal.tools` names its types**: the bundle
  imports `org.jahia.modules.mcp.spi.tool` with `DynamicImport-Package`, and each tool is a delayed
  component. Never set `immediate = true` on a tool, and never name a class of that package from
  another package: either one loads the class at start, which fails without mcp-server.
- **The jar never imports `org.yaml.snakeyaml`**: snakeyaml is embedded, and the build removes the
  optional imports that the Jahia plugin generates for it (`build-helper-maven-plugin` in
  `pom.xml`). With them the read of the rule files fails with a `LinkageError`, on a Jahia that
  exports snakeyaml (8.2.4.0 does). Check the jar after a change of the parent or of the plugin.
- **A readability score uses a formula published for the page language** (2026-10-31): one entry
  per language in `FORMULAS` (`audit/analyzers/readability.js`), an adaptation of Flesch on its
  0-100 scale so the bands and advice stay shared, its vowels in `VOWELS`, its source in the file
  comment, and a hand-computed case in `src/test/javascript/readability.test.js`. A language without
  one gets no score, never another language's; a language written without spaces gets no word count.
  A scale that reads the other way round (LIX) sets `scale` and is judged by `band`, never by
  comparing the raw score with Flesch thresholds.
- **jContent's start loads the button and one language only** (2026-10-31): `init.js` imports the
  registration code, `GovernanceAction` and `SectionGate`; a new screen, dashboard or drawer section
  is loaded with `React.lazy` (`named()` in `init.js`, `loadDrawer` in `GovernanceAction`), and a new
  language file goes in `LOCALES`. The drawer spec fails when jContent loads 200 kB or more of the
  module where the button does not show.
- **No audit step can hold the drawer** (2026-10-31): a Page quality step that waits on the network
  goes through `runStep` with its own entry in `STEP_LIMITS_MS`, and its tab shows `IncompleteStep`
  when it does not finish; the frame is audited after `FRAME_LOAD_LIMIT_MS` when its load event does
  not come. The audit spec's never-answering page proves both.
- **Shared audits are counted and checked on the server** (2026-10-31): the drawer sends the
  results; `AuditResults` keeps the tab results and `page` only, `AuditSummary` counts the issues,
  `AuditAccess` checks `jContentAccess`, the section and the page with the caller's session before
  the store's system session. Only complete audits are stored, the last 10 per page and language;
  a tab added to the results gets its key in `AuditSummary.TAB_RESULTS`, its count in
  `GqlAuditSummary` and its column in `exportAudits.js`.
- **The Publish menu line never blocks and never carries page text** (2026-10-31, Stephane: a
  non-blocking item, jContent's publish actions untouched): it is added to `publishMenu`, never
  wraps jContent's actions; jContent writes action labels as HTML, so `buttonLabelParams` holds the
  module's own counts only.
- **A section that gains a kind of finding reports it to "Fix first"** (2026-10-31): through its
  `onFindings`, with an axe-core severity (`critical serious moderate minor`) and a translated title,
  and the tab that shows it (`drawer/findings.js`). Sections report every change of their results;
  the drawer ignores an identical list, so a section never needs to deduplicate.
- **Other modules add tabs and finding actions through jContent's registry** (#71): with none
  added, the drawer renders as before. `docs/drawer-entries.md` is the contract: what an entry
  declares, and the `path`, `language` and `findings` it receives (each finding with `rule`, its key
  until #70). A change to what an entry receives changes that page and `drawerEntries.test.js`. A
  new place that lists findings renders `FindingActions` with the finding's key.

## Configuration

- `org.jahia.community.contentgovernance.geo.cfg`: crawler fetch settings of the GEO section.
- `org.jahia.se.modules.georeadiness.botrank-<id>.cfg`: one per BotRank organisation (legacy PID).

The module ships the first one with `# default configuration` as first line, so a redeploy never
overwrites it. A server administrator creates the BotRank files, and the module never ships them.

## Verify

- `mvn clean install` runs 492+ Java unit tests (GEO, compliance, settings, editorial frame) and the
  front-end unit tests (`yarn test`: Vitest, `src/test/javascript/`: readability, findings, locales,
  the CSRF fetch; `@apollo/client`, a host library, is a stub in `stubs/`).
- **`yarn lint` before every push** (ESLint, `eslint.config.mjs`, about 3 s): the rules the SonarQube
  gate of the pull requests fails on (nested ternaries and template literals, `replaceAll`, `globalThis`,
  array index keys, `propTypes`, roles and handlers on non-interactive elements, cognitive complexity
  15, slow or redundant regular expressions), each with its Sonar key. The gate accepts no new issue, and it failed four pull requests
  before this check existed (#30, #31, #49, #63). The pull request CI runs it (`Static Analysis`). A rule
  is added when the gate reports one this file lacks; a rule is switched off on a line only with the
  reason beside it (`-- ...`). Keys of a list come from `common/keys.js` (`withKeys`), never the index.
- The Cypress suite in `tests/` (README, "Build") against the local Jahia: settings, compliance scan,
  CSRF guard, drawer, Page quality audit (axe-core, readability per language). Not run by the pull request
  CI; it runs nightly on `main` (`nightly.yml`, README "Build"), so check the last nightly before a release.
- Deploy, enable on a site, open a page: the drawer shows the switched-on sections; switch a tab off
  in Site Settings > Content governance and reopen the drawer; the GEO servlet answers 403 and the
  compliance query refuses once their section is off.

## CI and releases

Jahia's shared workflows in `.github/workflows/` (table in the root README, "CI and releases").
Every PR carries a `.chachalog/` fragment, docs and CI changes included; `.version` stays `0.0.0`
until 1.0.0 is released. Before releasing, read the README's release steps: the CI account may not
have write access to this new repository, and the first release PR must fold the hand-written
`1.0.0 (unreleased)` section of `CHANGELOG.md`. The Cypress suite in `tests/` runs locally against a
running Jahia (README, "Build") and nightly in GitHub Actions; it is kept out of the pull request CI,
which the team wants short.

## Known leftovers

- None known. (Up to 1.4.0, switching AI citations or the GEO section off turned the daily BotRank
  sync off for good; since 1.5.0 it only pauses it, `SyncStep.first`.)
