# Content Governance

One jContent drawer that helps authors check a page before they publish it, and two site
dashboards. A site administrator chooses which sections and which tabs each site uses.

This module replaces three: **GEO Readiness**, **Page Quality Audit** and **Content Compliance
Scan**. It reads the data they left (GEO scans, BotRank connections, compliance glossaries), so a site
keeps its history when it moves.

## What authors get

A **Content governance** button in the header of jContent and Page Builder opens a drawer on the
right. It has one tab per section, each with a red count of the issues to review. Every section and
every tab has a tooltip saying what it is for, and why it is greyed out when it cannot be used.
Under the section tabs, **Fix first** lists the five most serious findings, whatever their section,
each opening the tab that explains it.

| Section | What it answers | Where it runs |
|---|---|---|
| **Page quality** | Is the page I am editing accessible, findable, fast, readable, light, well linked and complete in Jahia? | In the editor's browser, on a preview of the page in the editing workspace; AI on demand |
| **GEO readiness** | Can AI assistants and their crawlers read and cite the published page? | On the server, on demand (one fetch per AI crawler) |
| **Compliance** | Does the page, or content it shows, use a term the site's glossaries forbid in this language? | On the server, when the section opens |

### Page quality

| Tab | What it checks |
|---|---|
| **Accessibility** | Automated WCAG A, AA and AAA rules with axe-core, issues by severity with highlighting, what axe cannot decide, and a manual checklist; alt text suggested by AI for images without one |
| **SEO** | Title, meta description, indexing, canonical address, social sharing card, structured data, image alt coverage and link texts; titles, descriptions, keywords, headings and call-to-action labels suggested by AI |
| **Web Vitals** | Load timings, layout shift, an estimated LCP, page weight, requests, DOM size and the heaviest resources, measured in the browser |
| **Readability** | A score with the formula of the page language (English, French, German, Spanish, Italian, Dutch, Portuguese; LIX for Swedish, Danish, Norwegian), long sentences and heading structure; plainer rewrites suggested by AI |
| **Ecodesign** | Page-level checks from the French RGESN 2024 referential, plus a checklist for what a page tool cannot judge (not a conformity score) |
| **Links** | Broken internal links, absolute addresses to the site's own host, mixed content, unsafe `target="_blank"`; external links are counted, not verified |
| **Jahia** | Unpublished content on the page, missing translations in active languages, raw resource keys and placeholder text |
| **AI review** | A review of the page text by the configured model: each finding with its severity and the exact wording to change |

AI suggestions are shown and copied, never written to the repository. They are written in the
language of the page.

### GEO readiness

| Tab | What it checks |
|---|---|
| **Score** | Eighteen checks in three groups, each showing the fact it read; a count of checks, not a rating |
| **Crawler access** | Whether each AI crawler gets the page, and what blocks it (robots.txt, status, redirects, a server that answers crawlers differently) |
| **Site files** | What `robots.txt` and `llms.txt` say to AI crawlers |
| **Structured data** | The JSON-LD that says what the page is about, and what is missing |
| **AI citations** | How often AI answers cite the page and for which questions, from BotRank |

### Compliance

Terms come from **Compliance glossaries**, one per language, kept in the site's contents by the
people who own the wording (Legal, compliance). The scan reads the page and every piece of content
it shows, and lists each term found with its reason and the suggested wording.

### Site dashboards

They stay in jContent's **Additional** accordion:

- **SEO > GEO readiness**: site score, reach, invisible content, sitemap against reality, internal
  links, addresses, freshness, languages, structured data, the `robots.txt` editor, the published
  `llms.txt` (read only, written in the `llms` module's own **Llms.txt** screen), AI citations, and
  a written report by AI. Scans can be scheduled; **Export CSV** and **Export JSON** download what
  the last scan found.
- **Page quality**: the latest audit of every audited page, as editors ran it from the drawer, most
  issues first, with its trend, **Export CSV** and **Export JSON**. Audits are shared: every editor
  of the site sees a page's newest audit and its last 10 in the drawer.
- **Compliance**: the whole-site scan, with **Export CSV** and **Export JSON**.

A section switched off for a site switches its dashboard off too: the entry stays in **Additional**
and says so.

### Before publishing

jContent's **Publish** menu of the page in view starts with "Review before publishing - audit issues:
12, serious accessibility: 2, forbidden terms: 1" (or "Not audited yet") when there is something to
review, opening the drawer. It never blocks publishing; jContent's own publish actions are untouched.

## What site administrators get

**Administration > Sites > (site) > Content governance** (`site-admin`), in three tabs:

- **Features**: one switch per section and one per tab, everything on by default. A section off hides
  its drawer section and switches its dashboard off (the entry stays and says so); a tab off
  disappears from the drawer. The server refuses the calls of what is switched off. Each tab's switch
  says what the tab is for.
- **AI provider**: whether the shared model is configured, which provider and model, and whether it
  reads images. Read only: only a server administrator sets it.
- **BotRank**: connect the site to a BotRank project, map cited addresses to pages, and set the time
  of the daily sync.

## Languages

The drawer, the dashboards and Site Settings follow the editor's interface language: English, French,
German and Spanish (other languages show English). jContent itself has no Spanish interface, so a
Spanish editor sees jContent in English and this module in Spanish. The German and Spanish texts are
machine-drafted and await a native review. Content types and the example glossary created on enable
also come in the four languages.

## Requirements

- Jahia 8.2 (tested on 8.2.3.2) with jContent 3.7 or later (`jcontent=[3.7,4)`; UI tested on 3.7.1,
  API also on 3.8.0) and graphql-dxm-provider. React 18 and Moonstone 2.20 come from jContent.
- Optional: the `robots` module, for the GEO `robots.txt` editor, and the `llms` module, which writes
  and serves `llms.txt` (Content Governance shows it read only and checks its links). Without them
  the rest of the module works and those tabs say why they cannot.
- Optional: an Anthropic, OpenAI or DeepSeek API key, for the AI features.
- Optional: a BotRank organisation API key, for AI citations.

## Install

1. If GEO Readiness, Page Quality Audit or Content Compliance Scan are installed, uninstall them
   first: this module reads their data and declares the same glossary types.
2. **Administration > Server > Modules and Extensions > Modules**, **Upload module from file**:
   `content-governance-<version>.jar`. Then open **Content Governance** in the list and, under
   **Usage in sites**, enable it on each site.
3. Open **Administration > Sites > (site) > Content governance** to choose the sections and tabs.
4. For the AI features, a server administrator fills in the shared model (below).
5. For compliance, enabling the module on a site creates **Content Folders > Compliance glossaries**
   with an example glossary in the site's default language and one example term. Add a
   **Compliance glossary** per language there, or anywhere under the site's `contents`.

### Moving from the three modules

What carries over with nothing to do: GEO scans and reports (`/sites/<key>/geo-readiness`), BotRank
connection files (their old name is kept), compliance glossaries (`ccs:` types); the results cached in
each editor's browser are simply recomputed.

What a site administrator saves again once: the GEO scan schedule and the BotRank daily sync. The
first time the module starts on a site's existing GEO data, it keeps their settings but switches both
off, with a note on the dashboard and in **Site Settings > Content governance > BotRank**; saving them
from there turns them back on.

What does not carry over: the AI settings. Page Quality Audit and GEO Readiness each had their own; copy the
provider, model and key into `org.jahia.community.contentgovernance.llm.cfg`, then delete the old
files (`org.jahia.se.modules.pageaudit.cfg` and the `AI_*` keys of
`org.jahia.se.modules.georeadiness.cfg`).

**Glossary types on an instance that had Content Compliance Scan.** Jahia keeps the glossary types
registered to the uninstalled module. From 1.1.0, Content Governance takes them over when it starts
(same definitions, so existing glossaries stay valid; the log says "Compliance glossary types taken
over"). With 1.0.0 they stayed with the old module: **New content** did not offer **Compliance
glossary** and **Non-authorised term**, and their names showed untranslated; installing 1.1.0 fixes
it. Fresh installs were never affected.

## Configuration

Files in `digital-factory-data/karaf/etc/`. Edits apply at once with no redeploy, and a redeploy
never overwrites them (`# default configuration` first line).

### `org.jahia.community.contentgovernance.llm.cfg`: the shared AI model

Used by the **AI review** tab, the SEO, alt text and plain-language assists, and the written report
of the GEO dashboard. Until provider, model and key are all set, these features are greyed out and
nothing leaves the server.

| Key | Default | What |
|---|---|---|
| `AI_PROVIDER` | empty | `anthropic`, `openai` or `deepseek` |
| `AI_MODEL` | empty | The provider's model id |
| `AI_API_KEY` | empty | Used on the server only, never sent to the browser or logged |
| `AI_BASE_URL` | empty | The provider's API base, for a proxy or a compatible gateway |
| `AI_MAX_TOKENS` | `6000` | Output budget of a page review or assist; a full review needs 3000 to 4000, and a reasoning model spends part of it thinking first |
| `AI_REPORT_MAX_TOKENS` | `6000` | Output budget of a GEO site report |
| `AI_RATE_MAX_CALLS` | `30` | Page calls allowed per user per ten minutes |
| `AI_REPORT_RATE_MAX_CALLS` | `5` | GEO reports allowed per user per ten minutes |
| `AI_COST_INPUT_PER_MTOKENS`, `AI_COST_OUTPUT_PER_MTOKENS` | empty | Price in USD per million tokens; when set, editors see the cost of each review |
| `AI_PROMPT_APPENDIX` | empty | Instructions added to every prompt: brand, tone, editorial rules |

Anthropic and OpenAI read the pictures of the alt text assist; DeepSeek is text only, and the assist
then infers from file names and surrounding text, and says so.

Any chat model works. The page tasks need a JSON answer: they ask OpenAI-compatible APIs for JSON
mode (`response_format`), and OpenAI's reasoning models (o1, o3, o4, gpt-5) for short reasoning
(`reasoning_effort: low`); a model or gateway that refuses either is asked again without it. An
answer that is still not JSON gets one retry with a correction, and its tokens count in the cost. A
reasoning model that spends the whole budget before answering is reported as such: raise
`AI_MAX_TOKENS` (or `AI_REPORT_MAX_TOKENS` for the GEO report).

### `org.jahia.community.contentgovernance.geo.cfg`: the GEO crawler check

| Key | Default | What |
|---|---|---|
| `FETCH_TIMEOUT_MS` | `8000` | Wait for each crawler fetch (sixteen agents, three at a time) |
| `MAX_BODY_BYTES` | `1500000` | Bytes read from a page |
| `RATE_MAX_CALLS`, `RATE_WINDOW_MS` | `20`, `600000` | Checks allowed per user per window |
| `CRAWLER_AGENTS` | empty (built-in list) | `name\|user-agent` pairs, comma separated |
| `PUBLIC_BASE_URL` | empty | Force the public base URL when the site's server name does not resolve from the server |

### `org.jahia.se.modules.georeadiness.botrank-<id>.cfg`: one per BotRank organisation

The name GEO Readiness used, so existing files keep working. No file ships with the module: a file
is a credential.

```properties
label = Acme (BotRank)
apiKey = brk_...
# Optional: the site keys allowed to use this connection. Blank allows every site.
sites = acme-fr,acme-com
# Optional: https://api.botrank.ai/public/v1 by default.
baseUrl =
```

The key is created by an owner of the BotRank organisation (**Organisation > Integrations > API
keys**). A site administrator then picks the connection in the **BotRank** tab. Editors never cause a
call to BotRank: a daily sync, on the processing server, stores the citations of the site's pages.

## Who sees what

| Surface | Permission | Shown when |
|---|---|---|
| Header button and drawer | read the page | the site enables the module and at least one section is on |
| Page quality, GEO readiness sections | read the page | the section is on |
| Compliance section | `jcr:write` on a page | the section is on; greyed out on content that is not a page, or without write access |
| GEO readiness dashboard and its endpoints | `publish` on the site | always listed; says it is switched off when the GEO section is off |
| Publish menu line | read the page; `jcr:write` for the forbidden terms | the page is in view, Page quality or Compliance is on, and something was found |
| Page quality dashboard and the shared audits | `jContentAccess` on the site, read on the page | always listed; says it is switched off when the Page quality section is off |
| Compliance dashboard | `jContentAccess` on the site | always listed; says it is switched off when the Compliance section is off |
| Site Settings > Content governance | `site-admin` on the site | always; the server checks every save |

Switches in the browser only hide. Every servlet and GraphQL field checks the permission, the module
on the site and the site's switches on the server. The page checks and the compliance scans read with
the caller's own rights and change no content; the only content write is the `robots.txt` editor
(diff, then a confirming click). The module keeps its own state (site settings, GEO
scans, schedules and reports, BotRank binding) in nodes under each site that only the module reads and
writes, after its own permission checks; GEO site scans and the BotRank sync run on the server and
store their results there.

## What leaves the server

- **Nothing by default.** Page quality runs in the editor's browser against the editing workspace.
- **GEO readiness** fetches the site's own public pages and files, as each AI crawler would.
- **AI features**, once configured, send to the configured provider: the page text, its metadata and
  a digest of the automated findings (page tasks); a digest of site measurements (GEO report);
  pictures only for the alt text assist with a provider that reads images. Each call is started by
  an editor; the prompts are fixed on the server and the answers are validated before they reach the
  browser.
- **BotRank** is called by the daily sync only, with the organisation's key.

## API

```graphql
query {
  contentGovernance {
    siteSettings(siteKey: "mysite") {
      moduleEnabled canEdit disabled
      sections { id enabled tabs { id enabled } }
      ai { configured provider model images }
    }
  }
}

mutation {
  contentGovernance {
    saveSiteSettings(siteKey: "mysite", disabled: ["geo.citations", "audit.vitals"]) { disabled }
  }
}
```

Feature ids: `audit` (`accessibility`, `seo`, `vitals`, `readability`, `ecodesign`, `links`,
`jahia`, `ai`), `geo` (`score`, `crawler`, `files`, `schema`, `citations`), `compliance`. The
compliance scans stay under `complianceScan { scanPage, scanSite }`. Servlets live under
`/modules/content-governance/` (`geo/crawler-check`, `geo/report`, `geo/site-scan`, `geo/site-files`,
`geo/citations`, `audit/ai-review`).

## Known gaps

The main ones; each section's architecture page has the full list.

- Automated accessibility rules cover a part of WCAG only; the drawer never claims conformity.
- Web Vitals are lab measurements in an iframe: LCP is estimated and INP needs an interaction.
- A Jahia Cloud node cannot always fetch its own public URL, which stops the GEO fetches there; the
  checks name the error instead of reporting a missing file.
- The GEO site scan reads one language per run and at most 2000 pages; a larger or multilingual
  site needs schedules.
- AI answers can be wrong and cost money per call: they are never generated on their own.
- The German and Spanish interface texts have not had a native review yet.

## Documentation

[`docs/README.md`](docs/README.md) indexes the guides: one for site administrators, and for each
section a user guide, a reference of what it checks and an architecture page. The history of this
module and of the three it replaces is in [`CHANGELOG.md`](CHANGELOG.md). Agents start with
[`.agents/README.md`](.agents/README.md).

## Build

Java 17 and Maven 3.8 (`mise.toml`; under Maven 3.9 the Jahia plugin writes a wrong
`Import-Package`). Node 22 and Yarn are installed by the build.

```bash
mvn clean install
```

The jar must hold `javascript/apps/remoteEntry.js`. Deploy `target/content-governance-<version>.jar`
with the module manager or the provisioning API:

```bash
curl -u root:<password> http://localhost:8080/modules/api/provisioning \
  -F 'script=[{"installOrUpgradeBundle":"content-governance-<version>.jar","autoStart":true,"uninstallPreviousVersion":true}];type=application/json' \
  -F file=@target/content-governance-<version>.jar
```

`mvn clean install` runs the unit tests: 497 in Java and the front-end ones (`yarn test`, Vitest, in
`src/test/javascript/`); `-DskipTests` skips both. `test-fixtures/` holds a local site and a BotRank stub
for the GEO checks.

The Cypress suite in `tests/` runs against a Jahia with the module deployed: the site settings (who reads
and who changes them, unknown ids dropped, a site without the module), the compliance scan (the example
glossary, a page scan, a site scan, the refusals, a section switched off), the CSRF guard on the servlets
(basic auth and a session without the token refused, a session with the token answered, `publish`
required), the drawer in jContent (a tab per section kept on, no button once every section is off),
the GEO dashboard asking the guard for its token once per page, and the Page quality audit (the
bundled axe-core is the declared version, the rules axe-core turns on reach the Accessibility tab, and
the Readability tab scores English and German with their own formula, leaves Russian unscored and
counts characters in Japanese, and a page whose requests never answer is still audited, with the
check that did not finish named), the shared audits (stored with counts computed on the server, read
by a colleague, the last 10 kept, refused outside the site or with the section off, listed in the
dashboard), and how much of the module jContent loads when it starts.

```bash
cd tests && yarn install
JAHIA_URL=http://localhost:8080 SUPER_USER_PASSWORD=<password> yarn e2e:ci
```

Fixture sites are built from `dx-base-demo-templates`; on an instance without it set
`CYPRESS_CG_TEMPLATE_SET` to an installed template set (for example `classic-templates`). The suite
creates and deletes its own sites (`cg-*`) and users, and signs out after each back-office login.

It also runs every night in GitHub Actions (`.github/workflows/nightly.yml`, or by hand with **Run
workflow**): the module built from `main` on `jahia/jahia-ee:8.2.3.2`, with jContent 3.8.0, engine 1.2.0
and classic-templates 0.8.0 from `tests/provisioning-manifest-build.yml`, through Jahia's shared
integration-tests workflow (`tests/ci.build.sh`, `ci.startup.sh`, `docker-compose.yml`). It stays out of
the pull request CI. Screenshots of failures are in the run's artifact.

## CI and releases

GitHub Actions run Jahia's shared [jahia-modules-action](https://github.com/Jahia/jahia-modules-action)
workflows:

| Workflow | When | What |
|---|---|---|
| `on-code-change.yml` | every pull request | module signature, static analysis (dependency vulnerabilities), build with unit tests, SonarQube |
| `on-merge.yml` | push to `main` | build, SBOM to Dependency-Track, SNAPSHOT to Nexus |
| `schedule-sonar.yml` | Monday, Wednesday, Friday | full SonarQube analysis of `main` with OWASP Dependency-Check |
| `chachalog-*.yml` | pull requests, push to `main` | checks the changelog fragment, opens the release pull request |
| `on-release.yml` | a GitHub prerelease | Maven release, signature, Nexus promotion |

The Cypress suite needs a running Jahia and runs locally (above), not on every pull request. Dependabot keeps axe-core and
the other dependencies current, and holds back the majors that would break jContent (React 18, the
Module Federation singletons, css-loader 7, the Jahia parent; reasons in `.github/dependabot.yml`).

Every pull request carries a changelog fragment in `.chachalog/` (`.github/changelog-fragments.md`).
To release:

1. Merge the **chore: release content-governance @ vX.Y.Z** pull request the bot opens. The first
   one, 1.0.0, also folds the hand-written `1.0.0 (unreleased)` section of `CHANGELOG.md` into the
   section it adds.
2. Create a prerelease on `main` tagged in Jahia's form, `X_Y_Z` (`1_0_0`), titled
   `content-governance vX.Y.Z`, with the `CHANGELOG.md` section as notes.
3. `on-release.yml` runs the release. It pushes as the org CI account, which needs write access to
   this repository (a repository admin grants it). Without it the release fails at its first push and
   is done by hand (AIStartupKit `jahia-release-and-ci.md`, "Manual release when the release workflow
   cannot run").

## Licence

MIT
