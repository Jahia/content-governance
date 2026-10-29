# The GEO readiness dashboard, for content authors

The GEO readiness section of the drawer answers for one page. This dashboard answers for the whole
site: where it is weak for AI crawlers, which template is to blame, and what the two files that
speak for the site currently say.

Open it in jContent under **Additional > SEO > GEO readiness**. The title reads *GEO readiness for
site (site name) - (language)*. Eleven panels in four groups, the written report among them, measure
one language at a time. A fifth group, **AI citations**, shows what AI answers do with the site,
from BotRank.

A companion guide covers the drawer: [`drawer-guide.md`](drawer-guide.md).

Every tab is laid out the same way: a title and what the tab is for, the dates of the data as small
pills, then one card per section. The numbers that matter come first, in large type, coloured
green, amber or red only when the tab itself judges them.

## Three things that explain everything else

- **Most of it is instant, one part is not.** Nearly every panel is a question put to the content
  store, so it answers as soon as you open it. Only the site score needs to fetch every page, which
  is why it has a **Scan now** button and a schedule.
- **It covers one language at a time.** The language in the title is the one being measured. A
  second language needs its own scan, and a language nobody has scanned reports as *not measured*,
  which is not the same as scoring badly.
- **It is read-only except in two places.** Only the `robots.txt` and `llms.txt` panels write
  anything, both show the exact change first, and both need a second click.

## Who sees it

The entry appears on sites that enable Content Governance, for people who hold the **publish**
permission on the site. Without it the dashboard does not appear, and nor do the two file editors.
That is deliberate: these settings affect every page. The server checks the same permission on
every call behind the screen.

A site administrator can switch the GEO readiness section off for a site in **Site Settings >
Content governance**. The entry stays in the menu, but opening it then shows *Switched off for this
site*, and the server refuses the dashboard's calls. The **AI citations** group follows its own
switch, the **AI citations** tab of the GEO readiness section: switched off, the group is not shown.

## Overview

### Site score

The summary of everything else. A headline such as *78% of checks pass across the site*, with the
movement since the last run (*up 4 points since the last run*), so you can tell whether things are
getting better.

Underneath: how many pages were scored, how many have a critical failure, and how many no visitor
can read. Then a breakdown **By section**, so you can see one part of the site dragging the average
down rather than guessing.

There is also a list of the pages with findings, drawn as a grid of pages and checks, and a
**By template** card. This is the panel that answers "is it me or the template?". When a check
fails on nearly every page a template renders, it is reported against the template, not the
author.

**The scan.** Press **Scan now** for an immediate run, or set a schedule: **Scheduled scan** turns
it on, **When to scan** picks the frequency and time with dropdowns (server time), **What to scan**
takes the whole site or one branch, and **Save schedule** keeps it. A run fetches every published
page once, so it takes a while and shows progress as it goes. It covers one language and stops at
2000 pages, so a large site needs the schedule rather than the button. A running scan appears in
the administration job list. After an upgrade from GEO Readiness the schedule is off, with a note
saying so: the module switches it off the first time it starts on the site's existing data, and
**Save schedule** turns it back on.

The site score counts 17 checks where the drawer counts 18: comparing what a crawler receives with
what a browser receives needs a second fetch of each page, so that check stays in the drawer. Both
report the same failures.

### Report

Everything else on this dashboard is measured. This tab asks a model to read those measurements and
say what they add up to. It is always listed, but stays greyed out, with the tooltip *No AI provider
is configured on this server.*, until a server administrator configures the model the module's AI
features share (see [`architecture.md`](architecture.md#configuration)).

Press **Write the report**. You get a verdict, a status per area, a ranked list of what to fix first
with who can fix it and how much effort, quick wins, and a now/next/later plan. **Export as
Markdown** and **Export as JSON** download it.

**What leaves the building:** scores, the names of failing checks, page paths and titles, and
counts. Never the text of your pages, and never the key. It is written only when you ask, because
each one costs money. The report is written in your interface language; when a stored report was
written in another one, the tab says so.

## Can a crawler reach it

### Invisible content

A crawler served a login form gets a perfectly successful response and reports success. Only the
content store knows the page was never readable.

Press **Scan the site**. You get *N of M published pages can be read by a visitor with no account*,
and the rest split into **Probably not intended** and **Closed on purpose**. A members' area is not
a defect; a press release nobody can open is.

Start here when the site score looks bad for no obvious reason. A page in this list fails
everything downstream.

### Internal links

A crawler can report what it found. It cannot report what it missed, because it never knew the page
existed. This compares everything published against everything actually linked.

Two findings. **Orphan pages**: nothing links to them and the sitemap does not list them, so nothing
will ever find them. **Weakly linked**: reachable only through a menu that lists everything, or only
through the sitemap.

Navigation links and body-content links are counted separately on purpose. Being in a menu that
lists every page is not the same as somebody choosing to link to you, and crawlers weigh them
differently too.

Needs a site scan, because the links are read from the rendered pages.

### Addresses

Every alternative address a page answers on, taken from Jahia's own vanity URL list rather than
guessed by crawling.

What it looks for: an alias filed under a language the site does not serve, which returns a
not-found to everybody; one page live on several addresses, which splits its reputation; and a page
with aliases but nothing saying which address is the real one.

If the site uses no vanity URLs, it says so and there is nothing to do.

## Is what arrives usable

### Structured data

Structured data tells a machine "this is an article, published then, written by them". This panel
is where a content type is mapped to its schema.org meaning, once, so every item of that type
produces it.

Nothing is guessed: a type is whatever the person who defines it says it is. Required properties
with no source in the content model are named rather than invented, and a value that would
contradict the page is reported instead of emitted. An address split into its schema.org fields
(street, postal code, town, country) and a pair of coordinates count as a source. When the
properties of a type point to a better fit, such as a store with an address, a telephone and opening
hours, the panel suggests `LocalBusiness` with a **Use LocalBusiness** button that puts it in the
mapping; **SAVE MAPPING** keeps it. **Deliberately none** marks a type that should not produce
structured data, so it stops being reported as unmapped.

Nothing is written into your pages. The drawer shows the finished snippet for somebody to place,
normally in a template.

### Languages

One figure for the whole site hides the market that is failing. This puts every language the site
declares next to each other.

Per language: how many items are published out of the total, the percentage covered, how many were
never translated, and the score where one exists. A language with no scan says *not measured*,
which is not a zero, and the panel is careful about the difference.

### Freshness

Dates taken from the content store, not from whatever date a page chooses to display. You pick the
threshold in **Flag a group when nothing in it has changed in**: 3 months, 6 months, a year or two
years.

It groups rather than ranks, which matters: a legal notice that has not moved in two years is fine,
and a news section in the same state is not. A group is flagged only when its *newest* item is past
your threshold.

Then **Every page, oldest first**, with its type, its section and when it last changed. Each row
opens in jContent.

## Site-level files

### Sitemap vs reality

Every entry in the sitemap is resolved back to real content rather than fetched, so checking costs
the site nothing however large it is. **CHECK NOW** runs it at once; the scheduled scan refreshes it
too.

It finds: published pages the sitemap never mentions, entries pointing at nothing, dates that
disagree with the content's real date, pages advertised while their own markup says to ignore them,
and pages listed at an address the site redirects away from.

### robots.txt - writes to the site

A row per crawler with an **Allow** or **Block** stance, plus what blocking it would cost you.
Roughly half of them answer people's questions, so refusing one takes you out of that assistant's
replies; the others only train models, so refusing those costs no visibility at all. **Check what
the server does** fetches the site as each crawler, to see whether the server honours the file.

**Nothing is written until you confirm.** You get a **What would change** view showing the exact
lines added and removed; **Apply and publish**, then **Confirm overwrite**, applies it. Groups you
did not touch are left exactly as they are, comments and sitemap lines included.

If the site has no robots.txt yet, applying creates one. If you lack permission, the panel says so
instead of failing silently. Writing needs the `robots` module on the server; without it the panel
reports that robots.txt could not be read or written.

### llms.txt - writes to the site

**Generate** builds the file from the published page tree. No model is involved and nothing
external is called, so the same site produces the same file every time.

Pages a visitor cannot read, and pages marked to be ignored, are left out automatically. The panel
tells you whether the stored file is still current or has gone stale against what the site now
contains.

Same safety as robots.txt: you see the exact difference first, and what gets written is exactly
what is on screen, so hand edits made before applying survive. Writing needs the `llms` module on
the server.

## AI citations

What AI answers actually do with the site, from the last daily sync with BotRank. It is not a
readiness check: nothing here is computed from your pages, and nothing on it moves the site score.
It needs the same **publish** permission as the rest of the dashboard. Until a site administrator
connects the site (**Site Settings > Content governance**, **BotRank** tab), the group says *Not
connected to BotRank*. A line of dates at the top says when BotRank counted its latest answer, the
period, and when the site last synced.

BotRank counts citations, share and rank for the whole registrable domain (`blog.example.com` counts
under `example.com`, with any other site on that domain); the panel names the domain it shows.
*No comparison* on a figure means BotRank had no comparable previous 30 days, not that nothing
changed.

- **Overview**: the rank among cited sites as a badge, then the share of all citations in the
  project's AI answers (with its daily trend), the citations and the cited pages, each with its
  change. *What to read first* turns the figures into sentences: the gap to the site just above,
  how much the three most cited pages carry, cited addresses with no page, the best day. The brand
  visibility gauge sits beside it as context: an answer can mention the brand without citing any
  page of the site.
- **Ranking**: the sites just above and below, as bars of their share, the site highlighted; then
  the share per day, one bar per day the project received answers (empty when no answer cited any
  source), with the latest, average, best and lowest day.
- **Pages**: the 20 most cited addresses, each linked to its page in jContent when it matches one
  you can read. *No page matched* means the address resolves to no page; a site administrator can
  match it in Site Settings. Below, citations and cited pages per host, computed from the cited
  pages of each host; their share is an estimate.
- **Engines**: brand visibility per engine and per market, and the engines the project tracks with
  how often BotRank asks each one again.

## A sensible order

1. **Invisible content first.** A page no stranger can read fails everything else, so fixing these
   moves the score most.
2. **Then the site score by section.** Find the section dragging the average down rather than
   working page by page.
3. **Check whether it is the template.** If the same check fails across a whole template, one
   change fixes hundreds of pages and none of them are yours.
4. **Then internal links and the sitemap.** Pages nothing points at are invisible however good they
   are.
5. **Freshness and languages last.** These are planning tools: they tell you where to spend next
   quarter, not what is broken today.

### Before you change robots.txt

It applies to the **whole site**, not the page you came from, and blocking a crawler that answers
people's questions removes you from that assistant's replies. The panel tells you which are which.
Worth a conversation rather than a quick toggle.

## For site administrators: switches and BotRank

Both live in **Site Settings > Content governance**, which needs the **site-admin** permission on
the site. The server checks it again on every save.

**Features tab.** One card per section with an **On**/**Off** switch, and one switch per tab of the
section. For GEO readiness the tabs are **Score**, **Crawler access**, **Site files**,
**Structured data** and **AI citations**. Everything is on until someone switches it off. With GEO
readiness switched off, the section disappears from the drawer, the dashboard says it is switched
off, and the server refuses the dashboard's and BotRank's calls; the tabs keep their own setting
for when it comes back. A tab switched off disappears from the drawer. The **AI citations** switch
also hides the dashboard's **AI citations** group and stops the site's daily BotRank sync at its
next run. Press **Save**; editors see the change the next time they open the drawer.

**AI provider tab.** Shows whether the model shared by the AI features is configured, which
provider and model, and the file a server administrator edits. The written report of this dashboard
uses that model.

**BotRank tab.** Connects the site to BotRank, which feeds the drawer's **AI citations** tab and the
dashboard's **AI citations** group.

- **BotRank connection**: the BotRank organisation whose data this site reads. The list holds the
  connections a server administrator created for this site (see
  [`architecture.md`](architecture.md#botrank-connections)); *No BotRank connection* means there is
  none yet, and *This connection cannot be used* names the problem of a broken one.
- **Project**: the BotRank project that tracks the questions about this site.
- **Hosts of the site**: the addresses the site is served on, comma-separated, at most 10. `www.`
  does not matter. Needed when the server name is `localhost`, as on Jahia Cloud.
- **Daily sync**, with its **Time** (HH:mm, Paris time; 06:30 by default, after BotRank's night
  runs).
- **Save** keeps the settings; **Sync now** starts a sync at once. It takes a few minutes on a large
  site.
- **Last sync**: its status, when it last completed, the latest AI answer BotRank counted, the next
  daily sync and how many pages have citations, with a note when the last sync had something to say.
- **Cited addresses not matched to a page**: addresses of the site that AI answers cite, but that no
  published page, vanity URL or mapping matches. Old addresses after a migration often end up here.
  **Match to a page** links one to a page; **Addresses matched by hand** lists them with **Remove**.
  A mapping counts from the next sync, in the language the settings screen was showing.

Editors never cause a call to BotRank: only the daily sync and **Sync now** do. The sync stops, and
turns **Daily sync** off, when the site no longer enables the module, when the GEO readiness section
or its **AI citations** tab is switched off, or when the administrator who saved the settings loses
**site-admin**. The note from the last sync says why, and a site administrator turns **Daily sync**
on and saves again to restart it. While the section or the **AI citations** tab is switched off,
this tab cannot read the site's BotRank settings at all: switch them back on first.

---

**Dashboard or drawer?** The drawer checks one page, live, each time you run it. The dashboard
covers the whole site from the last scan. If the two disagree, the scan is probably old: run it
again.

Some lists are capped so a very large site stays usable, and only one of them says so on screen. If
a list looks suspiciously round, it may have been cut. For the exact definition of every check and
every site-level comparison, read [`checks.md`](checks.md).
