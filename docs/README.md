# Documentation index

Read the part that matches why you are here. Install, configuration keys, permissions and the API
are in the root [`README.md`](../README.md); the history of this module and of the three it replaces
is in [`CHANGELOG.md`](../CHANGELOG.md).

## If you edit pages

Open a page, click **Content governance** in the header, then pick a section. Each guide says what
every tab shows, which findings are yours to fix and which belong to a developer or an administrator.

- **[`page-quality/guide.md`](page-quality/guide.md)**: the **Page quality** section, its eight tabs
  and the AI assists.
- **[`geo-readiness/drawer-guide.md`](geo-readiness/drawer-guide.md)**: the **GEO readiness**
  section, its five tabs and the banners above them.
- **[`compliance/page-scan-guide.md`](compliance/page-scan-guide.md)**: the **Compliance** section,
  how to read an issue and fix it.

## If you look after a whole site

- **[`geo-readiness/dashboard-guide.md`](geo-readiness/dashboard-guide.md)**: **Additional > SEO >
  GEO readiness**, its panels, the two that write to the site, scheduled scans and the written report.
- **[`compliance/dashboard-guide.md`](compliance/dashboard-guide.md)**: **Additional > Compliance**,
  its tables and the CSV and JSON exports.
- **[`compliance/glossary-guide.md`](compliance/glossary-guide.md)**: for the people who own the
  wording (Legal, compliance): writing glossaries and terms, how matching works.

## If you own the voice and the writing rules of the brand

- **[`editorial-frame/guide.md`](editorial-frame/guide.md)**: the editorial frame, which holds the
  voice, the personas, the writing rules, the translation glossary and the skills. The guide states
  where they live, who reads them, the import and the endpoints.

## If you are a site administrator

- **[`settings-guide.md`](settings-guide.md)**: **Site Settings > Content governance**: switching
  sections and tabs, the AI model, connecting BotRank.
- **[`content-rules/catalog.md`](content-rules/catalog.md)**: the rules the checks apply, how a
  site changes one or adds its own, and how a module ships rules.

## If you want to know what it checks, or how it works

| Section | What it checks | How it works |
|---|---|---|
| Page quality | [`page-quality/guide.md`](page-quality/guide.md) | [`page-quality/architecture.md`](page-quality/architecture.md) |
| The HTML check on the server, which AI agents run | [`page-quality/html-rules.md`](page-quality/html-rules.md) | [`page-quality/html-rules.md`](page-quality/html-rules.md) |
| GEO readiness | [`geo-readiness/checks.md`](geo-readiness/checks.md) | [`geo-readiness/architecture.md`](geo-readiness/architecture.md) |
| Compliance | [`compliance/glossary-guide.md`](compliance/glossary-guide.md) (matching rules) | [`compliance/architecture.md`](compliance/architecture.md) |
| Editorial frame (no section of the drawer yet) | [`editorial-frame/guide.md`](editorial-frame/guide.md) | [`editorial-frame/architecture.md`](editorial-frame/architecture.md) |
| Content rules (the id, severity and limits of each check) | [`content-rules/catalog.md`](content-rules/catalog.md) | [`content-rules/catalog.md`](content-rules/catalog.md#how-a-check-reads-the-rules) |

Agents read [`../.agents/README.md`](../.agents/README.md) first: invariants, the legacy storage
kept on purpose, traps already hit once, known leftovers.

## If you build a module that adds to the drawer

- **[`drawer-entries.md`](drawer-entries.md)**: how another module adds a tab to the drawer, or an
  action on a finding, and what the drawer gives to each entry: the page, the language and the
  findings.

## If you are deciding what to build next

- **[`geo-readiness/geo-backlog.md`](geo-readiness/geo-backlog.md)** and
  **[`geo-readiness/botrank-backlog.md`](geo-readiness/botrank-backlog.md)**: the GEO stories, built
  and planned, carried over from GEO Readiness with the reasoning behind each refusal.
- **[`compliance/publication-blocking.md`](compliance/publication-blocking.md)**: why compliance does
  not block publication yet, and the design when it is wanted.
