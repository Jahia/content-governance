# Changelog

## 1.5.0

### New Features

* The drawer starts with **Fix first**: the most serious finding of the page on one line, with how many more there are, and on Show the five most serious whatever their section (accessibility violations, the recommendations of every Page quality tab, GEO readiness tabs with something to review, forbidden terms), each with Open to go to the section and tab that explains it. GEO readiness and Compliance join the list once they have been opened.

* Content Governance speaks German and Spanish: the drawer, the dashboards, the Publish menu line and Site Settings follow the editor's interface language, as do the content type labels and the example glossary created when a site enables the module. jContent itself has no Spanish interface, so a Spanish editor sees jContent in English and Content Governance in Spanish. The German and Spanish texts were machine-drafted and await a native review.

* Page quality audits are shared by the editors of a site. A complete audit is stored for its page and language, so anyone opening the drawer on that page sees the newest audit at once, with who ran it and when, instead of measuring the page again; the last 10 are kept and the drawer shows how the number of issues moved ("Issues over the last 10 audits: 12 → 9 → 7"). A new site dashboard, Additional > Page quality, lists the latest audit of every audited page in the current language, most issues first, with its issues by tab and its trend, Export CSV and Export JSON; anyone who may open jContent on the site sees it. Audits with a check that did not finish and AI answers are not shared.

* jContent's Publish menu now starts with a line from Content Governance when the page in view has something to review: "Review before publishing - audit issues: 12, serious accessibility: 2, forbidden terms: 1", from the page's latest audit and a compliance scan, or "Not audited yet" for a page nobody has audited. It opens the drawer on the section to review and never blocks publishing; nothing is added to a page without issues. Shared audits now also count their critical and serious accessibility violations.

* Swedish, Danish and Norwegian pages get a readability score: LIX (Björnsson), words per sentence plus the share of words longer than six letters, where a higher score reads harder. Bands follow Björnsson's table, the advice aims for a LIX of 40 or less, and the score card is coloured by band. Finnish stays without a score, LIX being untested there.

### Bug Fixes

* Page quality no longer stays on "Loading" or "Analyzing" when part of a page never answers. A page whose images, fonts or scripts keep loading is audited after 20 seconds as it stands, and a check that takes too long (Accessibility 60 seconds, Links 45, Jahia 30, Web Vitals 15) or fails is stopped: its tab says why with a Re-run button, and the other tabs show their results. jContent also starts faster: it now loads about 105 kB of the module instead of 622 kB, with only the editor's language; the drawer loads in the background once its button shows, and the dashboards and settings the first time they are opened.

* The GEO readiness dashboard no longer risks having a request refused by Jahia's CSRF guard when it loads: requests sent at once to the same module address now wait for the first one's answer instead of all using the session's master token, which the guard accepts once per address. The Cypress suite now runs every night against Jahia 8.2.3.2 in GitHub Actions.

* The help tooltip of a section or tab no longer stays open over the content after you click the tab: the selected tab shows none. Switching AI citations or the GEO readiness section off now pauses the site's daily BotRank sync instead of turning it off: it syncs again at its first run after the switch is back on, with nothing to save. A sync turned off that way by an earlier version is restarted by saving the BotRank settings once.

## 1.4.0

### New Features

* Page quality scores readability with the formula made for the page language: Amstad for German, Fernández Huerta for Spanish, Franchina-Vacca for Italian, Douma for Dutch and Martins for Portuguese, next to Flesch for English and Kandel-Moles for French. Until now these languages got the English formula, which scored them lower than they read. A page in another language gets no score instead of a misleading one, and the tab says why; sentence length, paragraphs and headings are still checked. Japanese, Chinese, Thai and other languages written without spaces between words get no word count either: the tab counts characters and sentences. Syllables are counted with each language's vowels, so accented letters (ñ, ã, ä...) no longer split words. Audits cached by an earlier version are measured again.

### Bug Fixes

* Page quality runs axe-core 4.14 (4.14.0 was already bundled in 1.3.0; it is now the declared minimum). The Accessibility tab now also checks that the visible text of a link or button is part of its accessible name (`label-content-name-mismatch`, on by default since 4.14) and reports a few more cases in existing rules, while some false positives are gone (colour contrast, `target-size` on overlapping links to the same page, captions in tables). A page can therefore show a different count than before without its markup having changed. Audits run faster on large pages. The Cypress suite checks that the audit runs the declared axe-core version and that its findings, the new rule included, reach the Accessibility tab.

* A Cypress suite (`tests/`, run against a running Jahia) covers the site settings, the compliance scan and its example glossary, the CSRF guard on the module's servlets and the drawer in jContent.

## 1.3.0

### Added

* GEO readiness dashboard: **Export CSV** (one row per finding of the last site scan) and
  **Export JSON** (the whole stored scan) in the header, like the Compliance dashboard.
* Enabling the module on a site that has no glossary creates **Content Folders > Compliance
  glossaries**, which only offers glossaries, with an example glossary in the site's default
  language and one example term. It is created once per site.

### Changed

* The module's servlets go through Jahia's CSRF guard, and the drawer, the dashboards and the
  settings send its token. They answer browser sessions only: **calls with an API token are no
  longer accepted.**
* Light tooltips (white, rounded, dark grey text) on the drawer's sections and tabs.
* Build and test tooling: webpack-cli 7, babel-loader 10, style-loader 4, clean-webpack-plugin 4,
  rimraf 6, JUnit 6, Mockito 5.24, frontend-maven-plugin 2.0.2.

### Fixed

* French labels of the content types (Compliance glossary, Non-authorised term) showed garbled
  accents in jContent.
* GEO readiness dashboard: uses the full width of a wide window, stays within a narrow one (no
  sideways scroll, header buttons visible) and keeps its group tabs on one row; the BotRank
  settings tab uses the full width too.
* Links to pages in jContent work when Jahia runs under a context path.

## 1.2.0

### Changed

* GEO readiness: llms.txt is written in the `llms` module's own **Llms.txt** screen (Additional >
  SEO), no longer generated here. The **llms.txt** tab shows the published file, read only, with an
  **Edit in Llms.txt** button, and says when the `llms` module is not enabled on the site.
* The llms.txt check lists the links of the file to the site whose page moved or is no longer
  published. A page the file does not list is the author's choice and is no longer reported; the
  drawer says whether the page is listed.
* The AI output budget (`AI_MAX_TOKENS`) defaults to 6000.

### Fixed

* The AI review and assists work with any chat model, OpenAI reasoning models included, and with
  models that wrap or skip the JSON.
* On a server where several sites share a host name, the llms.txt check read another site's file.

## 1.1.0

### Fixed

* After an upgrade from Content Compliance Scan, **New content** offers **Compliance glossary** and
  **Non-authorised term** again, with their labels: the module takes the glossary types over when it
  starts. Existing glossaries are unchanged.
* The **Content governance** entry of the site administration menu shows the module's icon.

## 1.0.0

First release, replacing three modules: GEO Readiness (`geo-readiness` 1.6.0-SNAPSHOT), Page Quality
Audit (`page-audit` 1.7.0) and Content Compliance Scan (`content-compliance-scan` 1.0.1).

### Added

* One **Content governance** button in the page header, opening one drawer with three sections:
  **Page quality** (accessibility, SEO, Web Vitals, readability, eco-design, links, Jahia checks,
  AI review), **GEO readiness** (score, crawler access, site files, structured data, AI citations)
  and **Compliance** (glossaries of non-authorised terms). Each section shows its issue count; a
  section keeps its results while the editor switches to another.
* Every section and every tab of the drawer has a tooltip saying what it is for, followed by why it
  is greyed out or what its count means; Site Settings shows the same text under each tab's switch.
* **Site Settings > Content governance**, for site administrators: switch each section and each tab
  on or off per site (everything on by default); see the shared AI provider; connect the site to
  BotRank. A section switched off also switches its dashboard off (the entry stays and says
  so), and the server refuses its calls.
* GraphQL `contentGovernance { siteSettings(siteKey) }` and
  `mutation { contentGovernance { saveSiteSettings(siteKey, disabled) } }` (site-admin).

### Changed

* **One AI configuration** for every AI feature (page review, SEO, alt text and plain-language
  assists, GEO written report): `org.jahia.community.contentgovernance.llm.cfg`. The page review now
  uses the same provider layer as the GEO report (OpenAI calls send `max_completion_tokens`). The
  cost of a review is shown only when the price is configured; there is no default price or model.
* AI features are shown greyed out, with the reason, when no model is configured; the AI citations
  tab is greyed out until the site is connected to BotRank.
* Page quality now uses Moonstone throughout (buttons, tabs, badges, banners, loaders); the
  compliance scan moved from a dialog into the drawer.
* Servlets moved under `/modules/content-governance/` (`geo/*`, `audit/ai-review`).
* `robots` and `llms` became optional dependencies: Page quality and Compliance work without them.
* A tab switched off is not measured, cached, exported or sent to the AI review; a site that keeps
  one section on still sees its title; refusals of the AI features say why, in the editor's language.
* The module keeps its settings, GEO scans and BotRank binding in nodes only the module reads and
  writes; scheduled scans fetch only from the site's own addresses.

### Kept

* GEO scans and reports (`/sites/<key>/geo-readiness`), BotRank connection files
  (`org.jahia.se.modules.georeadiness.botrank-<id>.cfg`) and compliance glossaries (`ccs:` types)
  are read where the old modules left them. The GEO scan schedule and the BotRank daily sync keep
  their settings but start switched off: a site administrator saves them once to turn them back on.
* The GEO dashboard stays under Additional > SEO and the Compliance dashboard under Additional.

## Before 1.0.0: the three replaced modules

The history of the modules this one replaces, as they were released. Configuration files,
labels and paths named below are the old ones; the 1.0.0 entry above says what changed.

### GEO Readiness (`geo-readiness`)

#### 1.5.0

##### New Features

* The AI citations tab shows the AI engines behind each question, the change of each question and theme, and where competing pages stand against yours in the same answers.

* Added AI citations from BotRank: each page shows how often AI answers cite it and for which questions.

* Added AI citations to the GEO dashboard: how AI answers cite the whole site, its share and rank among cited sites, its most cited pages and the brand's visibility, from BotRank.

* The GEO dashboard has one layout: every tab opens on its title and intro, then cards, with its key figures in large type and the same text sizes everywhere.

##### Bug Fixes

* A page the server could not reach is no longer reported as crawlers refused by the site: the check says the server could not connect, names the error, and the score marks the fetch as failed instead.

* The page drawer closes when you click outside it, in the page under edit, the content tree or another panel, as jContent's own panels do.

* A sitemap, robots.txt or llms.txt that could not be fetched now says why (connection refused, timeout, certificate, unknown host) instead of reading as a missing file, and the page drawer shows the date of the site scan its sitemap facts come from.

* Structured data now reads an address split into its schema.org fields, coordinates and opening hours, and suggests LocalBusiness or Place from the properties a content type holds.

#### 1.4.1

* Fixed the page drawer so it opens against the right edge instead of in the top-left corner.

#### 1.4.0

* Closed a flaw where any logged-in account could read the site's configured AI provider, model and crawler list.

#### 1.3.2

A maintenance release with no change a user of the module will see. It clears the quality gate that had been red on `main` since before 1.3.1 was prepared, and removes the first of the obstacles that stop the release running in CI. An existing install upgrades in place.

* Cleared the eight maintainability issues and the reliability rating holding `main`'s quality gate red. A field left dead when 1.3.1 stopped reading JSON-LD with a regex and started parsing it; fifteen private helpers moved into the nested classes they actually serve, in `GeoScore`, `LinkGraph` and `SiteScorer`; and `Snapshot`'s thirteen-parameter constructor replaced by one that reads the configuration map, which is also what lets its defaults be an empty map instead of a second copy of the same thirteen values that had to be kept in step by hand. Behaviour is unchanged - sorting the stripped lines of `SiteScorer` before and after gives the same 612 lines, nothing added and nothing removed, and the 354 unit tests pass.

* The four chart boxes that carry the pointer tooltip now declare `role="none"`. The accessibility work in 1.3.1 took the tab stops off them and moved their numbers into the surrounding table and list structures, which is the right shape, but left them reading as interactive elements a keyboard could not reach. The role says what is true: the box draws, and the value spans inside it are what assistive tech reads. Nothing is hidden.

* `languageFlag` now reads a code point where it read a code unit. The two agree for every region letter that reaches this line, so no flag changes - the premise was wrong, not the answer.

* Two control characters in `SiteScopeTest` were stored as raw bytes rather than escapes, which made git classify the whole file as binary. Four kilobytes of tests had merged with no diff anyone could read. Same inputs, same cases, readable file.

* The release workflow declares the checked-out workspace trusted, so the release action's git commands can run as root inside the build container instead of failing on `detected dubious ownership` before anything is released. This is necessary but not sufficient: a CI release also needs the CI identity added as a bypass actor on the `Prevent tag deletion` and `support-baseline` rulesets, neither of which can be changed from a workflow file. Until then the release stays a manual job.

#### 1.3.1

* The dashboard and the drawer now meet WCAG 2.2 AAA on contrast, carry real headings, and announce a running scan to a screen reader. The charts stopped making every data mark a tab stop - a full failure matrix was upwards of three hundred of them - and instead carry their numbers as text inside the table and list structures they already had, so assistive tech reads the figures and the keyboard passes through in one stop. The drawer is a real `<dialog>`, and every control that had no accessible name has one.

* The public base url now takes its host from the repository rather than from the request that matched it. The two were equal under a case-insensitive comparison but not identical, so a `Host:` header could choose the casing of a base url that the scan servlet then persisted in `geoBaseUrl` and reused on every later scheduled scan.

* Sped up reading of fetched pages, sitemaps and site files, which could take minutes on some documents.

* Moved the module to the `org.jahia.community` groupId. The Java package, the OSGi configuration file and the bundle name are unchanged, so an existing install upgrades in place and keeps its settings.

* The runtime configuration is swapped through an AtomicReference, so the operation that replaces it reads as the atomic one it always had to be.

* Decomposed the four methods Sonar scored furthest over the cognitive-complexity limit - the scoring engine at 43, the robots.txt rule evaluator at 27, the llms.txt freshness comparison at 19 and the robots.txt parser at 16. Behaviour is unchanged, and the characterisation tests added in the previous release are what proves it.

* Decomposed four more methods over the cognitive-complexity limit - the HTML analyser at 26, the link-graph report at 28, the robots.txt editor's parser at 16 and the guest-visibility classifier - each behind characterisation tests written first. Unit coverage of the module roughly doubled, and nine classes are now at or near complete coverage.

* Audited every factual claim in the documentation against the code and corrected what was wrong, including two instructions that would have broken a contributor's setup: a `yarn e2e` command that does not exist, and a CI `instance_type` the live workflow deliberately omits because it fails. Two of the corrections were code rather than prose - the test harness dropped the `notests` flag it documented, and a comment claimed the module refuses to fetch a site with no server name when it in fact falls back to `localhost:8080`.

* Fixed crawler and site scans so repeated checks remain reliable and sitemap comparisons no longer follow links to other sites.

* Fixed the `$` end-anchor in robots.txt matching, which was wrong in two opposite directions: a pattern with no wildcard checked its start and its end independently, so `/foo$` matched `/foo/bar/foo`, and a pattern ending in `*$` rejected every path longer than its literal prefix, so `abc*$` did not match `abcdef`. Sites whose robots.txt uses `$` anchors will see corrected verdicts in the drawer's policy cross-check and may see their score move.

* The language, freshness, sitemap and schema checks are now covered by tests that assert what they report rather than that they answered, and the three methods behind them have been decomposed to sit under the complexity limit.

* Closed an SSRF where the request's Host header chose the port this module connected to, gated the one endpoint that had no permission check, stopped a browsing node deleting every site's scan schedule from the cluster, and fixed six places where the code did the opposite of its own comment - including a config marker typo that silently reset the administrator's settings on every redeploy.

* Gave the four servlets one base class instead of four copies of the same seven helpers, which fixed two defects that were living in the drift between them: three of the module's four JSON endpoints were served without `X-Content-Type-Options: nosniff` while echoing content read from the site being checked, and three of them silently truncated an oversized request body so it came back as "malformed body" instead of being refused for what it was.

* Nothing escapes a servlet method any more, the servlets hold no mutable shared state, an interrupted crawler check no longer swallows the interrupt, and the language pattern is bounded so a long input cannot overflow the matcher's stack.

* Fixed seven defects the new characterisation tests had pinned. The largest: a page whose JSON-LD is syntactically broken now correctly reports no structured data, where it used to pass the check because the types were read out of the text with a regex rather than parsed. The robots.txt editor also now keeps the promise it makes in its own documentation - a merge that changes nothing returns a customer's file exactly as it arrived, rather than dropping blank lines, adding a trailing newline or mixing line endings into it.

* The module has unit tests for the first time: JUnit 5, AssertJ and JaCoCo, with 189 characterisation tests pinning the scoring engine, the robots.txt matcher, the llms.txt freshness comparison and the two security guards. They exist so the decomposition that follows cannot change a score silently - the existing Cypress suite asserts that a scan happened and that the UI reads "n of m checks passed", never which n or which m.

* Removed ten imports left behind by the decomposition.

* Cleared what was still keeping the quality gate red on the main branch: a pattern left dead when JSON-LD types stopped being read with a regex, six helpers that belonged inside the class that was already their only caller, and a thirteen-argument constructor that now reads its thirteen defaults out of an empty map instead of a second list of the same values kept in step by hand. In the dashboard, four chart boxes carried a mouse handler with no role to say they are decorative, and the flag helper read a code unit where it meant a code point.

* Two test inputs wrote their control characters as raw bytes rather than escapes, which made git classify the whole file as binary - four kilobytes of tests that no reviewer could see a diff of, and that no later change to them would show either.

#### 1.3.0

* Added an optional AI-written GEO report with prioritised recommendations and Markdown or JSON export.

#### 1.2.0

Freshness stops being a picture you have to decode and becomes a report you can act on, the
endpoints hold to the permission the screen already declared, and the repository grows the test
suite and the shared CI it never had.

##### Added

* **Freshness reads as a timeline.** The distribution is drawn left to right from oldest to most
  recent, so the shape says whether the site has been maintained. The bucket lying entirely past
  the threshold carries the warning colour, which puts the line an editor chose into the picture
  rather than leaving it in a caption. Empty buckets draw nothing at all.
* **Every page and when it last changed.** Under the groups, one row per published item in the
  language being measured: title, content type, section, public path, the date it last changed and
  how long ago. Anything with no date comes first, then oldest to newest, so the list opens on the
  work. Rows open in jContent and page like every other list here. Cut at five thousand rows, which
  it says; the counts and the groups still measure everything.
* **An end-to-end test suite.** Two releases had shipped with no automated test of any kind. The
  suite covers the three endpoints across a guest, an editor without the dashboard's permission and
  a publisher, the values a path-shaped input can take, request validation, the rate-limit window
  and the two jContent screens. It asserts its own premise as well as its results, because a
  refusal proves nothing unless the account demonstrably holds the neighbouring rights and lacks
  only the one under test.
* **The shared Jahia module CI.** The build was a checkout and one `mvn install`, so nothing gated a
  pull request beyond compilation. The workflows now call `jahia/jahia-modules-action`: a
  pull-request gate with static analysis and SonarQube, a merge path publishing a snapshot and an
  SBOM, a release path, and a scheduled full analysis. Also a pull-request template and the
  changelog-fragment mechanism the repository's own settings already imply.

##### Changed

* **The endpoints require the permission the dashboard route declares.** That requirement lived
  only in the front end, so the screen and the server can no longer drift apart. An editor without
  it gets no dashboard and no robots.txt or llms.txt editor; the page drawer is unchanged and still
  opens for anyone who can read the page in the editing workspace.
* **A scan's scope must resolve inside the site it was started from**, and it is resolved before it
  is compared rather than matched as a string, so a relative path cannot leave the site it came
  from. The language a request carries is now required to look like a language, at the endpoints
  and again where it becomes a node name.
* **The address a check fetches comes from the site or from configuration.** A request may say how
  that host is reached, scheme and port, but only once it is already addressing it: a `Host` header
  is written by whoever sent the request. A site that declares no server name now has nothing to
  fetch until `PUBLIC_BASE_URL` says so, where before the request decided. A sitemap index names
  further files, and only a child on the same origin is followed.
* **A schedule belongs to the account that saved it** and every run asks again whether that account
  still holds the permission, so revoking it stops the schedule instead of merely hiding the screen.
  Triggers are now tied to the bundle: removed when it stops, reinstalled from the repository when
  it starts, so a redeploy neither strands one nor discards an editor's schedule.
* **The module declares `jcontent`, `robots` and `llms` as dependencies.** Jahia holds it back until
  those are started rather than starting it and failing at the first write.
* **`Bundle-SymbolicName` and `Jahia-Depends` come from the parent's properties only.** Both were
  also written into the bundle plugin's instructions, so each had two sources that could disagree.

##### Removed

* **The range chart.** It drew each group from its newest item to its oldest with the median
  marked, which encoded spread. Nobody asks that of a site, and on most groups the newest and
  oldest item share a date, so the range collapsed to a dot. Nothing else drew one.

##### Fixed

* **Seven advisories in the build's own dependencies**, five against `postcss` and two against
  `serialize-javascript`, by taking the patched `css-loader` and `copy-webpack-plugin`. Neither
  ships in the bundle, so this is the build's supply chain rather than the running module.
* **Every declarative service component carries `service.description` and `service.vendor`**, which
  is what the OSGi console reads when somebody asks what a service is and who ships it.

#### 1.1.0

The visual release. Nothing new is measured; what was already measured is now drawn where a number
has a shape worth seeing, and no findings list is capped any more.

##### Added

* **Charts, without a chart library.** Four panels now carry a visual form drawn in plain HTML and
  CSS: a meter under the site score whose fill carries severity; horizontal bars for the score by
  section; a histogram of content age plus a range bar per type and section running from newest
  to oldest with the median marked; coverage and readiness score side by side per language; and a
  single stacked bar for structured-data coverage. Every mark has a hover and keyboard tooltip. A
  charting dependency inside a Module Federation bundle is weight every jContent page pays, plus a
  second theme to keep in step with Moonstone - and the forms needed are bars and a meter.
* **Colors from Moonstone's own tokens, validated rather than eyeballed.** The two series hues
  (dark accent, purple) were run through a colorblind-safety and contrast validator on Moonstone's
  light surface as an adjacent pair. The plain accent fails 3:1 and is not used for marks; gray is
  a track, never a series. A missing value is never drawn as a zero-length bar - an unmeasured
  language renders as text saying so.
* **Pages with findings is a matrix, not a list.** Pages down, failing checks across, one cell
  per pair colored by severity, columns ordered by how many pages fail them. A list of "15/17"
  chips said how many things were wrong on each page; it could not say which, or that two of them
  are wrong on every page - and a column that is solid top to bottom *is* the template roll-up,
  visible before anyone reads a number. Each cell and column header carries a tooltip; every
  status color ships with its word in the legend. Paged twenty-five at a time with Moonstone's pagination,
  the column set computed over every finding so columns do not reshuffle between pages.
* **Every findings list pages, none is capped.** Sitemap findings, unlinked pages, address
  conflicts and invisible content used to stop at ten rows with an "and N more" line - which told
  the reader something was withheld and gave them no way to reach it. On a site with two hundred
  stale sitemap dates, a hundred and ninety were invisible. Each group now pages twenty-five at a
  time with Moonstone's control. Anything up to the chosen page size is shown in full, as is the
  failure matrix; the control itself appears once a list passes ten rows, so a denser view can be
  chosen and paged even when nothing is being withheld. A paged group also says "Showing 1–25
  of 40" *above* its rows, so the header's count and the visible rows never disagree without the
  explanation sitting between them.

#### 1.0.0

First release, so this section describes the whole module rather than a delta. It needs nothing
from any external vendor.

##### Added

**Seeing what a crawler sees**

* **Crawler access check.** One server-side fetch per AI crawler user agent, fifteen of them with a
  normal browser first as the control. No cookies, no session, redirects not followed, and the
  **initial HTML** is read rather than a rendered DOM. A page whose content only appears after
  JavaScript runs looks perfect to an editor and empty here, which is the point. Fetches run three
  at a time, so sixteen requests take about a second rather than two minutes.
* **robots.txt parsing and cross-check.** Follows what the major crawlers actually do: consecutive
  `User-agent` lines share a group, the most specific group wins, the longest rule wins within it,
  `Allow` beats `Disallow` on a tie, and `*` and `$` are honoured. The matched rule is always shown
  so the reasoning can be checked rather than trusted. Names the two disagreements between policy
  and reality: **blocked but allowed**, and **reachable but disallowed**.
* **llms.txt report.** Presence, title, summary, section and link counts, and the common false
  positive where `/llms.txt` returns the HTML page with a 200.
* **Content no AI can ever read.** Published pages a visitor with no account cannot open, listed
  with pages closed *on purpose* separated from pages closed *by accident*. A members area is not a
  defect. Also covers visibility conditions that have expired, and pages marked `noindex`.

**Scoring**

* **Eighteen checks in three groups**, each reading one fact already observed and showing that fact
  beside the verdict. Deliberately a count of checks, not a rating out of 100: an editor can
  disagree with a specific line but not with a number they cannot see inside. Three severities,
  from "a crawler cannot read this at all" down to "worth doing".
* **Six signals read from HTML already in memory**, so they cost no extra request: declared page
  language, hreflang alternates, sub-heading outline, alt-text coverage, JSON-LD `@type` breakdown
  rather than a bare count, and whether a modification date is published.

**The whole site, not one page**

* **Scheduled site scan.** A Quartz cron job walks every published page, fetches each once as an AI
  crawler, and stores an aggregate plus the pages that failed something. The dashboard shows the
  overall figure, the movement since the last run, a breakdown by section, and the pages with
  findings. The schedule is built from dropdowns, with an optional scope and an enable toggle, and
  a running scan appears in the administration job list.

* **Findings roll up to the template that produced them.** Templates are ranked by the pages they
  render, so the biggest single fix is first, and what the template is answerable for is separated
  from what merely happens on some of its pages. The drawer says so on the affected check: which
  template, how many pages share it, and that one fix there covers all of them. Nothing outside
  Jahia can do this, because nothing else knows which template rendered a page.

* **The sitemap is compared against reality.** Every `sitemap.xml` entry is resolved back to a
  repository node instead of being fetched, so a 378-URL site costs no requests to check. Four
  disagreements are reported: published pages the sitemap never mentions, entries that resolve to
  nothing published, `lastmod` values that contradict the node's real modification date, and pages
  the sitemap advertises while their own markup says `noindex`. A fifth, reported as one finding
  rather than two: the sitemap module does not use vanity URLs, so a page that has one is listed
  under the address Jahia redirects away from - crawlers sent through a redirect to reach content
  with a direct address. The published set is built as guest
  and across every site language, because a sitemap is language-aware and written for anonymous
  crawlers. It has its own dashboard tab with its own refresh, because it is a comparison and not a
  score, and a presence test that requires the response to actually be a sitemap: "200 with a tag
  in it" would accept an HTML error page, parse to zero entries and report every published page as
  missing. No sitemap, not a sitemap, and could not be reached are three different messages. The
  dashboard lists each finding with a link to the node, its public URL and, for a
  wrong date, the two dates. The drawer carries the three findings that are about one page -
  absent from the map, advertised while saying `noindex`, or listed with a date that no longer
  matches - matched on path and language so a French finding never surfaces on the English page.

* **Pages nothing links to.** A link graph built from the rendered HTML of every page the scan
  already fetches, so it costs no extra requests and every way of producing a link counts the same:
  a menu built from the page tree, a listing, a rich text link, a configured button. Repository
  references cover sources the scan never rendered. Two findings, because they are two problems: a
  page nothing links to *and* the sitemap does not list is unreachable; one reachable only through
  the menus or only through the sitemap is found but uncommitted. Navigation and content links are
  counted apart, since being in a menu that lists everything is not the same as somebody choosing
  to link to you. The drawer says which of the two this page is.

* **One page, several addresses.** Jahia owns the vanity URL service, so every address a page
  answers on is a repository fact rather than something to be crawled for. Three findings: a vanity
  URL filed under a language the site does not serve, which returns a 404 to everybody; one page
  answering on several live addresses in the same language, loudest when none is marked default;
  and a page with aliases whose canonical tag is missing or names something else entirely.
  Deliberately no check for two pages claiming one address - Jahia renames the second on save, so
  it cannot happen.

* **llms.txt is checked against the site it describes.** The file is written once and served
  unchanged, so it goes stale silently - an assistant is handed a map of a site that has moved on,
  and nothing anywhere says so. The served copy is diffed against what regenerating would produce
  right now, so the finding is exactly as trustworthy as the button offered to fix it, and the
  reason is named: the address now redirects, the page is no longer part of the generated list, it
  is gone, or it is published and missing from the file. Not tied to the readiness score, which
  regenerating does not move.

* **Where the site has gone stale.** The age of everything a visitor can read, taken from the
  repository rather than from the dates pages choose to publish, in five buckets and broken down by
  content type and by section. Grouped rather than ranked, because a single oldest-first list puts
  a legal notice next to a news article and gets ignored; each type is judged against its own list
  instead, and nothing tries to guess which types are meant to age. A group is flagged only when
  its *newest* item is past a configurable threshold. A repository query, so it answers without
  waiting for a scan, and changing the threshold recomputes rather than filtering.

* **Readiness one language at a time.** Coverage and score for every language the site declares,
  side by side, so a strong language cannot average out a weak one. A language that has never been
  scanned reports as *not measured* rather than as zero - a zero reads as "this market is broken"
  when it means "nobody has looked", and the two need opposite responses. Coverage is a repository
  read separating translated from published, over pages and content items alike. The drawer names
  the languages this page has no translation in, and any translation that exists but has not been
  published.

* **Structured data derived from the content model.** Every other tool infers schema.org output
  from the words on a page; the content type already says what the content *is*, so it is derived
  instead. Map each type once - defaults only for Jahia's own types, since a custom type is
  somebody's model and its name is not evidence - and every item of it produces JSON-LD, with
  property sources found by convention rather than configuration. Required properties with no
  source are named rather than invented, and a generated name that disagrees with the title the
  page renders is reported as a conflict, because structured data that contradicts its page is
  worse than none. The drawer shows the snippet with a copy button; nothing is written into a page.

* **The page drawer now says where a page stands.** A score with no reference point is not
  information, so the drawer carries the site average and the page's own section average beside it,
  how many pages link here from navigation and from content, and whether the page is in llms.txt.

**Finding your way around it**

* **Ten panels grouped into four.** The dashboard reuses the same three groups the page drawer
  already sorts its checks into - can a crawler reach it, is what arrives usable, site-level files -
  so there is one vocabulary to learn rather than two, with the site score standing outside them as
  the summary of all three.

**Writing, behind generate → diff → confirm**

* **llms.txt generated from the published page tree.** Deterministic: no model call and no external
  service, so the same site produces the same file every time and the rule can be read and
  predicted. Honours role visibility and `noindex` by walking as a guest, so a page a visitor cannot
  read is never advertised to an assistant.
* **AI crawler control in robots.txt.** Allow or block per crawler, merged into the existing file.
  Everything not asked about is left byte for byte as found, comments and `Sitemap` lines included.
  Each crawler carries a one-line description of what it does, and a split between the seven that
  answer questions and the seven that only train models: refusing a training crawler costs no
  visibility, refusing an answering one takes the site out of that assistant's replies. The panel
  warns only for the second kind.
* **Nothing is overwritten unseen.** Both write paths show a highlighted line diff against what is
  stored, unchanged runs collapsed, additions and removals marked with a sign as well as a colour.
  The apply button needs a second confirming click, and what gets written is exactly the text on
  screen, so hand edits made before applying survive.

**Where it lives**

* **A page drawer** for one page, and a **site settings page under Additional > SEO**, beside
  Robots.txt and Sitemap. Both files belong to the site, so they are edited there rather than from
  whichever page an editor happens to have open.
* English and French throughout, including every check label and its explanation.

##### Notes on behaviour worth knowing

* **Being disallowed in robots.txt is not scored as a failure.** Refusing a crawler is a legitimate
  decision. A disagreement between the policy and the server is scored, because one of the two is
  then wrong.
* **The site scan evaluates 17 checks, the drawer 18.** Comparing what a crawler receives against
  what a browser receives needs a second fetch of the same page, so that check stays in the drawer.
  Both report the same failures; only the denominator differs, and the panel says so.
* **A check is blamed on a template only at nearly every page it renders**, over at least three
  pages. A missed roll-up costs one person some time; a wrong one sends them to edit a shared
  template over a colleague's typo.
* **Allow and block are not symmetric.** Block replaces a crawler's rules with a site-wide refusal.
  Allow only undoes a site-wide refusal, so path rules such as `Disallow: /docs/` survive rather
  than being flattened away.
* **A crawler sharing a `User-agent` group is split into its own group** when its stance changes, so
  its former siblings keep their rules.
* **The tested URL is built by Jahia, not guessed.** `node.getUrl()` then the outbound URL rewriter,
  which yields the vanity URL where one resolves on that host.
* **The check fetches a page even when robots.txt disallows it**, on purpose. Knowing that a
  disallowed page is nonetheless reachable is a finding.
* **Results are stored on the site**, on a hidden node that never publishes, as an aggregate plus
  the failing pages rather than a row per page. Deleting a site takes its scan with it.

##### Fixed

* **A page with a default vanity URL was scored against the wrong address.** The module used the
  page tree path, which Jahia 301-redirects to the vanity URL, so every crawler fetch received a
  redirect rather than the page: the score collapsed to 7 of 18 reporting "no text in the initial
  HTML", the sitemap comparison called the page missing, and the link graph could not match links
  pointing at it. The default vanity URL is now read from the repository, which is the authority.
  The same page returned to 16 of 18.

##### Known gaps

* The site scan is capped at 2000 pages per run, and is synchronous when triggered by the button. A
  larger site needs the schedule.
* One language per scheduled run, so a multilingual site needs one schedule per language.
* `llms-full.txt` cannot exist on this stack. The community `llms` module declares one property and
  one route, so the report calls the file absent when it is really unsupported.
* The robots.txt merge is line-based, not a full RFC 9309 rewrite. It does not reorder, deduplicate
  or tidy, so a hand-written file comes back recognisable.
* Vanity URLs are host-dependent by Jahia design: the rewriter emits one only when the request's
  server name resolves to the page's site.
* The link graph judges only the language the scan read, since only that language's pages were
  fetched. A multilingual site needs one scan per language to see all of it.
* The link graph sees links on pages, not on content items with their own URL, unless they are
  modelled as repository references. An article linking to a page through rich text alone is not
  counted.
* The sitemap comparison resolves rather than fetches, so it cannot see redirects. An entry that
  301s to somewhere else is reported as clean.
* The site walk lists `jnt:page`, while the sitemap lists everything with a public URL. On a site
  whose articles are `jmix:mainResource` content rather than pages, the scan scores far fewer items
  than the sitemap compares. The two counts are measuring different things by design, but only the
  sitemap comparison currently sees the whole addressable site.

### Page Quality Audit (`page-audit`)

#### 1.7.0

##### New Features

* The Accessibility scorecards now show the share of automated rules that passed at each WCAG level, instead of a violation count where zero was the good result. A clean level reads "100% automated rules passed" in green rather than a bare "0".

  The wording is deliberately not "compliant": automated checks cover only part of WCAG, so a 100% card is not a conformity claim. Rules the engine could not decide stay out of the ratio and are listed as "to verify", a level with nothing to test shows no score instead of a fake 100%, and when a level has violations the card still says how many and how many elements they affect. The French copy also drops "conformes" for the same reason.

##### Bug Fixes

* Refreshed the build tooling: webpack 5.111.1 and @jahia/moonstone 2.21.0. Moonstone is a declared dependency but is never imported by the drawer nor shared with jContent, so the update has no effect on the back-office.

#### 1.6.0

##### New Features

* Upgraded the accessibility engine to axe-core 4.13. The Accessibility tab picks up the new rule set automatically, because the audit runs by WCAG tag rather than a hardcoded rule list. Editors will notice that `sectionheader` and `sectionfooter` roles are recognised, deprecated ARIA attributes are now reported as "needs review" instead of ignored, whitespace `alt` on presentational images is accepted, and several colour-contrast false positives caused by stacking contexts are gone. A page can therefore show a different violation count than it did on 4.12 without its markup having changed.

##### Bug Fixes

* Moved the module to the `org.jahia.community.modules` groupId. The bundle name, the Java package and the OSGi configuration file name are unchanged, so AI provider settings and per-site activation survive the move.

  Upgrading an instance that already runs an older release is **not** a drop-in install: Jahia identifies a module by its Id, and refuses to parse a bundle whose Id is already registered under a different groupId, leaving it stuck in `STARTING` with its resources unavailable. Uninstall every installed version of Page Quality Audit first, then install the new jar. The module defines no content types, so a full uninstall does not touch site content.

* Held back dependency updates that would break the jContent host. The packages this bundle shares with jContent as Module Federation singletons (i18next, react-i18next, @jahia/data-helper, @jahia/ui-extender, @jahia/moonstone) now have their majors pinned, because Webpack elects the highest version among providers: a newer major shipped here would replace the host's copy for the whole back-office UI, and the build compiles either way so continuous integration cannot catch it. Also refreshed the build tooling (webpack 5.110.3, frontend-maven-plugin 2.0.2).

#### [1.5.0] - 2026-09-07

AI moves from reviewing to assisting: suggestions appear next to the findings
they address, in the page language, ready to copy.

##### Added

* **AI suggestions in the SEO tab** (when a provider is configured): ready-to-paste
  `<title>` alternatives, meta descriptions with live character counts, a focus
  keyword plus supporting terms, and heading rewrites (current → suggested with a
  one-line reason, highlightable in the preview) - each with a Copy button.
  Suggestions are grounded in the page text and written in the **page's
  language** since they are published content; only the reasons follow the
  editor's UI language. Display-only: nothing is written to the repository.
  Served by the existing hardened endpoint as a second server-defined task
  (`task: seo`) with its own prompt and whitelisting parser; cached with the
  audit like the AI review. Also proposes `og:title` / `og:description` for
  the social sharing card.
* **AI alt text in the Accessibility tab** (when a provider is configured and
  the page has images without `alt`): per image, a thumbnail, a suggested alt
  (≤125 chars, page language, Copy, highlight) and a one-line reason; purely
  decorative images are flagged for an empty alt. Vision-capable providers
  (Anthropic, OpenAI) receive a downscaled copy of each picture; DeepSeek is
  text-only, so suggestions there are inferred from file names and context
  and the UI says so. Third server-defined task (`task: alt`), 8 images per
  call.
* **Suggested fixes in the AI review**: recommendations about specific wording
  now carry a ready-to-copy correction in the page language (typo fixed,
  stronger CTA label, consistent term…).
* **Call-to-action labels** in the SEO assist: generic link/button labels
  ("Read more", "Click here", "Submit"… detected after stripping decorations
  like "Read More +") get 2-3 specific alternatives grounded in the CTA's
  surrounding text, with Copy and highlight. The SEO generic-anchor check now
  uses the same normalized matching and covers buttons.
* **AI plain-language rewrites in the Readability tab** (when a provider is
  configured and the page has long sentences): up to 6 of the hardest paragraph
  sentences are rewritten in the page language - same facts, shorter sentences,
  common words - with before/after word counts, highlight of the original,
  Copy and a reason. Fourth server-defined task (`task: simplify`). Inline
  scripts inside content blocks are excluded from the candidates.

##### Changed

* Results cache schema bumped to 3 (the SEO result gained `weakCtas`); older
  cached audits are discarded and re-run on the next opening.

##### Fixed

* The AI review prompt gave the page language as a bare ISO code while the
  report language was spelled out, so a French-UI editor auditing an English
  page could be told to translate the page into French. Both languages are
  now named explicitly, with the rule that content in the page language is
  correct by definition.
* DeepSeek V4 models reason by default, which consumed the whole `AI_MAX_TOKENS`
  budget (empty answer, `502`) and made the visible answer ignore the JSON-only
  instruction. Reasoning is now disabled on DeepSeek requests, and an empty
  reasoning-only answer produces an explicit server-side diagnostic instead of
  "model did not return JSON".

#### [1.4.0] - 2026-07-09

##### Added

* **Ecodesign (RGESN) tab**: page-level checks against the French RGESN 2024
  eco-design referential (Frontend / Contents / UX / Architecture families) -
  page weight, HTTP requests, DOM size, lazy-loading, web fonts, autoplay media,
  legacy image formats, oversized images, missing dimensions, third-party
  origins - each with an actionable recommendation, plus a manual checklist for
  the whole-service criteria (hosting, backend, governance) that a single page
  cannot assess. It is explicit in-UI that this is **not** an RGESN conformity
  score. Reuses the Web Vitals resource data.
* AI review gains an **`ecodesign` category** so the LLM can flag
  digital-sustainability issues in prose.

##### Fixed

* The localStorage results cache now carries a schema version; entries written
  by an older module version are discarded instead of restored, preventing a
  crash when the cached shape lacks a newly added analyzer key. Tab badges and
  dispatch are null-guarded as defense-in-depth.

#### [1.3.0] - 2026-07-09

##### Security

* Hardened the AI review endpoint against abuse of the operator's LLM key
  (fixes [#16](https://github.com/Jahia/page-audit/issues/16), PR
  [#17](https://github.com/Jahia/page-audit/pull/17)):
  * **Authorization** - the review is bound to a page the caller can read
    (`jcr:read`); unreadable or missing paths are rejected with `403`.
  * **Rate limiting** - per-user sliding window of 30 reviews / 10 minutes
    (`429` beyond that), protecting the shared provider quota and cost.
  * **Unauthenticated disclosure** - the status `GET` now requires a non-guest
    user, so provider/model/enabled state is no longer readable anonymously.
  * **CSRF** - the `POST` requires `Content-Type: application/json` (`415`
    otherwise) and rejects cross-origin browser requests via an Origin/Referer
    host check.
  * **Error disclosure** - provider/exception detail is logged server-side only;
    the client receives a generic message.
  * **Link checker** - only same-origin links on a read-only content-serving
    allowlist (`/cms/render`, `/cms/file`, `/files`) are verified with the
    editor's session; every other same-origin link is counted but never
    fetched, so a planted link cannot trigger a credentialed side-effect
    request from whoever audits the page.

#### [1.2.0] - 2026-07-09

##### Fixed

* AI review recommendations are now written in the **editor's jContent UI
  language** rather than the audited page's language (quoted page wording stays
  verbatim). The prompt states an explicit language name instead of an ISO code
  for reliable results.

##### Changed

* More comfortable reading: base line-height raised to 1.55 across the drawer,
  with extra spacing in stat lists and recommendation details.

#### [1.1.0] - 2026-07-08

##### Added

* **AI review tab** (optional): sends the page text plus a digest of all audit
  findings to a configured LLM (Anthropic, OpenAI or DeepSeek) and returns an
  overall assessment plus up to 15 prioritized recommendations across 11
  categories - including dimensions no automated check covers (proofreading,
  factuality, consistency, conversion, localization quality, legal risk). Exact
  wording is highlightable in the preview; the footer shows token consumption
  and estimated cost. Configured via `org.jahia.se.modules.pageaudit.cfg`.
* **Result caching** per page and language in localStorage, with a "last audit"
  timestamp in the header; reopening the drawer restores instantly.
* **Staleness detection**: a cheap repository probe flags when the page changed
  after the audit, keeping the old report visible as a fix-it checklist.
* **Collapsible page preview** for full-height results.

##### Changed

* Tab order: Accessibility, SEO, Web Vitals, Readability, Links, Jahia, AI review.
* Text extraction joins text nodes with spaces (accurate readability counts, no
  spurious "missing spaces" findings); Jahia's preview title prefix is stripped
  from SEO title checks.

##### Fixed

* Editor/preview tooling (e.g. jExperience persona panel) is excluded from all
  analyzers; HTTP 4xx/5xx preview renders are refused instead of scored.

#### [1.0.0] - 2026-07-08

##### Added

* Initial release. jContent UI extension adding a "Page audit" action that opens
  a side-drawer auditing the current page across six tabs: Accessibility
  (axe-core WCAG A/AA/AAA + manual checklist), SEO (with social preview), Web
  Vitals (lab), Readability (EN/FR), Links (internal verification), and Jahia
  content health (publication + translation coverage via GraphQL).
* Every tab leads with severity-ranked, editor-friendly recommendations; results
  re-runnable and exportable as JSON. Full English + French UI.
* MIT licensed; GitHub Actions CI and Dependabot with platform guardrails.

[1.5.0]: https://github.com/Jahia/page-audit/releases/tag/v1.5.0

[1.4.0]: https://github.com/Jahia/page-audit/releases/tag/v1.4.0

[1.3.0]: https://github.com/Jahia/page-audit/releases/tag/v1.3.0

[1.2.0]: https://github.com/Jahia/page-audit/releases/tag/v1.2.0

[1.1.0]: https://github.com/Jahia/page-audit/releases/tag/v1.1.0

[1.0.0]: https://github.com/Jahia/page-audit/releases/tag/v1.0.0

### Content Compliance Scan (`content-compliance-scan`)

#### 1.0.1 (2026-10-29)

##### Fixed

* Japanese and Chinese: a *Whole word* or *Word starting with* term never matched inside a sentence,
  since these languages write words without spaces. Letters of Han, Hiragana, Katakana, Thai, Lao,
  Khmer and Myanmar no longer extend a word, so 安全 is found in 安全なロック機能 and a prefix stops at
  the end of the term. A boundary is checked only where the term itself starts or ends with a word
  character, so 情報 is found in ユーザー情報 and 安全 in Jahia安全. Kana keep their voiced marks
  (ガ is not カ), decomposed kana match their composed form, compatibility ideographs their standard
  form, and fullwidth Latin letters and digits their plain forms.
* A glossary's *Language* list offers every language of the site, also those not yet active on the
  live site (Jahia's own site-language list showed the live-active ones only, so a glossary for a
  language being prepared could not be written or edited).

##### Changed

* README: the install steps name the module manager's screens (upload, then Usage in sites).

#### 1.0.0 (2026-10-29)

##### Added

* Page Builder action **Compliance scan**: scans the page being edited against the site's
  glossaries of non-authorised terms for the language being edited, and lists each use with the
  field, the highlighted sentence, the reason and the suggested wording. Edit opens the content in
  Content Editor and the scan runs again when the editor closes. Informational: it does not block
  publication.
* Page scan scope: the page and its content, content references, the header and footer stored on
  the ancestor pages (read on the server, since locked chrome carries no marker in the edit frame),
  and the listing items the Page Builder frame shows.
* Site dashboard in jContent (**Additional > Compliance**): the whole site in the language selected
  in jContent, terms ranked by use, pages and content items with their issues as expandable rows,
  Open in Page Builder and Edit, **Export CSV** (one row per issue, UTF-8 with BOM, formula-safe) and
  **Export JSON** (the whole report with the scan time). Each node counts once, where it is stored.
* Content types `ccs:glossary` (one per language, under the site's contents) and
  `ccs:glossaryTerm` (term, match mode `word`/`prefix`/`anywhere`, reason, suggested wording), EN and
  FR labels and tooltips. Matching ignores case and accents, and phrases match across any white space.
* Several glossaries per language: all apply, each issue names its glossary, and a term two
  glossaries share is reported for each with its own reason.
* Content-type icons (Lucide glyphs) for the glossary, the term and the Compliance category.
* GraphQL `complianceScan { scanPage, scanSite }` and the exported `ComplianceScanService`.
* Documentation: `docs/` guides for glossary maintainers, editors and dashboard users, the
  architecture, and the publication-blocking analysis.

##### Security

* Both scans run with the caller's own session (default workspace, read-only, no system session).
  `scanPage` needs `jcr:write` on the page, `scanSite` needs `jContentAccess` on the site, and both
  refuse guests and sites where the module is not enabled (directly or through a dependency); paths
  sent by the browser are restricted to the page's site, and one unreadable path or location is
  skipped instead of failing the scan. One request runs at most 5 page scans and 1 site scan, and
  rich text is read in linear time whatever its markup.
* Requires jContent 3.7 or later (`Jahia-Depends: jcontent=[3.7,4)`).

##### Fixed (before release)

* The site dashboard could not scroll in jContent's Additional panel: its wrapper now fills the
  panel, so the report scrolls under a fixed header.
