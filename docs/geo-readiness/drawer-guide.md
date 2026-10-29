# The GEO readiness section of the drawer, for content authors

When ChatGPT, Claude or Perplexity fetch one of your pages, what do they actually get? This section
answers that for the page you have open, and tells you which parts are yours to fix.

## Opening it

Open a page in jContent or Page Builder. A content item that has its own address (a main resource,
such as a blog post) works too. Press **Content governance** in the header. A drawer opens on the
right with one tab per section the site uses: **Page quality**, **GEO readiness** and
**Compliance**. Choose **GEO readiness**. When it is the only section switched on, the drawer opens
on it, still under its **GEO readiness** tab.

The **GEO readiness** tab carries a red number when the tabs inside it found problems: it is the
total of their own numbers. Each tab, here and inside the section, has a tooltip saying what it is
for.

Nothing in this section changes your page: it only reads and reports. The drawer closes with
Escape, its close button, the **Content governance** button again, or a click anywhere outside it:
the page under edit, the content tree, or another panel's button.

If you do not see **GEO readiness**, a site administrator switched it off for this site in
**Site Settings > Content governance**. A single tab can be switched off the same way, and is then
simply absent.

A companion guide covers the site-wide screen: [`dashboard-guide.md`](dashboard-guide.md).

## What happens when you click Run check

The server asks for your published page **sixteen times**: once pretending to be an ordinary web
browser, then once as each of the fifteen AI crawlers it knows about. It compares what they each
received.

Two things make this different from looking at your page in a browser.

- **It asks as a stranger.** No login, no cookies. If a visitor with no account cannot read the
  page, no crawler ever will.
- **It reads the page before any JavaScript runs.** That is all most AI crawlers ever see. A page
  that fills itself in after loading looks perfect to you and empty to them.

While it runs the button reads *Checking, one request per crawler…*. Afterwards it becomes
**Run again**, with the date of the last check beside it. The last result for each page and
language stays in your browser, so opening the drawer again shows it at once; **Run again** fetches
everything anew.

If the page has never been published you get a single message, *This page is not published*, and
no check tabs, because there is no public address to test yet. Publish it, then run the check.

## Coloured banners come first

Some things are facts about the page rather than findings, so they sit above the tabs. You may see
none of these, or several.

| Banner | What it means |
|---|---|
| No visitor can read this page | The page is closed to anonymous visitors. Everything below is moot: no crawler will ever get in. If that is deliberate, fine. If not, fix this first. |
| This page is set to noindex | The page itself asks search engines and crawlers to ignore it. |
| This page is not in the sitemap | Nothing tells crawlers the page exists. They can only find it by following a link. |
| This page is in the sitemap and marked noindex | Your own two files contradict each other: one advertises the page, the other says to ignore it. Pick one. |
| The last site scan could not reach the sitemap | The scan could not fetch the sitemap, so whether this page is in it is unknown. It names the error; it is a connection problem, not a finding about the page. |
| Nothing links to this page | No other published page links here and the sitemap does not list it, so a crawler has no way to find it. |
| Only the menu links to this page | Navigation reaches it, but no page links to it from its content. A weaker signal for crawlers that weigh links. |
| This page does not exist in every language | The page is missing in one or more of the site's languages. |
| A translation of this page is not published | The translation was written but never published, so nobody can read it. |
| Address warnings | The page has an address filed under a language the site does not serve, answers on several addresses, or its canonical points somewhere unexpected. The banner title names which. |
| The sitemap's date for this page is wrong | The sitemap's "last changed" date disagrees with the real one. |

Only the first two come from the check itself. The others are read from the last site scan, never
fetched by the drawer, and the sitemap banners say the date of that scan: run a new scan from the
dashboard when the sitemap changed since. These scan facts, like the context lines of the Score tab
and the template line, appear only for people who hold the **publish** permission on the site, the
same people who can open the dashboard.

A tab showing a red number has that many problems inside it. A tab with no number has nothing that
needs you.

## Tab 1 - Score

**What it answers.** "Overall, is this page in good shape, and what exactly is wrong with it?" It
is a count of checks that passed, not a mark out of a hundred, so you can disagree with one line
rather than with a number you cannot see inside.

**What you see.** A headline such as *16 of 18 checks passed*, red if anything critical failed,
amber if something important did, green if all is well. Then context, then the checks in three
groups.

| Group | Asks |
|---|---|
| Can a crawler reach it | Can they get the page at all, does robots.txt agree with what the server does, can a visitor with no account read it |
| Is what arrives usable | Is there real text before JavaScript, one clear heading, a title of sensible length, a description, a language, a date, alt text on images |
| Site-level files | Are `robots.txt` and `llms.txt` being served at all |

**Reading a failed check.** Each failed line tells you what to do about it and carries a severity:
*Critical* means an AI crawler cannot read the page properly; *Important* means it can, but less
well than it should; *Advisory* is worth doing but not urgent. On the right you often see the
actual value that was read, such as the length of your title.

**The context lines.** Above the checks: how this page compares with the site average and with its
own section (*This site averages 78%*, *the news section averages 64%*), how many pages link to it
from navigation and from page content, and whether it appears in `llms.txt`. A score means nothing
on its own; these say whether the page is the problem or the site is.

### Not yours to fix

Some failed checks carry a line like *"Comes from the template "article", which renders 240 pages.
Fixing it there fixes all of them."* The same thing fails on nearly every page using that template,
so it is not something you did and not something you can fix in your page. Send it to whoever owns
the template, where one change fixes all of them.

### One deliberate omission

Being disallowed in `robots.txt` is **not** counted as a failure. Telling a crawler to stay away is
a legitimate decision. What *is* counted is your file and your server disagreeing, because then one
of the two is wrong.

## Tab 2 - Crawler access

**What it answers.** "Which crawlers got my page, and did they get the same thing a browser got?"

**The verdict at the top.**

| You see | It means |
|---|---|
| The server could not reach the page for N crawlers. | No answer came back at all (connection refused, timeout, certificate, unknown host), and the error is named. A connection problem on the Jahia server, not a refusal by the site: on Jahia Cloud, for example, a server that cannot reach its own public address. The Score tab then marks "The server could fetch the page" as failed and does not judge whether crawlers are refused. |
| N crawlers are blocked. | The server answered, but not with the page: a refusal, a missing page or an error status. |
| Even a browser receives almost no text before JavaScript runs | The page is an empty shell until JavaScript fills it in. Every crawler sees nothing. The most serious result on the tab. |
| Some crawlers receive far less text than a browser. | Crawlers got under half the words a browser got. Part of your content needs JavaScript. |
| Every crawler we tested can read this page. | Nothing to do. |

**The list of crawlers.** One row each, with a green or red status (200 is the healthy one), how
long it took, and how many words arrived. Underneath, five marks that are green when present and
grey when not: **H1 heading**, **Meta description**, **Canonical URL**, the number of **Links**, and
**Structured data**. A row may also be marked *far less text*, meaning that crawler got far less
than the browser did.

**The two sentences that matter most.** At the bottom you may get one of these, and they decide
*who* fixes the problem.

- *"Your robots.txt allows N of these crawlers, but the server refused them anyway."* Something
  between the crawler and your site is blocking them, usually a firewall or bot protection. Nothing
  you write in a page or in robots.txt will help. This goes to whoever runs the infrastructure.
- *"Your robots.txt tells N of these crawlers not to fetch this page."* The opposite: they could
  read it, but your own file asks them not to. If you want them in, change the file.

## Tab 3 - Site files

**What it answers.** "What do the two files that speak for the whole site currently say?" These
belong to the site, not to your page, so this tab is the same whichever page you opened it from.

**robots.txt.** The file that tells crawlers where they may go. A headline says how many AI
crawlers it *names* and how many of them cannot fetch this page, then a table with one row per
crawler.

| Column | Means |
|---|---|
| Declared | **By name** - the file mentions this crawler specifically. **Wildcard** - it is only covered by the catch-all rule. |
| For this page | **Allowed** or **Disallowed**: what the file actually permits for this crawler on this page. |
| Matching rule | The exact line that decided it, so you can see why. |

When the server could not fetch a file at all, the tab says so and names the error (connection
refused, timeout, certificate, unknown host) rather than reporting the file as missing: a missing
`robots.txt` would mean every crawler is allowed, which nobody checked.

**llms.txt.** A newer, simpler file: a plain summary of the site for AI tools. If it exists you see
its title, whether it has a summary line, and how many sections and links it contains. If the
server returns a web page instead of the file, that is called out, because a crawler asking for the
file gets something it cannot use.

**What to do.** Nothing here is edited here. Both files are managed on the site dashboard under
**Additional > SEO > GEO readiness**, and changing them affects every page. If a crawler you want
is turned away, that is a conversation to have, not a change to your page.

## Tab 4 - Structured data

**What it answers.** "Can a machine tell what kind of thing this page is?" Structured data is a
small block of hidden text that says, in a standard vocabulary, "this is an article, published on
this date, written by this person". Crawlers rely on it heavily.

**What you see.** One of three things.

- *"This content type is not mapped yet."* Nobody has said what kind of thing this type of content
  represents. That is a one-off setup job on the dashboard's **Structured data** tab, not a
  per-page one.
- A type and a verdict, such as *schema.org Article* with either "everything it needs has a source
  in the content model" or "N required properties have no source". Missing ones are listed, with
  where each value that was found came from.
- *"This would contradict the page."* The strongest signal on the tab: the data would say something
  the page contradicts. Wrong structured data is worse than none, so this is said loudly.

**What to do.** The tab shows the finished snippet with a **COPY SNIPPET** button. Nothing is ever
written into your page automatically: somebody places it, usually once in a template rather than
page by page. If properties are missing, the usual fix is to fill in the matching fields on the
content itself.

## Tab 5 - AI citations

Shown unless a site administrator switched it off. It stays greyed out, with a tooltip saying why,
until a site administrator connects the site to BotRank in **Site Settings > Content governance**,
on the **BotRank** tab. It opens without running the check: its figures come from the site's daily
sync, not from the button.

**What it answers.** "Do AI answers quote this page, for which questions, and against whom?"

**What you see.**

- When the data dates from, and when the site last synced.
- How many times AI answers cited the page over the last 30 days, compared with the 30 days
  before. When no tracked question ran in the previous 30 days, no comparison is shown: a "+14"
  against a period nobody measured would be meaningless.
- Its rank among the site's cited pages, and the AI engines that cite it.
- Citations per day. Read the trend: the same question gets different answers from one run to the
  next.
- The tracked questions that cite it, with the AI engines behind each one, and the themes those
  questions belong to. Each question and theme shows its own change on the previous 30 days, under
  the same rule as the page.
- The other pages the same answers cite, five at first and the rest one click away, each as a card:
  in how many of the answers they share it is listed before this page (orange when that is half or
  more), both average positions among the sources (1 is the first source) with which one ranks
  higher, and how often it is cited overall.
- *Not cited yet* when no tracked answer ever cited the page, or *Not cited recently* when it was
  cited before the last 30 days.

**What to do.** The themes say what AI engines think the page is about; if they surprise you, the
page may explain something else than you meant. The pages cited alongside show what the engines
prefer to quote on the same questions. The tracked questions themselves are set up in BotRank, not
in Jahia.

## Which problems are actually yours

| If you see | Who fixes it |
|---|---|
| Missing description, thin text, no heading, no alt text, missing translation | **You.** These are content fields on the page. |
| "Comes from the template X" | **Whoever owns the template.** Fixing it once fixes every page using it. |
| "The server refused them anyway", a 403, crawlers blocked | **Infrastructure.** A firewall or bot-protection rule. No content change will help. |
| "The server could not reach the page" | **Whoever runs the Jahia server.** A connection problem between Jahia and the site's public address. |
| "Even a browser receives almost no text" | **Developers.** The page needs to be built on the server rather than in the browser. |
| robots.txt or llms.txt wording | **Whoever owns the site settings.** It affects every page, so it is not a per-page decision. |

## Terms you will meet

| Term | Means |
|---|---|
| AI crawler | A program that fetches your pages so an AI assistant can read or cite them. GPTBot, ClaudeBot and PerplexityBot are three of the fifteen checked here. |
| Initial HTML | The page exactly as the server sends it, before any JavaScript runs. Most crawlers never see more than this. |
| robots.txt | A file at the root of the site telling crawlers where they may and may not go. Polite crawlers obey it; it is not a lock. |
| llms.txt | A newer file offering AI tools a plain-language summary of the site and its key pages. |
| Canonical URL | A line in the page saying "this is my real address", so the same content on several addresses is not counted several times. |
| noindex | An instruction in the page asking search engines and crawlers to leave it out of their results. |
| Structured data | Hidden, standardised facts about the page ("article", "published on", "written by") that machines read directly. |
| Sitemap | A list of every page on the site, published for crawlers so they do not have to discover pages by following links. |

---

The drawer checks one page, live, each time you run it. The site dashboard covers the whole site
and is based on the last full scan, so the two can disagree if that scan is old. For the exact
definition of every check, read [`checks.md`](checks.md).
