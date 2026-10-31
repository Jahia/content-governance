# How the GEO readiness section works

A map of the GEO readiness section of Content Governance for somebody about to change it. The
module-wide invariants (feature ids, the server-side switches, legacy storage, build) are in
[`../../.agents/README.md`](../../.agents/README.md). The GEO traps written while the section was
the GEO Readiness module (robots.txt parsing rules, the report-versus-write line, bugs already made
once) stay in that repository's `.agents/README.md`
([Jahia/geo-readiness](https://github.com/Jahia/geo-readiness)); read them before changing the
checks. This file is the shape.

## The one decision everything else follows from

**A crawler is not a browser, and the editor's browser is the worst possible instrument for asking
what a crawler sees.** It is authenticated, it runs JavaScript, and no firewall ever challenges it.

So every question is answered server-side: fetch the published URL with no cookies and no session,
send the crawler's own user agent, do not follow redirects, and read the **initial HTML** rather
than a rendered DOM. The Page quality section of the same drawer (formerly page-audit) audits what
an editor sees, on a preview in the browser. This section audits what a crawler receives. Different
question, different mechanism, and the two must not be merged, even though they now share a drawer.

## Two surfaces, two fetch strategies

|  | Drawer section | Site scan |
|---|---|---|
| Where | **Content governance** header button, **GEO readiness** tab | jContent, **Additional > SEO > GEO readiness**, **Site score** tab |
| Scope | One page | Every published page |
| Fetches | 16, one per crawler plus a control | 1 per page |
| Runs | On demand | Cron, or a button |
| Answers | How does each crawler differ on this page | Where is the site weak |

The asymmetry is arithmetic, not taste. Sixteen fetches across 50,000 pages is 800,000 requests and
about fifteen hours. One per page is about an hour.

The cost is exactly one check: comparing a crawler's response against a browser's needs two
fetches, so it is dropped from the site score rather than allowed to pass for free. A
JavaScript-only page is still caught, by the word-count check.

## Where it sits in Content Governance

- **The drawer.** `src/javascript/drawer/GovernanceAction.jsx` registers one header action,
  **Content governance** (`headerPrimaryActions`), on `jnt:page` and `jmix:mainResource` nodes of a
  site that enables the module. `GovernanceDrawer.jsx` renders one tab per switched-on section and
  mounts `geo/GeoSection.jsx` the first time **GEO readiness** is opened; it stays mounted while the
  drawer is open, so switching sections keeps the result. `GeoSection` reports its issue count (the
  sum of its tab badges) to the drawer, which shows it on the section tab.
- **The dashboard.** `init.js` registers the `siteSettingsSeo/geoReadiness` admin route on
  `jcontent-siteSettingsSeo` with `requiredPermission: 'publish'` and
  `requireModuleInstalledOnSite`. It renders through `common/SectionGate.jsx`, which shows
  *Switched off for this site* when the site switched the GEO section off.
- **Site Settings > Content governance.** `settings/SettingsPanel.jsx`, route
  `contentGovernanceSettings` on `administration-sites`, `site-admin` only. Tabs **Features** (the
  switches), **AI provider** (status of the shared model) and **BotRank**
  (`geo/settings/CitationsSettings.jsx`, the former "AI citations (BotRank)" entry).
- **The switches.** Feature ids `geo`, `geo.score`, `geo.crawler`, `geo.files`, `geo.schema`,
  `geo.citations` (`settings/Features.java`). A site stores the ids it switched off on
  `/sites/<key>/content-governance` (`disabledFeatures`), so everything is on by default.
  `settings/SiteFeatures.isEnabled` is the server-side gate. In the browser, `isOn(tab)` filters the
  drawer's tabs and the dashboard's AI citations group; the dashboard's other panels follow the
  section switch only.

## Classes

Java package `org.jahia.community.contentgovernance.geo` (it was `org.jahia.se.modules.georeadiness`
in GEO Readiness). Front end under `src/javascript/geo/`.

### `check/`

| Class | Does |
|---|---|
| `AiCrawlers` | The one list of crawlers: name, user agent, robots token, and whether it answers questions or trains models. Both the fetcher and the robots checker read it, so they cannot drift apart. |
| `PageFetch` | One fetch, and everything readable from the HTML that comes back. Shared by both surfaces so a "fetch" means the same thing in each. |
| `GeoScore` | The 18 checks (plus `fetched` when a fetch got no answer), over a report. The site scan builds a report shaped like the drawer's and runs this, so one page can never score differently in two places. |
| `RobotsRules` | A robots.txt parser: longest match wins, `Allow` beats `Disallow` on a tie, `*` and `$` honoured, consecutive `User-agent` lines share a group. |
| `RobotsEditor` | Merges allow/block decisions into an existing file without disturbing anything it was not asked about. |
| `SiteFilesChecker` | Fetches and describes `robots.txt`, `llms.txt` and `llms-full.txt`. |
| `GuestVisibility` | Which published pages a visitor with no account cannot read, and why. |
| `SiteScorer` | The site walk: list, fetch once, score, aggregate. |
| `TemplateRollup` | Groups findings by template and decides which are the template's fault. |
| `PublishedMap` | Everything a guest can reach, keyed by public path, across every site language. Shared, because getting it wrong is not visible in the results. |
| `LinkGraph` | Inbound links per page, read from rendered HTML and topped up with repository references. Navigation counted apart from content. |
| `SchemaMap` | Node type to schema.org type, the properties each type needs, and where each can be sourced from. |
| `StructuredData` | Generates JSON-LD per node and coverage per site; reports gaps and contradictions rather than filling them. |
| `Languages` | Translation coverage per language, joined to the per-language scores; unmeasured kept distinct from zero. |
| `Freshness` | Ages everything published, bucketed and grouped by type and section, against a configurable threshold. |
| `LlmsFreshness` | Which links of the served llms.txt no longer lead to a published page of the site. |
| `VanityUrls` | Every address a page answers on, checked against the site's languages and the canonical tag. |
| `SitemapCheck` | Resolves every `sitemap.xml` entry back to a node and compares it with what is actually published, as guest, across every site language. |
| `ScanStore` | Reads and writes the scan on the site node. |
| `GeoReport` | The written report: the digest built from the stored state, the prompt, the call through the shared model, and the parse into a fixed shape. |

### `config/`

`GeoReadinessConfigService` reads `org.jahia.community.contentgovernance.geo.cfg` (PID
`org.jahia.community.contentgovernance.geo`): fetch timeout, body size, rate limit of the crawler
check, crawler list, public base URL. It holds no AI key any more: the report uses the shared model
(`ai/` below).

### Front end: `geo/`

| Path | Role |
|---|---|
| `GeoSection.jsx` | The drawer section: the **Run check** button, the banners above the tabs, and the tabs **Score**, **Crawler access**, **Site files**, **Structured data** and **AI citations**, each filtered by its switch. The last result is cached in the browser's local storage per page and language. **AI citations** loads on its own and is greyed out while the site has no BotRank binding. |
| `tabs/` | One component per drawer tab (`ScoreTab`, `CrawlerTab`, `SiteFilesTab`, `SchemaTab`, `CitationsTab`) and per dashboard panel (`SiteScorePanel`, `ReportPanel`, `VisibilityPanel`, `LinksPanel`, `VanityPanel`, `SchemaPanel`, `LanguagesPanel`, `FreshnessPanel`, `SitemapPanel`, `RobotsControlPanel`, `LlmsPanel`, `SiteCitationsPanel`), plus `CronBuilder` and `DiffView`. |
| `dashboard/GeoDashboard.jsx` | The Additional > SEO screen: five groups of tabs (overview, reach, usable, files, AI citations), one panel per tab. The **Report** tab is always listed and greyed out while the shared model is not configured; the AI citations group shows only while `geo.citations` is on. |
| `dashboard/exportScan.js` | **Export CSV** / **Export JSON** of the header: reads the stored scan (`scanStatus`) at click time and builds both files in the browser, one CSV row per finding. The cell quoting and download helpers are shared with the Compliance dashboard (`common/exportFile.js`). |
| `dashboard/Layout.jsx` | The one layout every dashboard panel uses: `Panel`, `PanelHeader` (title and intro, `Meta` pills for dates), `Card` per section, `Figures`/`Figure` for key numbers with a tone, `Ring` and `Hero` for the figure a panel leads with, `Actions`, `Note`, `Source`. A panel places no Separator and no heading Typography of its own. |
| `dashboard/Layout.module.css` | The type scale (page title 20/28, intro 14/1.6, hero 18/26, card title 16/22, card intro 13/1.55, body 14/1.55, notes 12.5/1.55, figures 32), card and figure styles, tones good/warn/bad/accent on the module's AAA colours. Moonstone body and caption paragraphs inside a panel follow the same scale. |
| `charts/Charts.jsx` | `Meter`, `BarList`, `StackedBar`, `Histogram`, `PairedBars`, `FailureMatrix`: the six forms the panels use, in HTML/CSS with a shared hover/focus tooltip. No library. `Histogram` is the only vertical form: counts over a time axis, on a track with a definite height, because a percentage resolves against nothing else. |
| `charts/Charts.module.css` | Tokens mapped from Moonstone, tone classes scoped under `.chart` so they beat every mark's default, mark specs (thin bar, rounded data-end, 2px surface gaps, surface ring on markers). |
| `settings/CitationsSettings.jsx` | The **BotRank** tab of Site Settings > Content governance. |
| `api/` | One client per servlet, each URL built by `common/url.js` under the context path and `/modules/content-governance`. |
| `util/Paged.jsx` | Pages any findings list with Moonstone's `TablePagination`. Full up to the page size (25 by default); the control appears from ten rows so a denser view can be chosen; past the page size the range is stated above the rows. |
| `util/jcontentUrl.js` | jContent's own address for a node, routing `/contents/...` to the content-folders section so a link to a content item resolves instead of landing on nothing. |

### `servlet/`

| URL under `/modules` | Scope | Writes? |
|---|---|---|
| `/content-governance/geo/crawler-check` | One page | No |
| `/content-governance/geo/site-scan` | One site | Only the scan record |
| `/content-governance/geo/site-files` | One site | **Yes**, robots.txt (llms.txt is read only) |
| `/content-governance/geo/report` | One site | Only the stored report |
| `/content-governance/geo/citations` | One page (GET), one site (GET `scope=site`, POST) | Settings, mappings and the sync record |

They were under `/modules/geo-readiness/` in GEO Readiness.

All five, through `GeoServlet`: authenticated callers only, an exact `application/json` media type
required (a substring test would let `text/plain; application/json` through, which a browser sends
cross-site without a preflight) and any request the browser marks as cross-site or same-site
refused (`Sec-Fetch-Site`, or an `Origin` other than `Host` or `X-Forwarded-Host`), per-user rate
limiting on the expensive actions, and generic errors that leak no upstream detail.

`site-scan`, `site-files` and `report` require the permission the dashboard route declares in the
front end, **publish** on the site (`SiteScope.DASHBOARD`), so the screen and the server state the
same rule. `SiteScope.require` also checks the switches: a site that does not enable the module,
or switched the GEO section off, gets the same answer as a missing node, a 403. `crawler-check`
requires a read of the node in the editing workspace, which is the floor the drawer actually opens
at, and answers 403 when the page's site switched the GEO section off; the facts it reads from the stored scan (template, sitemap, links, addresses, languages,
context) go through the same `SiteScope.require` and are left out for a caller without
**publish**, or when the section is off. `citations` checks `geo.citations` through
`CitationsAccess` and answers 403 when the module, the section or that tab is off.

### `ai/` (shared)

The provider layer moved out of the GEO package to `org.jahia.community.contentgovernance.ai`,
where the Page quality AI review and assists use it too. It mirrors automatic-content-tags so a
reader of one module recognises the other: `LlmProvider` (one chat completion), `LlmSettings`
(credentials and model, `toString` masks the key), `Completion` (text, whether it was cut, token
counts), `AnthropicProvider`, and `OpenAiProvider` and `DeepSeekProvider` over a shared
`OpenAiCompatibleProvider`, all looked up by name in `Providers`. DeepSeek's V4 models reason by
default, which eats the output budget and loosens the JSON-only instruction, so that provider
switches thinking off. `HttpJson` is the JDK client; the endpoints it reaches come from
configuration, never from a request. `LlmImage` carries pictures for the alt text assist, which the
report does not use.

`LlmConfigService` (PID `org.jahia.community.contentgovernance.llm`) is the one model connection.
`GeoReport` asks it for the settings with the report's own output budget (`AI_REPORT_MAX_TOKENS`),
appends `AI_PROMPT_APPENDIX` to its system prompt, and `GeoReportServlet` meters it with
`AI_REPORT_RATE_MAX_CALLS`. Without a provider, a model and a key, `isEnabled()` is false: the
report status answers `enabled: false` and the dashboard shows the **Report** tab greyed out.

`check/GeoReport` owns the rest: the digest built from the stored state, the prompt, the call, and
the parse into a fixed shape where every string is clipped, every enum normalised, every list
capped, and a page reference kept only if the digest listed that page.

### `scheduler/`

`SiteScanJob` is a Quartz `BackgroundJob`; `ScanScheduler` installs one cron trigger per site and
language, in the Quartz group `content-governance-geo` (it was `geo-readiness`).
`isProcessingServer()` keeps a cluster from scanning the same site on every node. A run on a site
that switched the GEO section off is skipped, and the trigger stays, so switching the section back
on resumes the schedule.

A trigger names a job class from this bundle, so leaving one in place across a refresh would point
it at a class the framework has replaced. `ScanLifecycle` removes them when the bundle stops and
reinstalls them from the repository when it starts, which is what keeps a redeploy from either
stranding a trigger or discarding an editor's schedule. The schedule itself lives in the store,
never only in Quartz.

A run is owned: the store records the account that saved the schedule, and each run asks again
whether that account still holds the permission. A trigger nobody is entitled to removes itself.

### `botrank/`

The BotRank connector (GEO-4). BotRank's public API v1 is read server to server with the customer's
organisation key; the browser never calls BotRank, and editors never cause a call.

| Class | Does |
|---|---|
| `BotRankConnection` | One organisation, from one factory configuration file `org.jahia.se.modules.georeadiness.botrank-<id>.cfg` (`configurationPolicy = REQUIRE`). The PID keeps its GEO Readiness name so existing files keep working. The id comes from the file name. A file without a key registers with a `problem` and is never used. |
| `BotRankConnections` | Every connection, bound and unbound by DS; `forSite` honours a connection's `sites` list with no fallback. |
| `BotRankClient` | The read routes it uses (projects and one project, data freshness, a site's cited pages, totals, share history and ranking under `/ai-visibility/sites/{domain}/`, the visibility score and its history, the five page routes) on `java.net.http`: bearer key, `Project-Id`, explicit Paris days, cursor walk bounded at 50 pages, no redirects. RFC 9457 problems become a `BotRankException` that carries the type and never the key. |
| `CitedUrls` | BotRank's page identity: `www.`, trailing slash, anchor, its published tracking parameters and host case fold away; scheme, subdomain, path case and other parameters do not. |
| `PageResolver` | A cited URL to `jcrPath@language`: manual mappings, then published URLs (`PublishedMap`), every vanity URL in live, then `/sites/...` paths. A cited URL with a surviving query resolves through a mapping only. |
| `CitationSync` | One run: freshness, the 30-day and all-time lists (one per host, since BotRank lists one domain per call, merged and ranked across the site), resolution, the details of recently cited pages, and the variation guard (a variation is dropped when the previous window had no run day). Sequential calls. |
| `SiteCitations` | The dashboard's AI citations group, read at the end of each sync: per registrable domain (each host read once, the answer names its domain) totals, share history and ranking with three neighbours; per host, citations, cited pages and an approximate share computed from the 30-day list; the top 20 cited addresses with their page; the brand visibility score; the project's engines and refresh intervals. A domain never cited is a state; any other failed read is counted and never fails the run. |
| `CitationStore` | Settings, run record, the site summary and one node per cited page, under `geo-readiness/botrank`. |
| `CitationScheduler`, `CitationSyncJob`, `CitationLifecycle` | A daily cron trigger per site in `Europe/Paris`, in the Quartz group `content-governance-citations` (it was `geo-readiness-citations`), one-shot triggers for sync now and retries, restored from the store when the bundle starts and removed when it stops, on the processing server only. Each run re-checks that the site still enables the module with the GEO section and its AI citations tab on, and that the user who saved the settings still holds `site-admin`; otherwise it turns the daily sync off and records why. |
| `CitationsAccess` | Who may do what: a read of the page for the drawer, `publish` for the site figures (as the dashboard), `site-admin` for the settings, and for all three the module enabled on the site with `geo.citations` on (`SiteFeatures`). The site figures name a top page's path and title only when the caller can read it. |

### `util/`

| File | Role |
|---|---|
| `PublicUrls` | A page's public address by asking Jahia rather than guessing: `node.getUrl()` then the outbound URL rewriter, which yields the vanity URL where one resolves. The base host comes from `PUBLIC_BASE_URL` or the site's own server name; a request may contribute scheme and port, and only once it is already addressing that host. |
| `MockHttp` | A request and response as dynamic proxies, because the rewriter needs them and a scheduled job has neither. |
| `SiteScope` | Which site a request may act on, and where inside it. Resolves in the caller's session and compares the resolved path, because a request carries three values shaped like a path and JCR reads a relative one the way a filesystem does. Also the language pattern, since that value becomes a node name, and the GEO section switch. |
| `FetchGuard` | What may be fetched: an http(s) address on the same origin as the base. The sitemap index is fetched content naming further urls, so its children go through this. |

## Storage

GEO data stays where GEO Readiness left it, so the old module can be replaced in place: one hidden
`nt:unstructured` node per site, `/sites/<key>/geo-readiness`, carrying `jmix:nolive` so it never
publishes. A child node per language holds the run record and results, and the language is checked
against a pattern before it is used as that node's name. The written report is stored there too,
one per language, as the parsed shape rather than the model's raw answer.

The site's switches live apart, on `/sites/<key>/content-governance` (`nt:unstructured` +
`jmix:nolive`, property `disabledFeatures`).

The GEO section declares no node type. That is deliberate: a rejected content type does not merely
fail, it breaks Content Editor across the instance until the module is removed, and nothing here
needs a typed model. The module's `definitions.cnd` holds only the Compliance glossary types.

**Aggregate plus failures, never a row per page.** A per-page record measures 86 bytes, so every
page of a 50,000 page site across three languages would be about 12 MB against 2.5 MB for the
failures alone. The dashboard's four questions (overall score, score by section, worst pages,
movement since the last run) are all answerable from the smaller shape. The previous aggregate is
kept, which is the whole of "movement".

**BotRank citations** live under `geo-readiness/botrank`: the settings (`brConnection`,
`brProject`, `brHosts`, `brEnabled`, `brTime`, `brOwner`, `brMappings`), the record of the last sync,
the site summary of the dashboard's AI citations group (`brSite`, JSON) and a `pages` child replaced
as a whole by every sync, with one node per cited page named by the SHA-256 of `jcrPath@language`.
A page absent from it has never been cited. Text that comes from BotRank is bounded in length and
rendered as text.

## The write path

Only `site-files` writes, and never silently. Generate produces the text, the interface shows a
line diff against what is stored, the apply button needs a second confirming click, and what gets
written is exactly the text on screen. An editor can hand-edit a generated file and keep their
edits.

The two files are owned by the `robots` and `llms` modules, which add `jmix:robots` and `jmix:llms`
to the site node. Content Governance declares both as optional dependencies
(`robots=optional,llms=optional`): the module starts without them, Page quality and Compliance never
need them, and every GEO check still fetches `/robots.txt` and `/llms.txt` over HTTP. Only writing
robots.txt from the dashboard fails, with an error, while the `robots` module is missing. `llms.txt`
is never written here: the dashboard reads `j:llms` from the live site node and links to the `llms`
module's own screen.

Reporting is safe to run against anything. Writing is not. Keep the two visibly apart.

## Configuration

Files in `digital-factory-data/karaf/etc/`. The module ships the first two with `# default
configuration` as their first line, so a redeploy never overwrites them; the BotRank files are added by
a server administrator and never shipped. Edits apply at once, with no redeploy, except for scheduled
scans: a schedule keeps the fetch limits it was given when it was saved or when the module last
started.

### `org.jahia.community.contentgovernance.geo.cfg`

The crawler check and its fetches. It replaces `org.jahia.se.modules.georeadiness.cfg`, without the
AI keys.

| Key | Default | What it does |
|---|---|---|
| `FETCH_TIMEOUT_MS` | 8000 | Per-fetch timeout. Sixteen agents run three at a time, so the drawer's worst case is about six times this. |
| `MAX_BODY_BYTES` | 1500000 | How much of a page to read. |
| `RATE_MAX_CALLS` / `RATE_WINDOW_MS` | 20 / 600000 | Per-user limit on the crawler check only. The site scan has its own limit as a constant in the servlet (30 per ten minutes) and the report uses `AI_REPORT_RATE_MAX_CALLS`; neither reads these keys. Reading a stored result is never limited. |
| `CRAWLER_AGENTS` | *(blank)* | Override the agent list as `name\|user-agent` pairs. Blank uses the built-in fifteen. |
| `PUBLIC_BASE_URL` | *(blank)* | The base URL to fetch. Set this when the site's server name is not resolvable from inside the container. |

The address a check fetches comes from `PUBLIC_BASE_URL`, or from the site's own server name when
it is blank. The scheme and the port come from the connection the request arrived on
(`getLocalPort()` and `isSecure()`), never from a header, because a `Host` header is written by
whoever sent the request.

A site that declares no server name, or names `localhost`, does **not** refuse to fetch: it falls
back to `http://localhost:8080` and fetches that. On a box serving one site that is usually right.
On a box serving several it is the *default* site, so the check reports another site's pages and
robots.txt as if they were yours, quietly, because the fetch succeeds. `PUBLIC_BASE_URL` is how you
say which, and on a multi-site box it is required rather than optional.

### `org.jahia.community.contentgovernance.llm.cfg`

The one model every AI feature of the module uses. The written report reads these keys:

| Key | Default | What it does |
|---|---|---|
| `AI_PROVIDER` | *(blank)* | `anthropic`, `openai` or `deepseek`. With `AI_MODEL` and `AI_API_KEY` set, the **Report** tab can be used. Blank keeps it greyed out and nothing leaves the server. |
| `AI_MODEL` | *(blank)* | The model to ask. |
| `AI_API_KEY` | *(blank)* | The provider key. Never logged, never sent to the browser. |
| `AI_BASE_URL` | *(blank)* | The provider's API base, for a proxy or a compatible gateway. Blank uses the provider's own. |
| `AI_REPORT_MAX_TOKENS` | 6000 | Output budget of the report. A site-wide report is long; too low and the end is cut, which the tab says. |
| `AI_REPORT_RATE_MAX_CALLS` | 5 | Report generations allowed per user per ten minutes. |
| `AI_PROMPT_APPENDIX` | *(blank)* | Instructions appended to every prompt of the module, the report's included. |

`AI_MAX_TOKENS`, `AI_RATE_MAX_CALLS` and the two `AI_COST_*` keys in the same file belong to the
page tasks of Page quality; the report does not read them. Site Settings > Content governance >
**AI provider** shows whether the model is configured, its provider and model, never the key.

### BotRank connections

AI citations come from [BotRank](https://www.botrank.ai/)'s public API v1. Each BotRank organisation
this Jahia may read is one file,
`digital-factory-data/karaf/etc/org.jahia.se.modules.georeadiness.botrank-<id>.cfg`, created by a
server administrator; `<id>` is what site administrators pick on the **BotRank** tab of Site
Settings > Content governance. The file name is the one GEO Readiness used, so existing files keep
working. No file ships with the module, because a file is a credential.

```properties
# BotRank organisation of the customer
label = Example (BotRank)
apiKey = brk_...
# Optional: the site keys allowed to use this connection. Blank allows every site.
sites = example-fr,example-com
# Optional: the API base, https://api.botrank.ai/public/v1 by default.
baseUrl =
```

- The key is created by an owner of the BotRank organisation, under Organisation, Integrations, API
  keys. It is never logged and never sent to the browser; a connection without one is listed with
  its problem and never used.
- A site restricted out of a connection does not see it and cannot use it, with no fallback.
- A site administrator then binds the site on the **BotRank** tab: connection, project, the site's
  hosts (at most 10), the daily sync and its time. What each field does is in
  [`dashboard-guide.md`](dashboard-guide.md#for-site-administrators-switches-and-botrank).
- The daily sync runs at the time saved for the site (06:30 Paris time by default), on the
  processing server only, and waits while BotRank is still collecting answers, until 10:30 at the
  latest. It reads the cited pages of the site's hosts, one list per host since BotRank lists one
  domain per call (a site on `example.fr` and `example.com` gets both), ranks them across the site,
  matches each cited address to a page (manual mappings, published URLs, every vanity URL,
  `/sites/...` paths) and stores one entry per cited page under the site's hidden `geo-readiness`
  node. It then reads the site's own figures for the dashboard: totals, share and rank per
  registrable domain, the brand visibility score and the project's engines. Editors never cause a
  call to BotRank.
- BotRank's site figures cover the whole registrable domain: a site on `blog.example.com` shares them
  with every other site of `example.com`. The dashboard names the domain and splits citations per
  host from the cited pages list.

## Who can use it

| Surface | Permission | Shown when |
|---|---|---|
| Drawer section | can read the page in the editing workspace (anyone who can select it in jContent) | the site enables the module and keeps the GEO section on |
| Drawer facts from the last scan, context lines | **publish** on the site | as above |
| Drawer **AI citations** tab | can read the page | `geo.citations` on; greyed out until the site is bound to BotRank |
| Dashboard, robots.txt editor, llms.txt view | **publish** on the site | the GEO section is on (otherwise *Switched off for this site*) |
| Dashboard **AI citations** group | **publish** on the site | `geo.citations` on |
| Site Settings > Content governance, BotRank tab included | **site-admin** on the site, on the route and on every call: binding a site to a connection spends the customer's organisation key | always listed; the BotRank tab cannot read the site's settings while the GEO section or `geo.citations` is off |

A scan's optional scope has to name a node inside the site it was started from, and it is resolved
before it is compared, so a relative path cannot leave the site it came from.

The site scan reads every published page of the site, and the dashboard and the drawer's facts from
the last scan show their paths and titles to anyone holding **publish** on the site, pages restricted
to some members included: knowing what the site publishes is what the dashboard is for. The AI
citations panels list only the pages the reader may read (decision 2026-10-29, as in GEO Readiness).

## Known gaps

- **A Jahia Cloud node cannot fetch its own public URL.** Seen on 15 October 2026: the public URL
  answers from outside, while the node gets `ConnectException: Connection refused` on it, so the
  sitemap, `robots.txt`, `llms.txt` and the crawler fetches all fail there. The checks say which
  error stopped the fetch instead of reporting a missing file. `PUBLIC_BASE_URL=http://localhost:8080`
  reaches Tomcat directly, but only the instance's default site answers there, and the checks then
  skip the proxy a real crawler goes through; the fix belongs to the platform.
- **The drawer's sitemap facts come from the last site scan**, never from a fetch of its own; each
  of them carries the date of that scan.
- **The site scan is capped at 2000 pages per run** and is synchronous when triggered by the
  button. A larger site needs the schedule.
- **One language per scheduled run.** A multilingual site needs one schedule per language.
- **`llms-full.txt` cannot exist on this stack.** The community `llms` module declares one property
  and one route, so the report calls the file absent when it is really unsupported.
- **The link graph and the freshness picture judge one language per scan**, since only that
  language's pages were fetched. A multilingual site needs a scan per language to see all of it.
- **The sitemap comparison resolves rather than fetches**, so it cannot see a redirect inside the
  sitemap's own entries.
- **Structured data is generated, never injected.** Deliberate, but it means somebody has to place
  the snippet in a template for it to reach a visitor.
- **The robots.txt merge is line-based**, not a full RFC 9309 rewrite. It does not reorder,
  deduplicate or tidy, so a hand-written file comes back recognisable.
- **The check fetches a page even when robots.txt disallows it**, deliberately. Knowing that a
  disallowed page is nonetheless reachable is a finding.
- **A scan with a scope replaces the stored result for the whole site**, so every panel then
  reports on that subtree until a full scan runs again.
- **Most findings lists are capped, and only one says so.** The per-page failures list stops at 500
  rows, the link-graph and sitemap lists at 200 each, the llms.txt lists at 100, and the freshness
  list at 5000, which is the only one that emits a truncation flag the interface shows. The counts
  and the groups above the freshness list still measure everything.
- **The report is written by a model and can be wrong.** It prioritises and explains; it measures
  nothing itself. Check a recommendation against the panel it refers to before acting on it. It
  also costs money per answer, which is why it is never generated on its own.
- **The report reads one language at a time**, the one the dashboard is showing, and its digest is
  cut at fourteen thousand characters. On a very large site the counts and groups still reach the
  model; the per-page examples are what gets trimmed.
- **A scheduled scan runs as the account that saved it** and re-checks that account's permission on
  every run, so revoking the permission stops the schedule rather than merely hiding the screen.
- **A BotRank connection without `sites` is open to every site administrator of the instance**, and
  each of them can bind a site to that organisation's key. List the sites of each connection on a
  shared instance. A `sites` value that cannot be read closes the connection rather than opening it.
- **A BotRank sync reads the details of the 200 most cited pages of a site.** The others keep their
  total and rank. One run walks two lists per host (the last 30 days and all time, 50 pages of 100 at
  most each), then makes up to thirteen calls per detailed page, one at a time: about 2,600 calls for
  a site with one host; a run still going is never taken for dead and doubled.
- **A cited address mapped by hand counts in the language the settings screen was showing**, and a
  mapping takes effect at the next sync.
- **Jahia's CSRF guard.** Every servlet of the module (`/modules/content-governance/geo/*` and
  `/audit/*`) is under the guard (`org.jahia.modules.jahiacsrfguard-content-governance.cfg`, owned by
  the module, replaced on every deploy). The drawer, the dashboards and the settings send the guard's
  token through `common/guardedFetch.js`, which reads it from `/modules/CsrfServlet`, keeps the
  per-address token the guard answers with, and after a refusal reloads the tokens once and retries.
  The master token works once per address: while the first request to an address is out, the others
  to that address wait for its answer and its page token (the GEO dashboard sends several to one
  servlet at once; sent together, all but the first would be refused and cost a token reload).
  The servlets answer browser sessions only: they take no API token, and a call signed in another
  way without the token is refused. The JSON content type and same-site checks stay as a second check.
- **A BotRank sync stops** when the site disables the module, switches off the GEO section or its
  AI citations tab, or when the administrator who saved the BotRank settings loses site-admin. The
  sync turns itself off and records why; a site administrator turns it on and saves the settings
  again to restart it.
