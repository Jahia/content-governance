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
| `src/javascript/drawer/` | The header action and the one drawer: header, section bar, sections mounted on first visit |
| `src/javascript/audit/` | Page quality section: preview frame, analyzers (`analyzers/`), tabs, AI assists |
| `src/javascript/geo/` | GEO readiness section (`GeoSection`), its tabs, the GEO dashboard, the BotRank screen |
| `src/javascript/compliance/` | Compliance section and the compliance dashboard |
| `src/javascript/settings/` | Site Settings > Content governance: Features, AI provider, BotRank |
| `src/javascript/common/` | i18n hook, settings query and hook, `SectionGate`, `AiUnavailable`, URL helper |
| `src/main/java/.../ai/` | The provider layer and `LlmConfigService`, shared by every AI feature |
| `src/main/java/.../settings/` | `Features` (section and tab ids) and `SiteFeatures` (the server-side gate) |
| `src/main/java/.../graphql/` | `contentGovernance { siteSettings }`, `saveSiteSettings`, `complianceScan` |
| `src/main/java/.../{audit,geo,compliance}/` | Each section's server side, as in the original modules |

## Invariants

- React **18** only (jContent singleton). Moonstone **2.20.3**, the version jContent 3.7.1 and 3.8
  run: no API newer than that. Badge colours are `accent`, `success`, `danger` only.
- Module id, bundle symbolic name and i18n namespace: `content-governance` (`common/module.js`,
  `common/i18n.js`, `SiteFeatures.MODULE`). Rename all or none.
- Translations: one namespace, keys prefixed per part (`common`, `drawer`, `settings`, `audit`,
  `geo`, `compliance`), read through `useGovernanceTranslation(prefix)`, never `useTranslation`
  directly. `en.json` and `fr.json` keep the same keys.
- Feature ids (`audit`, `audit.seo`, `geo.citations`, `compliance`...) are stored per site and are
  part of the GraphQL API: never rename one. A site stores the ids it switched **off**, so a tab added
  later starts on. Add a tab in `Features.SECTIONS` and in the section's tab list together.
- Every endpoint checks the feature on the server: GEO servlets in `SiteScope.require`, the AI
  servlet per task (`review` = `audit.ai`, `seo` = `audit.seo`, `alt` = `audit.accessibility`,
  `simplify` = `audit.readability`), compliance in `GqlComplianceScan.requireModuleEnabled`, BotRank
  in `CitationsAccess` (`geo.citations`). Client gates only hide.
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
  (exact `application/json`, same-site browser requests only); `AiReviewServlet` has its own checks.
- Every servlet is under Jahia's CSRF guard (`org.jahia.modules.jahiacsrfguard-content-governance.cfg`,
  no first-line marker on purpose). Every call from the UI goes through `common/guardedFetch.js`,
  never a bare `fetch`; the servlets take no API token (decision 2026-10-30, browser only).
- Build with Maven **3.8** and Java 17 (`mise.toml`), always `clean install`.
- `robots` and `llms` are optional dependencies (`=optional`): only the GEO robots.txt editor and the
  read-only llms.txt tab need them. llms.txt is written in the `llms` module, never here.

## Configuration

- `org.jahia.community.contentgovernance.llm.cfg`: the one model connection (provider, model, key,
  budgets, rate limits, cost, prompt appendix). Empty by default: every AI feature greyed out.
- `org.jahia.community.contentgovernance.geo.cfg`: crawler fetch settings of the GEO section.
- `org.jahia.se.modules.georeadiness.botrank-<id>.cfg`: one per BotRank organisation (legacy PID).

The module ships the first two with `# default configuration` as first line, so a redeploy never
overwrites them; BotRank files are created by a server administrator, never shipped.

## Verify

- `mvn clean install` runs 477+ unit tests (GEO, compliance, settings, LLM config).
- Deploy, enable on a site, open a page: the drawer shows the switched-on sections; switch a tab off
  in Site Settings > Content governance and reopen the drawer; the GEO servlet answers 403 and the
  compliance query refuses once their section is off.

## CI and releases

Jahia's shared workflows in `.github/workflows/` (table in the root README, "CI and releases").
Every PR carries a `.chachalog/` fragment, docs and CI changes included; `.version` stays `0.0.0`
until 1.0.0 is released. Before releasing, read the README's release steps: the CI account may not
have write access to this new repository, and the first release PR must fold the hand-written
`1.0.0 (unreleased)` section of `CHANGELOG.md`. No integration-test job until a Cypress suite exists.

## Known leftovers

- Readability scores only English (Flesch) and French (Kandel-Moles); other languages get Flesch.
- Once AI citations or the GEO section were switched off, the daily BotRank sync stays off when they
  come back: an admin turns it on again (the BotRank tab says so).
