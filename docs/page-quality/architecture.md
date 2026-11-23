# Page quality: architecture

How the **Page quality** section of the Content Governance drawer works, for developers who
maintain it. The user-facing behaviour is in [guide.md](guide.md).

## In one paragraph

The checks of SEO, readability, links and Jahia health run on the server: the GraphQL field
`checkPage` renders the page as the editor, in the edit workspace, runs every check of the server on
it, and gives the result of each rule that ran, with the values that these tabs show. `frame.check`
gives an agent the same findings for the same path. The checks that need a browser run in the editor's browser: the section loads
`/cms/render/default/{lang}{path}.html` in a same-origin iframe, waits for it to settle, strips
editing tooling from its DOM, and runs each check of the registry type `contentGovernanceCheck`
against the frame's document and performance entries. axe-core, the web vitals and eco-design are
such checks. Results are kept
in the editor's `localStorage` and, once complete, stored on the site for every editor (see
[Shared audits](#shared-audits-and-the-site-dashboard)). The section calls no model. When the
module ai-assistant is installed, it adds its own AI review tab to the drawer
([`drawer-entries.md`](../drawer-entries.md)).

```
jContent / Page Builder header
  GovernanceAction (headerPrimaryActions:890, one button per panel)
    GovernanceDrawer  (portal into document.body)
      GovernancePanel
        AuditSection
          GraphQL checkPage (HtmlCheck)  -> one result per rule: seo, readability, links, jahia, HTML rules
          iframe  /cms/render/default/{lang}{path}.html?pageAuditRun=N
            removeToolingElements -> runChecks: webVitals, ecodesign.*, axe (axe-core injected), checks of other modules
          localStorage  content-governance:audit:{lang}:{path}
```

## Where the code lives

| Path | Role |
|---|---|
| `src/javascript/init.js` | Loads the editor's language file (only that one: `en` or `fr`, English otherwise), then registers the `contentGovernance` action on `headerPrimaryActions:890`, label `drawer.action` ("Content governance"), and the routes. Everything else loads on first use: the GEO and Compliance dashboards and the settings panel through `React.lazy` with a Moonstone loader, the drawer from `GovernanceAction`. jContent's start loads about 105 kB of the module (622 kB up to 1.4.0); the drawer spec fails above 200 kB. |
| `src/javascript/drawer/GovernanceAction.jsx` | Shows one button per panel of the registry (`contentGovernancePanel`, `drawer/entries.js`) on a site that enables the module (`useNodeChecks`, `requireModuleInstalledOnSite`) and keeps the panel on. A panel shows on `jnt:page` and `jmix:mainResource` unless its entry names other node types. It reads the site settings, holds which panel the one drawer shows (`drawer/drawerState.js`), and listens to `openPanel` (`drawer/drawerApi.js`). The button of the panel in view is pressed (`aria-pressed`, accent color). The drawer is one lazy chunk, fetched when the browser is idle once a button shows (`requestIdleCallback`, else after 2 s), at the latest on the first click. The governance panel and its sections load on first show. |
| `src/javascript/drawer/FixFirst.jsx`, `findings.js` | "Fix first": each section reports `{key, section, tab, severity, title}` findings through `onFindings` (Page quality: violations by impact and every tab's recommendations, `auditFindings`; GEO: one per tab with a count, score and crawler serious, the others moderate; Compliance: one serious per term, `complianceFindings`). The drawer keeps them per section (an identical list changes nothing), `rankFindings` keeps the five most serious, ties in section order, and **Open** selects the section and passes `requestedTab` `{tab, at}`. |
| `src/javascript/drawer/GovernanceDrawer.jsx` | The drawer: header with the label of the panel in view, panels mounted on first show and kept mounted while the drawer is open. The drawer has no switch between the panels: the header buttons switch them. |
| `src/javascript/drawer/GovernancePanel.jsx` | The governance panel: one section tab per switched-on section with its count badge, in the main tab bar of the drawer, in Moonstone's default size, "Fix first", sections mounted on first show and kept mounted while the drawer is open. |
| `src/javascript/drawer/useDock.js` | Docks the drawer beside the page under edit, under jContent's header, and resizes it from its left edge (see the drawer notes below). |
| `src/javascript/audit/AuditSection.jsx` | The section. It holds the check of the server, the preview frame and the run of the checks of the browser. It also holds the cache, the staleness probe, the export, and one collapsible section per subdomain with its count. |
| `src/javascript/common/CollapsibleSection.jsx`, `openSections.js` | The subdomains of Page quality and GEO readiness: one section each, with its count left of its title in capitals, its help as tooltip, and its content mounted on first open. A section with a critical or serious finding, or whose step did not finish, opens by default. The editor's choice holds until new results (`resetKey`: `auditedAt`, `ranAt`), when the defaults apply again. **Open** in "Fix first" opens the section of `requestedTab` and scrolls to it. |
| `src/javascript/audit/pageCheck.js` | Calls `checkPage`, and turns the findings and the values of its results into the results of the tabs SEO, Readability, Links and Jahia health, and the HTML rules of the Accessibility tab. |
| `src/javascript/audit/runChecks.js` | Runs the checks of the browser on the preview, one after the other, each with its time limit. It gives the results, the checks that did not finish, and the rules that no check gave a result for. |
| `src/javascript/audit/browserResults.js` | Turns the results of the checks of the browser into what each tab shows. These are the recommendations by subdomain, the view of axe-core, the figures of the web vitals and the criteria of eco-design. |
| `src/javascript/audit/tooling.js`, `timeLimit.js` | The cleanup of the editing tooling in the DOM, and the time limit of a step (`runStep`). |
| `src/javascript/checks/` | The checks of the browser of Content Governance. `register.js` adds them to the registry, as another module does. The checks are `webVitals.js`, `ecodesign.js` with one evaluator per measure, and `axe.js`. `pageMeasure.js` measures the page for the first two, and `cssPath.js` gives the selector that a highlight finds an element by. |
| `src/main/java/org/jahia/community/contentgovernance/page/` | The checks of the server for each domain (`SeoChecks`, `ReadabilityChecks`, `LinkChecks`, `HealthChecks`), with the values each one measures, and the checks of the measure rules `seo.tags`, `readability.statistics` and `readability.headings`. |
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
  returns `isOn(section, tab)`, off with the governance panel too, and `isPanelOn(panel)`.
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

A tab switched off is not shown. Its result is not cached or exported (`keepEnabled` in
`AuditSection.jsx`). A check of the browser runs while the site keeps a rule of its evaluator on,
whatever tab shows the rule. The check of the server runs while a tab that reads it is on, Accessibility included, and gives the
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
     before any check reads the DOM;
  3. starts the check of the server, then runs the checks of the browser one after the other. Each
     step goes through `runStep` (`timeLimit.js`): the check of the server has 60 s
     (`PAGE_LIMIT_MS`), and each check of the browser has 60 s (`CHECK_LIMIT_MS`). A step that fails
     or runs out of time is left out. Its sections open and show `IncompleteStep` (why, and **Re-run**) from
     `results.incomplete[<result key>]`: the check of the server for SEO, Readability, Links and
     Jahia health, and a check of the browser for the tab of the subdomain of each of its rules. The other tabs
     show their results, and such a partial audit is never cached. The late answer of a stopped step
     is ignored, and a check of the browser gets an aborted `signal`.
- When results come from the cache, the frame still loads (preview and highlight target) but the
  checks do not run (`cacheHitRef`).
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
  `inert`, `aria-hidden`), because the checks read the frame's viewport and paint (LCP, images
  above the fold) and a frame with no height measures nothing. A re-run keeps the editor's choice.

## The check of the server

`audit/pageCheck.js` calls the GraphQL field `checkPage(path, language)` (`ContentGovernanceCheckPage`,
`fetchPolicy: 'no-cache'`). The server renders the page as the editor, in the edit workspace, with
`HtmlCheck`, the service that `frame.check` and `audit.accessibility` call too. It answers `results`:
one result per rule that ran, passed or failed, in the order of the rules,
`{ruleId, passed, values, findings}`. A rule that the site turned off gives no result.

- `findings`: each place where the page breaks the rule, `{ruleId, severity, text, context, locator,
  values}`, at most 50.
- `values`: a JSON object of what the check of the rule measured. Example: `seo.title` gives
  `{length: 92, text: "Annual report…"}`.

Each block of a tab reads the values of the rule that feeds it, and is left out when the site turned
that rule off:

| Rule | Tab | Values |
|---|---|---|
| `seo.tags` (measure) | SEO | The tags of the head and the images: the tag list and the sharing preview. |
| `readability.statistics` (measure) | Readability | The statistics of the main text with the formula of its language (`Readability`), and its paragraphs. |
| `readability.headings` (measure) | Readability | The outline of the headings, each with a CSS selector of the server render, which the preview resolves to highlight it. |
| `links.broken` | Links | The links to a page, the internal links resolved in the repository and the broken ones (`missing` or `not_published`). |
| `health.unpublishedChanges` | Jahia health | The status of the page, the content items read (400 at most), and what is not published in the language. |
| `health.missingTranslations` | Jahia health | The active languages of the site, and what lacks one of them. |

A measure rule has no limit: it always passes, and carries values only. Each other built-in rule
carries its values too, such as the score under `readability.flesch` or the Open Graph tags under
`seo.openGraph`, for what the drawer shows later.

Each finding goes to the tab of the subdomain of its rule on the site, when the drawer has one (`tabOf`,
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

## The checks of the browser

A check of the browser is an entry of the registry type `contentGovernanceCheck`, with an
`evaluator` and a `run` function. [`drawer-entries.md`](../drawer-entries.md#add-a-check-that-runs-in-the-browser)
states the contract. Content Governance registers its own checks in `checks/register.js`, and their
code loads the first time the drawer runs them.

`runChecks` gives each check the enabled rules of its evaluator that state `runtime: browser`.
`audit/rules.js` reads these rules at the start of the audit, through the GraphQL field
`contentRules(siteKey, language)` ([the catalog](../content-rules/catalog.md)). Each check returns one
result per rule: `{ruleId, passed, values, findings: [{text, context, locator, values}]}`, the shape of
the results of the server. A rule of the browser never runs on the server, and it never blocks.

`browserResults.js` turns the results into what the tabs show:

- A failed result becomes a recommendation of the tab of the subdomain of its rule, when the drawer
  has one (`tabOf`), worded by its rule. Its texts take the limits of the rule and the values of the
  result, such as `size`.
  They also take `count`, the number of its findings, and `value`, the text of its first finding.
- The results of the evaluator `axe` give the Accessibility tab its level cards, its violations and
  its checks to review.
- The result of the evaluator `webVitals` gives the Web Vitals tab its figures.
- Each result of the Ecodesign tab with a `family` in its values is a criterion of that tab.
- A rule that no check gave a result for shows under **Not checked** in the tab of its subdomain. The
  result of the tab keeps such rules in `notChecked`.

The built-in checks:

| Check | Evaluator | File | What it does |
|---|---|---|---|
| Web Vitals | `webVitals` | `webVitals.js` | It applies the measure rule `ecodesign.webVitals`, which always passes. Its values hold the figures of the Web Vitals tab. It reads the buffered `PerformanceObserver` entries (`largest-contentful-paint`, `layout-shift`, 700 ms window) and the measure of the page. LCP falls back to the `responseEnd` of the largest in-viewport image, else `domContentLoadedEventEnd` (`lcpApprox`). INP is always `null`. The timings are indicative figures and never a finding, because the editing preview in the editor's browser cannot match the live site. `audit/vitalsFindings.js` drops the `slowLcp`, `highCls` and `slowTtfb` findings of audits stored before 1.11 where the dashboard shows them (`auditFindings`). |
| Eco-design | `ecodesign.<measure>` | `ecodesign.js` | There are 11 checks, one per measure. They measure the weight, the requests, the DOM size and the fonts, which must be WOFF2. They also count the heavy scripts and stylesheets, the autoplay media and the third-party origins. The last four count images: below the fold without `loading="lazy"`, in a legacy format over 30 kB, oversized, or with no dimensions. The rule gives the limit, in its `maximum`. A measure of elements passes with no element, and gives one finding per element, with its locator. The result is not an RGESN conformity score. `ecoChecklist.js` lists 8 whole-service criteria. |
| Accessibility | `axe` | `axe.js` | It injects axe-core (below) and runs the checks of the enabled rules `axe.<id>`, by rule. A rule whose check the version of axe-core lacks gives no result. A check that fails on one element fails, and one with an element to review passes with the outcome `incomplete`. The result keeps 25 elements. A card's percentage is passes / (passes + violations), floored, and `incomplete` is outside the ratio. `a11yChecklist.js` lists 14 manual criteria. The tab also lists the HTML rules of the server that no other tab words. |

`checks/pageMeasure.js` measures the page once per document for the web vitals and eco-design.
`runChecks` sets the performance mark `contentGovernanceChecks` before the first check. The measure
leaves out each resource after that mark, so the script of axe-core is not in the statistics. The size of a resource is
`transferSize`, else `encodedBodySize` (HTTP cache hits).

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
  serious and grey (`Chip`) otherwise, on the subdomain sections of every section, a grey 0 included,
  and on the section tabs, where the drawer reads the severities from the findings the section
  reports. A subdomain with no count (its step did not finish, or AI citations without BotRank) shows
  a dash. The section total, reported through `onCount`, sums the counts of the switched-on
  subdomains. "Fix first" lists critical and serious findings only (`isSerious`,
  `drawer/findings.js`) and shows nothing without one. GEO readiness: Score and Crawler access are
  serious, the other subdomains grey.
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
  - The click calls `openPanel('governance', {section}, path)` (`drawer/drawerApi.js`);
    `GovernanceAction` opens the drawer of its page on the governance panel and that section.
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

- [The checks of the browser](#the-checks-of-the-browser) states which resources the measure of the
  page leaves out.
