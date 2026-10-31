# Page quality: architecture

How the **Page quality** section of the Content Governance drawer works, for developers who
maintain it. The user-facing behaviour is in [guide.md](guide.md).

## In one paragraph

The automated checks run in the editor's browser. The section loads
`/cms/render/default/{lang}{path}.html` in a same-origin iframe, waits for it to settle, strips
editing tooling from its DOM, and runs seven analyzers against the frame's document and performance
entries. One of them (Jahia) also queries GraphQL through jContent's Apollo client. Results are kept
in the editor's `localStorage` and, once complete, stored on the site for every editor (see
[Shared audits](#shared-audits-and-the-site-dashboard)). The other server code specific to the section is one servlet,
`/modules/content-governance/audit/ai-review`, which runs four server-defined AI tasks on the
module's shared model connection (`org.jahia.community.contentgovernance.ai`).

```
jContent / Page Builder header
  GovernanceAction (headerPrimaryActions:890)
    GovernanceDrawer  (portal into document.body)
      AuditSection
        iframe  /cms/render/default/{lang}{path}.html?pageAuditRun=N
          removeToolingElements -> webVitals -> ecodesign -> readability -> seo
          -> accessibility (axe-core injected) -> links (HEAD/GET) -> jahiaHealth (GraphQL)
        localStorage  content-governance:audit:{lang}:{path}
        POST /modules/content-governance/audit/ai-review  {task: review|seo|alt|simplify}
          AiReviewServlet -> LlmConfigService -> LlmProvider -> Anthropic / OpenAI / DeepSeek
```

## Where the code lives

| Path | Role |
|---|---|
| `src/javascript/init.js` | Loads the editor's language file (only that one: `en` or `fr`, English otherwise), then registers the `contentGovernance` action on `headerPrimaryActions:890`, label `drawer.action` ("Content governance"), and the routes. Everything else loads on first use: the GEO and Compliance dashboards and the settings panel through `React.lazy` with a Moonstone loader, the drawer from `GovernanceAction`. jContent's start loads about 105 kB of the module (622 kB up to 1.4.0); the drawer spec fails above 200 kB. |
| `src/javascript/drawer/GovernanceAction.jsx` | Shows the button on `jnt:page` and `jmix:mainResource` of a site that enables the module (`useNodeChecks`, `requireModuleInstalledOnSite`), reads the site settings, opens the drawer. The drawer and its three sections are one lazy chunk, fetched when the browser is idle once the button shows (`requestIdleCallback`, else after 2 s), at the latest on the first click. |
| `src/javascript/drawer/GovernanceDrawer.jsx` | The drawer: header, one section tab per switched-on section with its count badge, sections mounted on first show and kept mounted while the drawer is open. |
| `src/javascript/audit/AuditSection.jsx` | The section: preview frame, analyzer pipeline, cache, staleness probe, export, AI task lifecycle, tab bar and badges. |
| `src/javascript/audit/analyzers/` | One file per analyzer, plus `tooling.js` (DOM cleanup) and `aiReview.js` (AI client). |
| `src/javascript/audit/tabs/` | One component per tab, the three assist components, the shared `Recommendations` list, the two manual checklists. |
| `src/javascript/common/useSiteSettings.js`, `settings.gql.js` | Site switches and AI status, from `contentGovernance.siteSettings`. |
| `src/javascript/common/AiUnavailable.jsx` | The greyed-out box shown in place of an AI feature when no model is configured. |
| `src/main/java/org/jahia/community/contentgovernance/audit/servlet/AiReviewServlet.java` | The AI endpoint. |
| `src/main/java/org/jahia/community/contentgovernance/ai/` | Shared model connection and providers. |
| `src/main/java/org/jahia/community/contentgovernance/settings/Features.java`, `SiteFeatures.java` | Feature ids and the server-side gate. |
| `src/main/resources/javascript/locales/{en,fr}.json` | Labels, under `content-governance.audit`, `content-governance.drawer` and `content-governance.common`. |
| `webpack.config.js` | Copies `node_modules/axe-core/axe.min.js` to `javascript/apps/axe.min.js`. |

## Opening and gating

- `GovernanceAction` resolves the path from its props or jContent's Redux state (`state.jcontent.path`),
  and the language from its props or `state.language`. The drawer has no language picker: it audits
  the language jContent is in.
- `useSiteSettings(siteKey)` runs `ContentGovernanceSiteSettings` with `fetchPolicy: 'network-only'`
  on every mount, so a switch changed by an administrator applies the next time the drawer opens. It
  returns `isOn(section, tab)` and `settings.ai` (`configured`, `provider`, `model`, `images`).
- The drawer is a `<dialog open>` (not `showModal()`, which would leave jContent's stacking context)
  rendered by a portal into `document.body`. It traps Tab, closes on Escape, on a pointer press
  outside, and when focus moves into an iframe outside it (the Page Builder frame). The preview frame
  of this section is inside the dialog, so clicking it does not close the drawer.
- `AuditSection` receives `isOn(tab)` for the `audit` section. `TAB_IDS` (`accessibility`, `seo`,
  `vitals`, `readability`, `ecodesign`, `links`, `jahia`, `ai`) is filtered by it. The `ai` tab is
  kept but disabled while `settings.ai.configured` is false; its tooltip (Moonstone `Tooltip`,
  `common/HelpTab.jsx`) says what it is for, then `audit.tabs.aiNotConfigured`.

A tab switched off is not measured. Its analyzer does not run, and its result is not cached,
exported or sent to the AI review (`keepEnabled` in `AuditSection.jsx`). Web Vitals still runs as the
input of Ecodesign, and the SEO read (a DOM pass) always runs for the page facts the AI tasks need:
`results.page` holds the title, the meta description and the number of images without alt, whatever
the switches. Neither result is kept when its own tab is off. A cached audit that lacks a tab switched
back on since is measured again; `CACHE_SCHEMA` 4 dropped caches from before `results.page`, 5 those from before per-language readability.
The analyzers call no module endpoint; the server enforces the switches on the AI endpoint (see
[Feature gates](#feature-gates)).

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
  3. runs the analyzers in order. The four network-bound ones go through `runStep`
     (`analyzers/timeLimit.js`) with `STEP_LIMITS_MS`: vitals 15 s, accessibility 60 s, links 45 s,
     Jahia 30 s. A step that fails or runs out of time is left out: its tab shows `IncompleteStep`
     (why, and **Re-run**) from `results.incomplete[<result key>]`, eco-design follows vitals, the
     other tabs show their results, and such a partial audit is never cached. The late answer of a
     stopped step is ignored, not cancelled.
- When results come from the cache, the frame still loads (preview and highlight target) but the
  analyzers do not run (`cacheHitRef`).
- Highlight: `highlight(selector)` uses `querySelector`; `highlightText(sample)` normalises
  whitespace, quotes and case, searches at element level, falls back to the first 8 then 4 words,
  and outlines the tightest matching element. Both reopen a collapsed preview.

## The analyzers

Run in this order in `handleFrameLoad`. Each returns a result object with a `recommendations` array
of `{key, severity, params}`; the text is in `audit.<ns>.recs.<key>.title|detail`.

| Analyzer | File | Reads | Notes |
|---|---|---|---|
| Web Vitals | `webVitals.js` | Buffered `PerformanceObserver` (`largest-contentful-paint`, `layout-shift`, 700 ms window), navigation and resource entries, `doc.images` | Runs first so axe and link checks are not in the resource stats. LCP falls back to the `responseEnd` of the largest in-viewport image, else `domContentLoadedEventEnd` (`lcpApprox`). INP is always `null`. Thresholds: LCP 2500/4000 ms, CLS 0.1/0.25, TTFB 800/1800 ms. Size is `transferSize`, else `encodedBodySize` (HTTP cache hits). |
| Ecodesign | `ecodesign.js` | Vitals diagnostics, resource entries, DOM | 10 criteria: weight 1 MB / 2.5 MB, requests 40 / 70, DOM 1000 / 1500, below-the-fold images without `loading="lazy"`, fonts (max 2, WOFF2 only), autoplay media, legacy-format images over 30 kB, oversized images, missing dimensions, third-party origins (max 5). Not an RGESN conformity score; `ecoChecklist.js` lists 8 whole-service criteria. |
| Readability | `readability.js` | `main` or `body`, minus `script, style, noscript, svg, nav, header, footer, [aria-hidden="true"]` | Text nodes joined with spaces. `analyzeText` scores with the formula of the language (`FORMULAS`: Flesch and Flesch-Kincaid grade `en`, Kandel-Moles `fr`, Amstad `de`, Fernández Huerta with Law's correction `es`, Franchina-Vacca `it`, Douma `nl`, Martins `pt`), syllables by vowel groups per language (diaeresis hiatus in `fr`/`nl`, silent final e in `en`/`fr`). Another language: `scored: false`, `score: null`, no score advice. `ja zh th lo km my` (`UNSPACED`): `spaced: false`, characters and sentences (split on `。．！？!?`), no word-based field or advice. Under 30 words: `empty`. Long sentence: over 25 words. H1 count on the whole document, heading skips in the root. |
| SEO | `seo.js` | `<head>` tags, JSON-LD blocks, `<html lang>`, images, links and buttons | Strips the preview prefix ("Aperçu - ", "Preview - "...) from the title. Title 30-60, description 50-160. `collectWeakCtas` keeps up to 10 distinct generic labels with their target and 300 characters of context, for the SEO assist. |
| Accessibility | `accessibility.js` | Whole frame document | Injects axe-core (below) and runs tags `wcag2a, wcag21a, wcag2aa, wcag21aa, wcag22aa, wcag2aaa, best-practice` by tag, not by rule list, so an axe-core update brings its new rules. Keeps 25 nodes per rule. A card's percentage is passes / (passes + violations), floored; `incomplete` is outside the ratio. `a11yChecklist.js` lists 14 manual criteria. |
| Links | `links.js` | `a[href]` | Same-origin links whose path starts with `/cms/render/`, `/cms/file/` or `/files/` are fetched with `credentials: 'same-origin'`: `HEAD`, then `GET` on 405/501, batches of 6, at most 60 distinct URLs. URLs matching `logout|login|\.do` and every other same-origin path are counted (`skippedSideEffect`) and never fetched, so a planted link cannot make the auditor's session call an action. External links are only counted. |
| Jahia | `jahiaHealth.js` | GraphQL `pageAuditHealth`, DOM text nodes | `descendants` of type `jnt:content`, recursion stopped at `jnt:page`, `limit: 400`; publication status per node and `translationLanguages` against the site languages `activeInEdit`. DOM scan for raw keys (`namespace:key.path`) and placeholders. A GraphQL failure adds `graphqlUnavailable` (minor) instead of failing the audit. |

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

- `pageAuditHealth(path, language)`: the Jahia tab.
- `pageAuditLastModified(path, language)`: `aggregatedLastModifiedDate` of the page and its content,
  stopping at sub-pages. Used as the staleness probe when results come from the cache: if it is later
  than the cached timestamp, the **Page modified since this audit** banner is shown. It compares a
  server date with a browser timestamp.

## Results, badges, cache and export

- Results: `{a11y, vitals, ecodesign, readability, seo, links, jahia}`. AI answers are kept beside
  them (`aiReview`, `seoAssist`, `altAssist`, `simplifyAssist`).
- Badges: `a11y.violations.length` for Accessibility, `recommendations.length` for the other
  analyzers, `aiReview.recommendations.length` for the AI tab. The section total, reported to the
  drawer through `onCount`, sums the badges of the switched-on tabs.
- Cache: `localStorage` key `content-governance:audit:{language}:{path}`, entry
  `{schema, timestamp, results, aiReview, seoAssist, altAssist, simplifyAssist}`. `CACHE_SCHEMA` is 5:
  bump it whenever the shape of `results` changes, or an old entry is restored into a UI expecting the
  new shape (entries with another schema are deleted on read). Pruned to the 10 most recent entries;
  on a quota error, pruned to 2 and retried once; failures are silent. Every AI result is persisted
  through `persistAi`, which rewrites all four AI fields, so saving one never drops the others.
- **Re-run** (`runId > 0`) skips the cache and resets every AI result.
- **Shared audits:** see the next section.
- **Export JSON** builds `{path, language, url, auditedAt, results}` in the browser and downloads
  `content-governance-audit-<last path segment>.json`. AI answers are not exported.

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
  - Pagination 10/20/50.
  - **Export CSV** (one row per page, a column per tab) and **Export JSON**, both built in the
    browser (`exportAudits.js`, `common/exportFile.js`).

## The AI endpoint

`AiReviewServlet`, a DS component registered as an `HttpServlet` with `alias=/content-governance/audit/ai-review`,
so it answers at `/modules/content-governance/audit/ai-review`.

The endpoint answers `POST` only (405 otherwise). The drawer reads the provider, model and image
support from `contentGovernance.siteSettings.ai`.

### POST: guards, in order

| Check | Failure |
|---|---|
| Authenticated, not `guest` | 403 `unauthenticated` |
| `Content-Type` starts with `application/json` | 415 `invalidRequest` |
| `Origin`, else `Referer`, has the request's host (`Host` header without port, else `getServerName()`); a request with neither header passes, as a non-browser caller | 403 `crossOrigin` |
| Body is at most 4 194 304 characters | 413 `invalidRequest` |
| Body parses as JSON | 400 `invalidRequest` |
| `path` is absolute, exists and has `jcr:read` for the caller's session | 403 `noAccess` |
| The site of the page enables the feature of the task (`SiteFeatures.isEnabled`) | 403 `switchedOff` |
| `LlmConfigService.isEnabled()` | 503 `notConfigured` |
| Per-user rate limit: `AI_RATE_MAX_CALLS` per 10-minute sliding window | 429 `rateLimited` |
| Provider call: the output budget spent before any answer | 502 `budget` |
| Provider call and parse | 502 `upstream`; detail and the first 500 characters of an unparseable answer are logged server-side only |

Page tasks ask the provider for a JSON answer (`LlmSettings.withJsonAnswer`): OpenAI-compatible
providers add `response_format: json_object`, and `reasoning_effort: low` on OpenAI's reasoning
models, and send the request again without them when the API answers 400 or 422. An answer the
parser cannot read is asked for once more with a correction appended to the user message; both
calls' tokens are added up.

Every refusal is JSON, `{"code": "...", "error": "<English message>"}`. The drawer shows the code in
the editor's language (`audit.ai.errors.<code>`) and falls back to the message. The rate limit is
counted after every refusal that costs nothing, so only a call that goes to the provider takes a
slot (a provider failure included); it is kept in memory per node.

The endpoint is under Jahia's CSRF guard, like every servlet of the module (see
[the GEO architecture](../geo-readiness/architecture.md), "Jahia's CSRF guard"): the drawer sends the
guard's token. The JSON content type and the Origin/Referer host check stay as a second check.

### Tasks

The client sends `task` and data; the server owns the prompts. An unknown task runs as `review`.

| Task | Feature gate | Client sends | Server caps | Answer (whitelisted) |
|---|---|---|---|---|
| `review` (default) | `audit.ai` | `language`, `uiLanguage`, `path`, `title`, `description`, `findings` (digest from `buildDigest`), `text` | text 15,000 chars, digest 60 lines, 20 recommendations kept | `summary`, `recommendations[{severity, category, title, detail, wording, fix}]` |
| `seo` | `audit.seo` | the above plus `headings` (h1-h3, max 20) and `ctas` (weak CTAs) | 3 titles, 3 descriptions, 8 secondary keywords, 8 headings, 10 CTAs of 3 suggestions | `titles`, `metaDescriptions`, `social{title, description}`, `keywords{focus, secondary}`, `headings[{level, current, suggested, reason}]`, `ctas[{current, suggestions, reason}]` |
| `alt` | `audit.accessibility` | `images[]`: selector, filename, size, link target, caption, nearest heading, context, and a JPEG thumbnail (max 512 px, quality 0.7) only when `settings.ai.images` is true and the canvas is not tainted | 8 images; a picture is attached only if the provider reads images, the type is JPEG, PNG or WebP, the base64 is valid and at most 400,000 chars | `images[{id, decorative, alt, reason}]`, `vision` (true when at least one picture was attached) |
| `simplify` | `audit.readability` | `sentences[{id, text, words}]`: the longest sentences of 22 words or more in `p, li, td, blockquote, dd` of the main content, outside nav, header, footer | 8 sentences, 1,500 chars each (the client sends 6) | `sentences[{id, rewrite, reason}]` |

- Prompts: `SYSTEM_PROMPT`, `SEO_ASSIST_PROMPT`, `ALT_TEXT_PROMPT`, `SIMPLIFY_PROMPT`. Each requires
  a JSON-only answer of a fixed shape. `LlmConfigService.withAppendix` adds `AI_PROMPT_APPENDIX`
  after every prompt.
- Languages: `language` is the audited page language, `uiLanguage` is `contextJsParameters.uilang`.
  Both are turned into a language name (French, German, Spanish, Italian, Portuguese, Dutch, English;
  any other code is passed as is). Content to publish (fixes, titles, alt texts, rewrites) is written
  in the page language; prose for the editor (summary, details, reasons) in the UI language.
- Parsing: the answer is read from its first `{`. `parseLenient` first tries up to the last `}`;
  if that fails (an answer cut by the output budget), it cuts back to the last complete `},` and
  closes `]}`, so complete items survive. Each task has its own parser that copies only the expected
  fields, normalises enums (severity, category, heading level, with a fallback), caps array lengths
  and clips suggestions and reasons to 300 characters (rewrites to 1,500; the review's title, detail
  and wording are not clipped). Nothing else from the model reaches the browser, and the tabs render
  it as React text (no `innerHTML`).
- Response extras: `provider`, `model`, `truncated` (the provider reported the output limit),
  `usage{inputTokens, outputTokens}` and, when `AI_COST_INPUT_PER_MTOKENS` or
  `AI_COST_OUTPUT_PER_MTOKENS` is set, `usage.cost` in USD (rounded to 4 decimals).
- Every task is started by a click (`runAiTask`), never automatically, to control cost.

## The shared model connection

Package `org.jahia.community.contentgovernance.ai`, used by this servlet and by the GEO site report
(`GeoReportServlet`, `GeoReport`), and read by the settings GraphQL types for the status.

| Class | Role |
|---|---|
| `LlmConfigService` | DS component, PID `org.jahia.community.contentgovernance.llm`. Holds one immutable `Snapshot` in a volatile field, replaced by `@Activate`/`@Modified`, so an edit of the `.cfg` applies at once. `isEnabled()` is true when `AI_PROVIDER` names a known provider and `AI_MODEL` and `AI_API_KEY` are set. Provider name and model are empty when disabled; the key is never exposed. |
| `Providers` | Looks up `anthropic`, `openai`, `deepseek` by name. |
| `LlmProvider` | `name()`, `defaultBaseUrl()`, `supportsImages()` (default false), `complete(system, user, images, settings)`. Implementations are stateless. |
| `AnthropicProvider` | `POST {base}/v1/messages`, `x-api-key`, `max_tokens`; reads images (base64 `image` parts, each after an `IMAGE #id:` text part); truncated when `stop_reason` is `max_tokens`. |
| `OpenAiCompatibleProvider` | `POST {base}/v1/chat/completions`, Bearer token; images as `image_url` data URLs when the subclass reads images; truncated when `finish_reason` is `length`; an empty answer with `reasoning_content` raises an explicit error. |
| `OpenAiProvider` | `max_completion_tokens`, reads images. |
| `DeepSeekProvider` | `max_tokens`, text only, and `thinking: {type: disabled}`: V4 models otherwise spend the budget on hidden reasoning and drift from the JSON-only instruction. |
| `HttpJson` | JDK `HttpClient`, connect timeout 10 s, request timeout 150 s, no redirects; a non-2xx status becomes an `IOException` with the status and 400 characters of the body. Endpoints come from configuration only. |
| `LlmSettings`, `Completion`, `LlmImage` | Per-call settings (its `toString` masks the key), answer (text, truncated, token counts or -1), picture. |

Configuration, in `digital-factory-data/karaf/etc/org.jahia.community.contentgovernance.llm.cfg`
(shipped empty, first line `# default configuration - won't be overridden`, so a redeploy keeps the
operator's values):

| Key | Default | Use |
|---|---|---|
| `AI_PROVIDER` | empty | `anthropic`, `openai` or `deepseek` |
| `AI_MODEL` | empty | the provider's model id |
| `AI_API_KEY` | empty | server-side only |
| `AI_BASE_URL` | the provider's API | a proxy or compatible gateway; trailing slashes removed |
| `AI_MAX_TOKENS` | 6000 (min 256) | output budget of a page task; reasoning models spend part of it before answering |
| `AI_RATE_MAX_CALLS` | 30 (min 1) | page tasks per user per 10 minutes |
| `AI_COST_INPUT_PER_MTOKENS`, `AI_COST_OUTPUT_PER_MTOKENS` | empty | USD per million tokens; no cost shown when both are empty |
| `AI_PROMPT_APPENDIX` | empty | instructions appended to every prompt, of every site |
| `AI_REPORT_MAX_TOKENS`, `AI_REPORT_RATE_MAX_CALLS` | 6000, 5 | GEO site report only |

The rate-limit windows are kept in memory per servlet instance: they reset on restart and are not
shared across cluster nodes.

## Feature gates

- `Features.SECTIONS` declares the ids: section `audit` with tabs `accessibility`, `seo`, `vitals`,
  `readability`, `ecodesign`, `links`, `jahia`, `ai`. Ids are stored and exposed in GraphQL: never
  rename one.
- `SiteFeatures.isEnabled(sitePath, "audit.<tab>")` is true when the site enables the module
  (directly or through a dependency) and neither `audit` nor `audit.<tab>` is in the site's
  `disabledFeatures`, stored on `/sites/<key>/content-governance` (`nt:unstructured`,
  `jmix:nolive`, default workspace), read with a system session. A site never configured has
  everything on, and so does a tab added in a later version.
- Task to feature: `review` to `audit.ai`, `seo` to `audit.seo`, `alt` to `audit.accessibility`,
  `simplify` to `audit.readability`. Switching off the AI tab therefore leaves the three assists
  working.

## Known leftovers

Facts from the merge that a maintainer may trip on:

- Web Vitals runs before axe is injected and before the link checks, or their requests would count
  in the page's own statistics; `webVitals.js` also filters `/modules/content-governance/`.
