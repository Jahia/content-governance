# Content Governance

One jContent drawer that helps authors check a page before they publish it, and two site
dashboards. A site administrator chooses which sections and which tabs each site uses.

This module replaces three: **GEO Readiness**, **Page Quality Audit** and **Content Compliance
Scan**. It reads the data they left (GEO scans, BotRank connections, compliance glossaries), so a site
keeps its history when it moves.

## What authors get

A **Content governance** button in the header of jContent and Page Builder opens a drawer on the
right, under the header: the page under edit (or the content list) narrows beside it and stays
usable, and the drawer's left edge drags to make it wider. It has one tab per section, each with a count of the issues to review, red only when one of them is critical or serious. Inside
Page quality and GEO readiness, each check is a collapsible section with its count left of its title:
a section with a critical or serious finding opens on its own, the others stay closed. Every tab and
every section has a tooltip saying what it is for, and why it is greyed out when it cannot be used.
Under the tabs, **Fix first** lists up to five critical or serious findings, whatever their tab, each
opening the section that explains it; it stays out of the way when there is none. A server
administrator shows it in `org.jahia.community.contentgovernance.drawer.cfg`: it is hidden by default.

| Section | What it answers | Where it runs |
|---|---|---|
| **Page quality** | Is the page I am editing accessible, findable, fast, readable, light, well linked and complete in Jahia? | On the server for SEO, readability, links and Jahia health, as `frame.check` checks it; in the editor's browser for accessibility, load and eco-design; both on the editing version of the page |
| **GEO readiness** | Can AI assistants and their crawlers read and cite the published page? | On the server, on demand (one fetch per AI crawler) |
| **Compliance** | Does the page, or content it shows, use a term that a term rule of the site forbids or replaces in this language? | On the server, when the section opens |

### Page quality

| Tab | What it checks |
|---|---|
| **Accessibility** | Automated WCAG A, AA and AAA rules with axe-core, issues by severity with highlighting, what axe cannot decide, and a manual checklist |
| **SEO** | Title, meta description, indexing, canonical address, social sharing card, structured data, image alt coverage and link texts |
| **Web Vitals** | Load timings, layout shift, an estimated LCP, page weight, requests, DOM size and the heaviest resources, measured in the browser |
| **Readability** | A score with the formula of the page language (English, French, German, Spanish, Italian, Dutch, Portuguese; LIX for Swedish, Danish, Norwegian), long sentences and heading structure |
| **Ecodesign** | Page-level checks from the French RGESN 2024 referential, plus a checklist for what a page tool cannot judge (not a conformity score) |
| **Links** | Internal links that a visitor cannot open, absolute addresses to the site's own host, `http://` links, unsafe `target="_blank"`; external links are counted, not verified |
| **Jahia** | Unpublished content on the page, missing translations in active languages, raw resource keys and placeholder text |

The module calls no model. The AI review of a page, its SEO texts, alt texts and plain-language
rewrites come from the module ai-assistant, which adds its own tab to the drawer when it is installed.

### GEO readiness

| Tab | What it checks |
|---|---|
| **Score** | Eighteen checks in three groups, each showing the fact it read; a count of checks, not a rating |
| **Crawler access** | Whether each AI crawler gets the page, and what blocks it (robots.txt, status, redirects, a server that answers crawlers differently) |
| **Site files** | What `robots.txt` and `llms.txt` say to AI crawlers |
| **Structured data** | The JSON-LD that says what the page is about, and what is missing |
| **AI citations** | How often AI answers cite the page and for which questions, from BotRank |

### Compliance

Terms are content rules of the check `pattern`. Each team keeps its terms in a folder of rules
under the site's contents, with the permissions of that folder: the legal team keeps terms that the
brand team cannot change. The scan reads the page and every piece of content it shows, and lists
each term found with its rule id, its folder, its advice and its replacement. The tool `frame.check`
reports the same rules to an agent. [`docs/compliance/glossary-guide.md`](docs/compliance/glossary-guide.md)
states how to write them.

### Site dashboards

They stay in jContent's **Additional** accordion:

- **SEO > GEO readiness**: site score, reach, invisible content, sitemap against reality, internal
  links, addresses, freshness, languages, structured data, the `robots.txt` editor, the published
  `llms.txt` (read only, written in the `llms` module's own **Llms.txt** screen), AI citations, and
  a written report that another module, such as ai-assistant, writes with a model. Scans can be
  scheduled; **Export CSV** and **Export JSON** download what the last scan found.
- **Page quality**: the latest audit of every audited page, as editors ran it from the drawer, most
  issues first, with its trend, **Export CSV** and **Export JSON**. Expand a page to read its
  findings, the most serious first, with what to do about each. Audits are shared: every editor
  of the site sees a page's newest audit and its last 10 in the drawer.
- **Compliance**: the whole-site scan, with **Export CSV** and **Export JSON**.

A section switched off for a site switches its dashboard off too: the entry stays in **Additional**
and says so.

### Before publishing

jContent's **Publish** menu of the page in view starts with "Review before publishing - audit issues:
12, serious accessibility: 2, forbidden terms: 1" (or "Not audited yet") when there is something to
review, opening the drawer. It never blocks publishing; jContent's own publish actions are untouched.

## What site administrators get

- **jContent > Additional > Rules & domains** (`site-admin`): order the domains, the subdomains and
  the rules of the drawer, move a rule to another subdomain, and switch the panels, the domains, the
  subdomains and the rules on or off. Everything follows the defaults until a save. A domain off hides
  it from the drawer and switches its dashboard off (the entry stays and says so). The server refuses
  the calls of what is switched off. A rule that a module ships after the last save shows the mark
  **New since your last save**.
- **Administration > Sites > (site) > Content governance** (`site-admin`): connect the site to a
  BotRank project, map cited addresses to pages, and set the time of the daily sync.

Server administrators get the same screen for the defaults of every site in **Administration > Server
> Configuration > Content governance rules**.

## The editorial frame

The module also holds the editorial frame of a brand: its writing rules and its translation
glossary. They are content of the site, which a brand manager edits in jContent and publishes. The
AI features of Jahia read them through an OSGi service, and AI agents check a text against them with
the tool `frame.check` when the `mcp-server` module is installed. The module ai-assistant holds the
rest of what its assistant writes with.

Until version 1.12, a separate module named editorial-frame held this code. Its content types
(`efnt:*`, `efmix:*`), its Java packages and its tool name keep their names.
[`docs/editorial-frame/guide.md`](docs/editorial-frame/guide.md) describes the frame, and
[`docs/editorial-frame/architecture.md`](docs/editorial-frame/architecture.md) how it sits in the
module.

## Languages

The drawer, the dashboards and Site Settings follow the editor's interface language: English, French,
German and Spanish (other languages show English). jContent itself has no Spanish interface, so a
Spanish editor sees jContent in English and this module in Spanish. The German and Spanish texts are
machine-drafted and await a native review. Content types and the example glossary created on enable
also come in the four languages.

## Requirements

- Jahia 8.2.2.0 or later (tested on 8.2.3.2) with jContent 3.7 or later (`jcontent=[3.7,4)`; UI tested on 3.7.1,
  API also on 3.8.0) and graphql-dxm-provider. React 18 and Moonstone 2.20 come from jContent.
- Optional: the `robots` module, for the GEO `robots.txt` editor, and the `llms` module, which writes
  and serves `llms.txt` (Content Governance shows it read only and checks its links). Without them
  the rest of the module works and those tabs say why they cannot.
- Optional: the `mcp-server` module, 0.5.0 or a later 0.x version, which serves the tool `frame.check`
  of the editorial frame and the two tools of the HTML check to AI agents. Without it, or with an older
  version, the module starts and adds no tool.
- Optional: the module ai-assistant, which adds the AI features: the AI review tab of the drawer
  and the writer of the GEO report
  ([`docs/geo-readiness/architecture.md`](docs/geo-readiness/architecture.md#the-geo-report-writer)).
  Without it the drawer has no AI tab, and the GEO dashboard has no **Report** tab.
- Optional: a BotRank organisation API key, for AI citations.

## Install

1. If GEO Readiness, Page Quality Audit or Content Compliance Scan are installed, uninstall them
   first: this module reads their data and declares the same glossary types. Uninstall the module
   editorial-frame too: this module declares the same content types and exports the same Java
   package ([Moving from editorial-frame](#moving-from-editorial-frame)). On a Jahia that ran a
   1.12 snapshot of this module that held the whole frame, install the ai-assistant that holds the
   content types this version no longer declares first: it takes them over, and their content
   stays.
2. **Administration > Server > Modules and Extensions > Modules**, **Upload module from file**:
   `content-governance-<version>.jar`. Then open **Content Governance** in the list and, under
   **Usage in sites**, enable it on each site.
3. Open **Administration > Sites > (site) > Content governance** to choose the sections and tabs.
4. For compliance, enabling the module on a site creates **Content Folders > Compliance glossaries**
   with a folder for the site's default language and one example rule. Create a folder of rules for
   each team there, or anywhere under the site's `contents`, and give each folder the roles of its
   team.

### Moving from the three modules

What carries over with nothing to do: GEO scans and reports (`/sites/<key>/geo-readiness`), BotRank
connection files (their old name is kept), compliance glossaries (`ccs:` types); the results cached in
each editor's browser are simply recomputed.

What a site administrator saves again once: the GEO scan schedule and the BotRank daily sync. The
first time the module starts on a site's existing GEO data, it keeps their settings but switches both
off, with a note on the dashboard and in **Site Settings > Content governance > BotRank**; saving them
from there turns them back on.

What does not carry over: the AI settings. Page Quality Audit and GEO Readiness each had their own.
This module calls no model: the AI features come from ai-assistant, which has its own model
settings. Delete the old files (`org.jahia.se.modules.pageaudit.cfg` and the `AI_*` keys of
`org.jahia.se.modules.georeadiness.cfg`).

**Glossary types on an instance that had Content Compliance Scan.** Jahia keeps the glossary types
registered to the uninstalled module. From 1.1.0, Content Governance takes them over when it starts
(same definitions, so existing glossaries stay valid; the log says "Compliance glossary types taken
over"). With 1.0.0 they stayed with the old module: **New content** did not offer **Compliance
glossary** and **Non-authorised term**, and their names showed untranslated; installing 1.1.0 fixes
it. Fresh installs were never affected.

### Moving from editorial-frame

The module editorial-frame was a prototype with no release, so this module ships no migration.

1. In **Administration > Server > Modules and Extensions > Modules**, stop editorial-frame, then
   uninstall each of its versions. With the provisioning API, the operation is
   `uninstallBundle: "org.jahia.pm.modules/editorial-frame/<version>"`.
2. Install this module.

The content of the frame stays valid: the rules and the glossary terms keep their types. Jahia keeps the content types of an uninstalled module registered to
that module, and it ignores the same types when another module declares them. On a Jahia that ran
editorial-frame, **New content** does not offer the types of the frame and shows their names
untranslated, until an administrator removes the definitions that editorial-frame left.

## Configuration

Files in `digital-factory-data/karaf/etc/`. Edits apply at once with no redeploy, and a redeploy
never overwrites them (`# default configuration` first line).

### Upgrade from 1.x: the AI features moved to ai-assistant

The module no longer reads `org.jahia.community.contentgovernance.llm.cfg`. On an existing install
the file stays in `digital-factory-data/karaf/etc/`, since its first line `# default configuration`
keeps it. Copy its `AI_PROMPT_APPENDIX` value into `review.instructions` of
`org.jahia.modules.aiassistant.cfg`, then delete the file. Install ai-assistant for the AI features.

### `org.jahia.community.contentgovernance.geo.cfg`: the GEO crawler check

| Key | Default | What |
|---|---|---|
| `FETCH_TIMEOUT_MS` | `8000` | Wait for each crawler fetch (sixteen agents, three at a time) |
| `MAX_BODY_BYTES` | `1500000` | Bytes read from a page |
| `RATE_MAX_CALLS`, `RATE_WINDOW_MS` | `20`, `600000` | Checks allowed per user per window |
| `CRAWLER_AGENTS` | empty (built-in list) | `name\|user-agent` pairs, comma separated |
| `PUBLIC_BASE_URL` | empty | Force the public base URL when the site's server name does not resolve from the server |

### `org.jahia.community.contentgovernance.drawer.cfg`: what the drawer shows

The same for every site of the server. Editors see a change the next time they open the drawer.

| Key | Default | What |
|---|---|---|
| `SHOW_FIX_FIRST` | `false` | Shows **Fix first** under the tab bar of the drawer |
| `SHOW_PREVIEW_BUTTON` | `false` | Shows the **Show preview** button of Page quality. Hidden, the preview keeps its own behaviour: hidden beside Page Builder, shown elsewhere, opened to show an item located in it |

For example, `SHOW_FIX_FIRST=true` gives every editor the list of the critical findings of the page.

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
| jContent > Additional > Rules & domains, Site Settings > Content governance | `site-admin` on the site | always; the server checks every save |
| Server settings > Content governance rules | `admin` on the root node | always; the server checks every save |

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
- **AI features**: the module sends nothing to a model. For the GEO report, it gives a digest of
  site measurements to the writer that another module registers. That writer may send the digest to
  the provider of its model. The module validates the answer before it reaches the browser.
- **BotRank** is called by the daily sync only, with the organisation's key.
- **The link check for AI agents** (`audit.links`) sends a `HEAD` request to each external link of
  the page that an agent checks, and never to a private or local address. It runs only when an agent
  calls the tool.

## API

```graphql
query {
  contentGovernance {
    siteSettings(siteKey: "mysite") {
      moduleEnabled canEdit disabled
    }
    drawerDisplay { fixFirst previewButton }
  }
}

mutation {
  contentGovernance {
    saveSiteLayout(siteKey: "mysite", layout: "{\"domains\": [], \"off\": [\"geo.citations\", \"vitals\"]}") { saved off }
  }
}
```

`disabled` lists the ids that are off on the site: the panels (`governance`, `chat`), the domains
(`quality`, `geo`, `compliance`), the subdomains (`geo.citations`, `vitals`) and the tabs that other
modules add. `saveSiteLayout` saves the layout of the site, and `resetSiteLayout` removes it:
[`docs/content-rules/catalog.md`](docs/content-rules/catalog.md#the-layout-of-a-site) states the JSON.
Everything is on until the site switches it off. The
compliance scans stay under `complianceScan { scanPage, scanSite }`. Servlets live under
`/modules/content-governance/` (`geo/crawler-check`, `geo/report`, `geo/site-scan`, `geo/site-files`,
`geo/citations`). The Java API of the editorial frame is the OSGi service
`org.jahia.pm.modules.editorialframe.api.EditorialFrameService`. The HTML check on the server is the
OSGi service `org.jahia.community.contentgovernance.html.HtmlCheck`, and
[`docs/page-quality/html-rules.md`](docs/page-quality/html-rules.md) lists its rules.

Another module adds a panel to the drawer, a tab to the governance panel or an action on a finding,
through jContent's registry, and opens a panel with `openPanel`:
[`docs/drawer-entries.md`](docs/drawer-entries.md) states what an entry declares and receives.

`GET geo/report` answers `{enabled, reason, provider, model}`, and `reason` is `absent` while no
module registers a writer of the GEO report
([`docs/geo-readiness/architecture.md`](docs/geo-readiness/architecture.md#the-geo-report-writer)).

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
section a user guide, a reference of what it checks and an architecture page. The editorial frame
has a guide and an architecture page too. The history of this
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

`mvn clean install` runs the unit tests: the Java ones and the front-end ones (`yarn test`, Vitest, in
`src/test/javascript/`); `-DskipTests` skips both. `test-fixtures/` holds a local site and a BotRank stub
for the GEO checks.

`yarn lint` (ESLint, `eslint.config.mjs`) checks the front-end code against the rules the SonarQube
gate of the pull requests fails on; run it before a push. The pull request CI runs it too.

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
