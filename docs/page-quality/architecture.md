# Page quality: architecture

How the **Page quality** section of the Content Governance drawer works, for developers who
maintain it. The user-facing behaviour is in [guide.md](guide.md).

## In one paragraph

The checks of SEO, readability, links and Jahia health run on the server: the GraphQL field
`checkPage` renders the page as the editor, in the edit workspace, runs every check of the server on
it, and gives the facts that these tabs show. `frame.check` gives an agent the same findings for the
same path. The checks that need a browser run in the editor's browser: the section loads
`/cms/render/default/{lang}{path}.html` in a same-origin iframe, waits for it to settle, strips
editing tooling from its DOM, and runs axe-core, the web vitals and eco-design against the frame's
document and performance entries. Results are kept
in the editor's `localStorage` and, once complete, stored on the site for every editor (see
[Shared audits](#shared-audits-and-the-site-dashboard)). The section calls no model. When the
module ai-assistant is installed, it adds its own AI review tab to the drawer
([`drawer-entries.md`](../drawer-entries.md)).

```
jContent / Page Builder header
  GovernanceAction (headerPrimaryActions:890)
    GovernanceDrawer  (portal into document.body)
      AuditSection
        GraphQL checkPage (HtmlCheck)  -> seo, readability, links, jahia, HTML rules
        iframe  /cms/render/default/{lang}{path}.html?pageAuditRun=N
          removeToolingElements -> webVitals -> ecodesign -> accessibility (axe-core injected)
        localStorage  content-governance:audit:{lang}:{path}
```

## Where the code lives

| Path | Role |
|---|---|
| `src/javascript/init.js` | Loads the editor's language file (only that one: `en` or `fr`, English otherwise), then registers the `contentGovernance` action on `headerPrimaryActions:890`, label `drawer.action` ("Content governance"), and the routes. Everything else loads on first use: the GEO and Compliance dashboards and the settings panel through `React.lazy` with a Moonstone loader, the drawer from `GovernanceAction`. jContent's start loads about 105 kB of the module (622 kB up to 1.4.0); the drawer spec fails above 200 kB. |
| `src/javascript/drawer/GovernanceAction.jsx` | Shows the button on `jnt:page` and `jmix:mainResource` of a site that enables the module (`useNodeChecks`, `requireModuleInstalledOnSite`), reads the site settings, opens the drawer. The drawer and its three sections are one lazy chunk, fetched when the browser is idle once the button shows (`requestIdleCallback`, else after 2 s), at the latest on the first click. |
| `src/javascript/drawer/FixFirst.jsx`, `findings.js` | "Fix first": each section reports `{key, section, tab, severity, title}` findings through `onFindings` (Page quality: violations by impact and every tab's recommendations, `auditFindings`; GEO: one per tab with a count, score and crawler serious, the others moderate; Compliance: one serious per term, `complianceFindings`). The drawer keeps them per section (an identical list changes nothing), `rankFindings` keeps the five most serious, ties in section order, and **Open** selects the section and passes `requestedTab` `{tab, at}`. |
| `src/javascript/drawer/GovernanceDrawer.jsx` | The drawer: header, one section tab per switched-on section with its count badge, sections mounted on first show and kept mounted while the drawer is open. |
| `src/javascript/drawer/useDock.js` | Docks the drawer beside the page under edit, under jContent's header, and resizes it from its left edge (see the drawer notes below). |
| `src/javascript/audit/AuditSection.jsx` | The section: the check of the server, preview frame, analyzer pipeline, cache, staleness probe, export, tab bar and badges. |
| `src/javascript/audit/pageCheck.js` | Calls `checkPage`, and turns its findings and facts into the results of the tabs SEO, Readability, Links and Jahia health, and the HTML rules of the Accessibility tab. |
| `src/javascript/audit/analyzers/` | The analyzers of the browser (`webVitals.js`, `ecodesign.js`, `accessibility.js`), plus `tooling.js` (DOM cleanup) and `cssPath.js` (the selector a highlight finds an element by). |
| `src/main/java/org/jahia/community/contentgovernance/page/` | The checks of the server for each domain (`SeoChecks`, `ReadabilityChecks`, `LinkChecks`, `HealthChecks`), with the facts each one measures, and `PageFacts`, which gathers them for `checkPage`. |
| `src/javascript/audit/tabs/` | One component per tab, the shared `Recommendations` list, the two manual checklists. |
| `src/javascript/common/useSiteSettings.js`, `settings.gql.js` | Site switches, from `contentGovernance.siteSettings`. |
| `src/main/java/org/jahia/community/contentgovernance/settings/Features.java`, `SiteFeatures.java` | Feature ids and the server-side gate. |
| `src/main/resources/javascript/locales/{en,fr}.json` | Labels, under `content-governance.audit`, `content-governance.drawer` and `content-governance.common`. |
| `webpack.config.js` | Copies `node_modules/axe-core/axe.min.js` to `javascript/apps/axe.min.js`. |

## Opening and gating

- `GovernanceAction` resolves the path from its props or jContent's Redux state (`state.jcontent.path`),
  and the language from its props or `state.language`. The drawer has no language picker: it audits
  the language jContent is in.
- `useSiteSettings(siteKey)` runs `ContentGovernanceSiteSettings` with `fetchPolicy: 'network-only'`
  on every mount, so a switch changed by an administrator applies the next time the drawer opens. It
  returns `isOn(section, tab)`.
- The drawer is a non-modal `<dialog open>` (not `showModal()`, which would make it modal, in the top
  layer above Content Editor and jContent's menus) rendered by a portal into `document.body`.
  `drawer/useDock.js` docks it in jContent's content area (`main .moonstone-layoutContent`), under
  the header: it gives the Page Builder frame's wrapper (or the content list in list views) a
  `margin-right` of the drawer's width plus 16 px, puts the original margin back on close, and
  follows window resizes, the swap between Page Builder and the list, and the frame's own size (a
  page header above it, such as Page Insights, opening or closing moves it while the area keeps its
  size). The width starts at 45% of
  the area (600 px at least), leaves the page 360 px, and is dragged on the left edge (a native
  button, since Sonar refuses a focusable `role="separator"` div; arrows move 16 px, Shift 64) and remembered in `localStorage`
  (`content-governance:panelWidth`). Where the area cannot keep 360 px beside a 480 px drawer, the
  drawer covers the page instead. Geometry is written to the elements, not React state, so a drag
  does not render the sections. It closes on Escape, its close button and the header button; a
  press on the page leaves it open, and it returns focus where it was on close.
- `AuditSection` receives `isOn(tab)` for the `audit` section. `TAB_IDS` (`accessibility`, `seo`,
  `vitals`, `readability`, `ecodesign`, `links`, `jahia`) is filtered by it.

A tab switched off is not measured. Its analyzer does not run, and its result is not cached or
exported (`keepEnabled` in `AuditSection.jsx`). Web Vitals still runs as the input of Ecodesign, and
the check of the server runs while a tab that reads it is on, Accessibility included, and gives the
page facts kept with each audit: `results.page` holds the title, the meta description and the number
of images without alt. Neither result is kept when its own tab is off. A run whose check of the
server did not finish, or whose server could not read the state of the content (Jahia health and
Links then show why), is never cached or stored. A cached audit that lacks a tab switched back on
since is measured again. `results.page.schema` holds `CACHE_SCHEMA`, and a stored audit of another
schema is measured again: 6 dropped the audits from before the check of the server.

## The preview frame

- URL: `${contextPath}/cms/render/default/${language}${path}.html?pageAuditRun=${runId}`, where
  `contextPath` is `contextJsParameters.contextPath` (empty at the web root). `runId` increments on
  **Re-run**, which also remounts the iframe (`key={runId}`), so the browser cannot serve a cached
  document.
- `default` workspace: the audit sees what the editor is editing, unpublished changes included.
  Internal links in that render legitimately point to `/cms/render/default/...`.
- The iframe mounts 400 ms after the section effect runs. A frame loaded while its box is still being
  laid out is throttled by Chrome and never emits paint entries.
- The frame's `load` event waits for every image, font and script of the page, so one request that
  never answers would hold the section on "Loading" for good. `FRAME_LOAD_LIMIT_MS` (20 s) after the
  frame mounts, a document whose `readyState` is past `loading` is audited anyway; one still loading
  shows `drawer.frameTimeout`. `analyzedRunRef` makes sure a run is analysed once, whichever comes
  first, and that a later load of the frame (a link followed in the preview) does not analyse again.
- On `load`, the section waits `SETTLE_MS` (2500 ms) for client islands to hydrate, then:
  1. reads the frame's `navigation` performance entry and throws `HTTP <status>` when
     `responseStatus >= 400`, so a 404 or 500 render is refused instead of scored;
  2. calls `removeToolingElements(doc)`, which removes `[id^="tst-"]`, `[class*="tst-"]` and
     `#personas_panel` (jExperience persona preview, injected by `wem.js` in authoring contexts),
     before any analyzer reads the DOM;
  3. starts the check of the server, then runs the analyzers of the browser in order. Each step goes
     through `runStep` (`analyzers/timeLimit.js`) with `STEP_LIMITS_MS`: the check of the server
     60 s, vitals 15 s, accessibility 60 s. A step that fails or runs out of time is left out: its
     tabs show `IncompleteStep` (why, and **Re-run**) from `results.incomplete[<result key>]`, the
     check of the server for SEO, Readability, Links and Jahia health, eco-design after vitals. The
     other tabs show their results, and such a partial audit is never cached. The late answer of a
     stopped step is ignored, not cancelled.
- When results come from the cache, the frame still loads (preview and highlight target) but the
  analyzers do not run (`cacheHitRef`).
- Highlight (`audit/editFrame.js`): the audit always runs in the preview, a plain render of the page;
  the outline goes to Page Builder's active frame (`iframe[data-sel-role="page-builder-frame-active"]`)
  when it shows the audited page and language (`/<lang><path>.html` ends its path). `highlight(selector)`
  resolves the selector in the preview, then `locateInFrame` finds the same element in the edit frame:
  the same id, else the same tag with the same `src`/`href`/`alt` and text at the same rank, outside
  jContent's `#jahia-portal-root`. A container whose markup edit mode changed gives way to its nearest
  ancestor (4 at most) that matches and holds an element of its tag; an element with no children
  never climbs. `highlightText(sample)` normalises whitespace, quotes and case, searches at element
  level, falls back to the first 8 then 4 words, and outlines the tightest matching element, in the
  edit frame first. What the edit frame lacks is outlined in the preview, which opens. One outline at
  a time; it is removed when the drawer closes.
- Hidden preview: beside Page Builder `previewCollapsed` starts true. Hidden is not folded: the
  preview sits behind the results at the size of the whole section (`position: absolute; inset: 0;
  z-index: -1` in the drawer section's isolated stacking context, every band above it opaque,
  `inert`, `aria-hidden`), because the analyzers read the frame's viewport and paint (LCP, images
  above the fold) and a frame with no height measures nothing. A re-run keeps the editor's choice.

## The check of the server

`audit/pageCheck.js` calls the GraphQL field `checkPage(path, language)` (`ContentGovernanceCheckPage`,
`fetchPolicy: 'no-cache'`). The server renders the page as the editor, in the edit workspace, with
`HtmlCheck`, the service that `frame.check` and `audit.accessibility` call too. It answers:

- `findings`: each place where the page breaks a rule of the site, `{ruleId, severity, text, context}`,
  in the order of the rules. A rule that the site turned off gives none.
- `facts`: a JSON object of what the tabs show, by tab. `seo`: the tags of the head and the images.
  `readability`: the statistics
  of the main text with the formula of its language (`Readability`), its paragraphs, and the outline
  of its headings, each with a CSS selector of the server render, which the preview resolves to
  highlight it. `links`: the links to a page, the internal links resolved in the repository and the
  broken ones (`missing` or `not_published`). `jahia`: the status of the page, the content items
  read (400 at most), what is not published in the language, and what lacks an active language.

Each finding goes to the tab of the first subdomain of its rule that has one (`tabOf`,
`DOMAIN_TABS`): `accessibility`, `seo`, `readability` (and `writing`), `links`, `health` for Jahia
health, and `ecodesign`. No list names the tab of one rule, so a check that another module registers
lands like a built-in one. A
tab shows one recommendation per rule `{key, rule, severity, params, items, title, detail, label}`:
`items` holds each finding of the rule, with its `locator` when the check gave one. The texts come
from the rule (`audit/ruleText.js`), in the language of the interface of the editor: the plural form
of its title for the count, with the values `count`, `value` (the text of the first finding), `min`
and `max`, and its advice. A finding with a locator offers "Highlight on the page", and says "Item
not found" when the preview has no such element. An audit stored before this version keeps its old
texts (`audit.<ns>.recs.<key>`), and the drawer offers "Run again".

## The analyzers of the browser

Each analyzer gets the rules of the site, which `audit/rules.js` reads at the start of the audit
through the GraphQL field `contentRules(siteKey, language)` ([the catalog](../content-rules/catalog.md)).
A finding is `{key, rule, severity, params, title, detail}`: `rule` is the id of its rule, `severity`
is the severity of the rule, `params` holds the values of its texts, and the texts come from the
rule. axe-core skips each check whose rule `axe.<id>` the site turned off, and each violation keeps
the impact of axe-core beside the severity of the site. The findings of the web vitals are rules of
the subdomain `ecodesign`: the Ecodesign tab shows them. A rule that the site
turned off gives no finding. These rules are browser rules: the server registers their ids and never
runs them, and they never block.

Run in this order in `handleFrameLoad`. Each returns a result object with a `recommendations` array
of these findings.

| Analyzer | File | Reads | Notes |
|---|---|---|---|
| Web Vitals | `webVitals.js` | Buffered `PerformanceObserver` (`largest-contentful-paint`, `layout-shift`, 700 ms window), navigation and resource entries, `doc.images` | Runs first so axe is not in the resource stats. LCP falls back to the `responseEnd` of the largest in-viewport image, else `domContentLoadedEventEnd` (`lcpApprox`). INP is always `null`. The timings are indicative figures: no band, no finding (they are measured on the editing preview in the editor's browser and cannot match the live site); `buildVitalsRecommendations` reads the diagnostics only, and `audit/vitalsFindings.js` drops the `slowLcp`, `highCls` and `slowTtfb` findings of audits stored before 1.11 where the dashboard shows them (`auditFindings`). The counts stored with those audits keep them, as history. Size is `transferSize`, else `encodedBodySize` (HTTP cache hits). |
| Ecodesign | `ecodesign.js` | Vitals diagnostics, resource entries, DOM | 10 criteria, each under the rule `ecodesign.<criterion>` of the catalog, whose maximum it reads: weight, requests, DOM size, below-the-fold images without `loading="lazy"`, fonts (WOFF2 only), autoplay media, legacy-format images over 30 kB, oversized images, missing dimensions, third-party origins. A criterion passes when its rule has no finding. Not an RGESN conformity score; `ecoChecklist.js` lists 8 whole-service criteria. |
| Accessibility | `accessibility.js` | Whole frame document | Injects axe-core (below) and runs tags `wcag2a, wcag21a, wcag2aa, wcag21aa, wcag22aa, wcag2aaa, best-practice` by tag, not by rule list, so an axe-core update brings its new rules. Keeps 25 nodes per rule. A card's percentage is passes / (passes + violations), floored; `incomplete` is outside the ratio. `a11yChecklist.js` lists 14 manual criteria. The tab also lists the HTML rules of the server that no other tab words. |

### axe-core injection

`axe.min.js` is shipped as a static resource of the bundle, at `javascript/apps/axe.min.js`
(webpack `CopyWebpackPlugin`), and loaded into the frame with a `<script src>` built by
`moduleUrl('/javascript/apps/axe.min.js')`, that is
`${contextPath}/modules/content-governance/javascript/apps/axe.min.js`. It runs in the frame's own
realm. Inline injection is not possible: `axe.source` was removed in axe-core 4.

### GraphQL through jContent's Apollo client

`AuditSection` takes the client from `useApolloClient()`. `@apollo/client` is shared with the host
as a Module Federation singleton with `import: false`, so the bundle never ships its own copy and
the queries run with the editor's session. Two queries, both `fetchPolicy: 'no-cache'`:

- `ContentGovernanceCheckPage(path, language)`: the check of the server.
- `pageAuditLastModified(path, language)`: `aggregatedLastModifiedDate` of the page and its content,
  stopping at sub-pages. Used as the staleness probe when results come from the cache: if it is later
  than the cached timestamp, the **Page modified since this audit** banner is shown. It compares a
  server date with a browser timestamp.

## Results, badges, cache and export

- Results: `{a11y, vitals, ecodesign, readability, seo, links, jahia}`.
- Counts (`audit/tabCounts.js`): the violations of axe-core and the HTML rules of the server for
  Accessibility, `recommendations.length` for the other tabs, each with `serious` when one finding is
  critical or serious. `common/CountBadge.jsx` draws a count red (Moonstone `Badge`, danger) when
  serious and grey (`Chip`) otherwise, on the tabs of every section and on the section tabs, where
  the drawer reads the severities from the findings the section reports. The section total, reported
  through `onCount`, sums the counts of the switched-on tabs. "Fix first" lists critical and serious
  findings only (`isSerious`, `drawer/findings.js`) and shows nothing without one. GEO readiness:
  Score and Crawler access are serious, the other tabs grey.
- Cache: `localStorage` key `content-governance:audit:{language}:{path}`, entry
  `{schema, timestamp, results}`. `CACHE_SCHEMA` is 6: bump it whenever the shape of `results`
  changes, or an old entry is restored into a UI expecting the new shape (entries with another
  schema are deleted on read). Pruned to the 10 most recent entries;
  on a quota error, pruned to 2 and retried once; failures are silent.
- **Re-run** (`runId > 0`) skips the cache.
- **Shared audits:** see the next section.
- **Export JSON** builds `{path, language, url, auditedAt, results}` in the browser and downloads
  `content-governance-audit-<last path segment>.json`.

## Shared audits and the site dashboard

- **Storage:** `AuditHistoryService` (`audit/service/spi`, DS component `AuditHistoryServiceImpl`)
  writes with system sessions in the site's private module node
  (`/sites/<key>/content-governance`, `PrivateStore`), under `audits/<language>/<page identifier>`.
  - The page node holds the latest audit's `summary` (counts per tab, total, critical and serious
    accessibility violations), `auditedAt`,
    `auditedBy`, `path`, `title` and `previousTotal` (the total of the audit before, -1 for none).
  - One `run-<millis>` child per audit holds `results` (the JSON), `summary`, `auditedAt` and
    `auditedBy`.
  - The page keeps `MAX_RUNS` (10). Keyed by identifier, so a moved page keeps its audits.
- **Input:** `AuditResults.parse` accepts a JSON object of at most 1,000,000 characters. It keeps the
  tab results (`a11y seo vitals readability ecodesign links jahia`) and `page`, and drops anything
  else. The counts come from `AuditSummary.of` on the server: accessibility violations, and the
  recommendations of every other tab.
- **GraphQL**, under `contentGovernance` (`audit/graphql/AuditFields`):
  - `saveAudit(path, language, results)` (mutation, once per request);
  - `pageAudits(path, language)`: newest first, only the newest with `results`, at most 5 per
    request;
  - `siteAudits(siteKey, language)`: `pages` of the readable pages, most issues first, and
    `pageCount`, the readable `jnt:page` of the site, counted up to 10,000. Once per request.
- **Access** (`AuditAccess`), with the caller's session in `default`, checked before the store:
  - a signed-in user;
  - `jContentAccess` on the site;
  - the module enabled and the `audit` section on;
  - the language one of the site's;
  - for a page, a `jnt:page` or `jmix:mainResource` the caller can read.

  `siteAudits` resolves each stored identifier with the caller's session: a page they cannot read,
  or a deleted one, is left out. Every refusal answers `Permission denied`.
- **Drawer:**
  - On opening (`runId` 0), the section asks `pageAudits` before mounting the frame. The newest
    stored audit replaces the local cache when it is more recent and holds every switched-on tab
    (`keepEnabled`); without one, the frame measures the page.
  - A complete fresh audit is cached locally and sent to `saveAudit`. The answer (`auditedBy`, the
    summary) heads the history.
  - `AuditTrend` draws the totals oldest first.
  - A failed read or save logs a warning, and the section works as before shared audits.
- **Publish menu:** `publish/PublishCheckAction.jsx`, action `contentGovernancePublishCheck` on
  `publishMenu:-1` (above jContent's own items, which stay untouched).
  - It shows for the page in view only (`state.jcontent.path`), on a `jnt:page` or
    `jmix:mainResource` of a site that enables the module.
  - It reads `pageAudits` (summary only: `total`, `seriousAccessibility`, the critical and serious
    violations counted by `AuditSummary`) and, with `jcr:write`, `complianceScan.scanPage` (with the
    Page Builder frame's rendered paths, as the drawer does).
  - The label (`publishCheck.*`) depends on the sections the site keeps on; nothing shows when
    nothing was found.
  - jContent writes action labels as HTML (`dangerouslySetInnerHTML`), so `buttonLabelParams` holds
    numbers only.
  - The click dispatches `content-governance:open` (`OPEN_DRAWER_EVENT`); `GovernanceAction` opens
    the drawer on that section for its page.
- **Dashboard:** `audit/dashboard/AuditDashboard.jsx`, jContent app `contentGovernancePageQuality`
  (`jcontent:89`, `jContentAccess`, `SectionGate` on `audit`, lazy).
  - Moonstone `DataTable`: page, last audit, issues by tab as chips (tabs with issues only), total
    with a trend chip, **Open**.
  - A page with issues can be expanded (`isStructured`, `expandedRows` controlled: rows change once
    their findings arrive). Opening a row the first time asks `pageAudits` for that page
    (`pageFindings.js`): the newest run's `results` go through `auditFindings`, the drawer's own
    list, and `rankFindings`, most serious first. Each finding is a sub-row: title and `detail`
    (axe-core's rule description, or the recommendation's `.detail` text), severity, tab, and
    `count` (failing elements) for accessibility. Until then one sub-row says the findings are being
    read; a failed read says so and the next opening tries again; an audit whose results were not
    stored says to run it again. The list is never loaded for the whole site at once.
  - Pagination 10/20/50.
  - **Export CSV** (one row per page, a column per tab) and **Export JSON**, both built in the
    browser (`exportAudits.js`, `common/exportFile.js`).

## Feature gates

- `drawer/ownTabs.js` registers the ids: section `audit` with tabs `accessibility`, `seo`, `vitals`,
  `readability`, `ecodesign`, `links`, `jahia`. Ids are stored and exposed in GraphQL: never
  rename one.
- `SiteFeatures.isEnabled(sitePath, "audit.<tab>")` is true when the site enables the module
  (directly or through a dependency) and neither `audit` nor `audit.<tab>` is in the site's
  `disabledFeatures`, stored on `/sites/<key>/content-governance` (`nt:unstructured`,
  `jmix:nolive`, default workspace), read with a system session. A site never configured has
  everything on, and so does a tab added in a later version.

## Known leftovers

Facts from the merge that a maintainer may trip on:

- Web Vitals runs before axe is injected and before the link checks, or their requests would count
  in the page's own statistics; `webVitals.js` also filters `/modules/content-governance/`.
