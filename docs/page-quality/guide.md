# Page quality: user guide

**Page quality** is the first section of the **Content governance** drawer. It checks the page you
are editing before you publish it: accessibility, SEO, speed, readability, eco-design, links and
Jahia-specific problems such as missing translations.

The checks run on the editing version of the page, in the language you are editing, so they include
changes you have not published yet. The server checks SEO, readability, links and Jahia health, with
the rules that an AI agent gets from `frame.check`: one page gets the same findings in both. Your
browser checks what needs a browser, on a preview of the page: accessibility with axe-core, load
figures and eco-design.
Nothing in this section changes your content: you read the findings, then fix them in Page Builder
or Content Editor.

## Open it

1. Open a page in Page Builder or jContent. The button also appears on a content item that has its
   own URL, such as an article.
2. Click **Content governance** in the header.
3. The drawer opens on the right. **Page quality** is its first tab.

The first time, the section shows "Loading page preview…" then "Analyzing page…". The analysis
waits for the page to settle before it measures, so expect several seconds.

A page that keeps loading (an image, a font or a script that never answers) is audited after 20
seconds as it stands. A check that takes too long (Accessibility 60 seconds, Links 45, Jahia 30, Web
Vitals 15) or fails is stopped. Its section opens and says "This check did not finish" or "This check
failed", with a **Re-run** button. The other sections show their results. Such an audit is not kept
for the next opening.

The drawer sits beside the page under edit, which stays usable: drag its left edge to make it wider
(the width is remembered). Close it with the **Close panel** button, the Escape key, or the **Content
governance** button again. Results are
kept: the next time anyone opens the drawer on the same page and language, the newest audit comes
back at once, whoever ran it.

Page quality shows one section per check: **ACCESSIBILITY**, **SEO**, **WEB VITALS**,
**READABILITY**, **ECODESIGN**, **LINKS** and **JAHIA**. Press the title of a section to open or close
it. A section with a critical or serious finding opens on its own, and so does a section whose check
did not finish. The other sections stay closed. **Expand all** opens every section, and **Collapse
all** closes them. After a new audit, the sections open and close by these rules again.

The number on the **Page quality** tab is the total of the counts of its sections. It is red when one
of its findings is critical or serious, grey otherwise.

Under the tabs, **Fix first** appears when a server administrator shows it (`SHOW_FIX_FIRST` in
`org.jahia.community.contentgovernance.drawer.cfg`) and a tab measured so far holds a critical or serious finding:
it gives the most serious one on one line, with how many more there are. **Show** lists up to five of
them (critical, then serious), whatever their tab: accessibility violations and the recommendations of
every Page quality section, the GEO readiness score and crawler access, and the forbidden terms.
Moderate and minor findings stay in their sections. **Open** goes to the tab and opens the section that
explains it. GEO readiness and Compliance measure only once opened, so their findings join the list
after that.

### The language that is checked

The section checks the page in the language currently selected in jContent or Page Builder. There
is no language picker in the drawer. To check another language, switch the language in jContent or
Page Builder and open the drawer again. Each language has its own results.

## The toolbar

| Control | What it does |
|---|---|
| **Re-run** | Loads the page again and runs every check from scratch. Use it after you have fixed something. |
| **Export JSON** | Downloads the results of the automated checks as a JSON file (`content-governance-audit-<page name>.json`), with the page path, language and date. Useful to attach to a ticket for a developer. |
| **Hide preview** / **Show preview** | Hides the page preview to give the results the full height of the drawer. In Page Builder it starts hidden, since the page is beside the drawer. The preview stays loaded behind the results, so the audit measures it the same way and highlighting can still show it. Shown only where a server administrator shows it (`SHOW_PREVIEW_BUTTON` in `org.jahia.community.contentgovernance.drawer.cfg`). |
| "Last audit: date by user" | When the results shown were produced, and who ran the audit. |
| "Issues over the last N audits: 12 → 9 → 7" | The total of the badges at each of the page's last audits (up to 10), oldest first. Hover a number for its date and who ran it. Shown from the second audit. |
| **Expand all** / **Collapse all** | Opens every section of the results, or closes them all. |

Under the toolbar is the page preview, hidden at first in Page Builder. In most sections, a **Highlight
on the page** button scrolls to the element concerned and draws a red outline around it: in the page
under edit when Page Builder shows it, so you can edit it at once, otherwise in the preview, which
opens for it. An element the page under edit does not show (one added by a script, or shown on
the published site only) is outlined in the preview. The outline goes when you close the drawer.

If the page was changed after the results were produced, a warning appears: **Page modified since
this audit**, with the text "The page has been modified since this audit - the results below may be
outdated. Re-run when you are done editing." and a **Re-run** button. The old results stay visible,
so you can keep using them as a to-do list while you edit.

Every complete audit is stored on the site for its page and language, the last 10 of each, so every
editor of the site sees the newest one and the trend. An audit with a check that did not finish is
not stored.

## The site dashboard

**Additional > Page quality** in jContent lists the latest audit of every audited page of the site, in
the language selected in jContent, most issues first: the page, when and by whom it was audited, its
issues by tab, the total and how it moved since the audit before (green when it went down, red when
it went up). Expand a page (the arrow before its title) to read the findings of that audit, the most
serious first: each says what is wrong and what to do, with its severity, its tab and, for
accessibility, how many elements fail the rule. They come from the stored audit, so nothing is
measured again; to see the elements themselves, open the page and its drawer. **Open** goes to the
page in Page Builder. **Export CSV** gives one row per page with a
column per tab; **Export JSON** gives the list as shown. Pages nobody has audited are counted in the
heading ("12 of 85 pages audited"), not listed: open them and run their audit. Anyone who may open
jContent on the site sees it; it says so when the site switched Page quality off.

## Before publishing

jContent's **Publish** menu of the page you are on starts with a line from Content Governance when
there is something to review: "Review before publishing - audit issues: 12, serious accessibility: 2,
forbidden terms: 1". The counts come from the page's latest audit (all its issues, and the critical or
serious accessibility ones) and from a compliance scan of the page. A page nobody has audited yet
reads "Not audited yet - open Content governance before publishing". Click the line to open the
drawer on the section to review. It never blocks publishing, and nothing is added when the page has
no issue. The forbidden terms are counted only for editors who may edit the page, and a section the
site switched off is left out of the line.

## Reading the results

### Badges

The number left of the title of a section is the count of issues to review in that section (hover the
title: "N issues to review"). It is **red** when one of them is critical or serious, the ones to deal
with before publishing, and **grey** when they are all moderate or minor. A grey **0** means nothing
to review. A dash means the check did not finish.

| Section | The badge counts |
|---|---|
| **Accessibility** | Automated rules the page fails. One rule can fail on many elements; it counts once. Items "to verify" are not counted. |
| **SEO**, **Web Vitals**, **Readability**, **Ecodesign**, **Links**, **Jahia** | Recommendations in the section's **Recommendations** list. |

Some figures show in two sections. **Web Vitals** shows the weight, the requests and the images of the
page, and **Ecodesign** gives their findings. Images without alt text appear in **SEO** and
**Accessibility**, and the section total counts them twice.

### Severity

Each finding carries one of four levels. Fix them in this order.

| Level | Meaning |
|---|---|
| **Critical** | Visitors are blocked or the page is broken for them: a broken link, a page hidden from search engines, raw technical keys on screen, a content element some visitors cannot use at all. |
| **Serious** | A real problem for many visitors or for search engines: unpublished content, missing translations, a missing meta description, a wrong page language. |
| **Moderate** | Worth fixing, with a smaller effect. |
| **Minor** | Good practice. |

The **Accessibility** section also uses **To verify**: the checking engine suspects a problem but a
person has to decide.

### Who fixes what

Many findings come from the content you entered. Others come from the template or the components,
which are the same on every page: those are for the developer of the site. A quick test: highlight
the element. If it is something you typed, chose or uploaded, it is yours. If it sits in the header,
the footer or the page frame, or the same finding shows on every page, pass it to a developer with the
exported JSON.

| Usually yours | Usually a developer's |
|---|---|
| Image alt text, image size and format | Page language attribute, `hreflang` links |
| Page title, meta description, social sharing fields | Canonical URL, structured data (JSON-LD) |
| Link texts, broken links, hardcoded URLs in rich text | Colour contrast, ARIA attributes, keyboard behaviour of components |
| Headings inside your content, sentence length, placeholder text | Scripts, fonts, third-party services, server response time |
| Publishing and translating the page's content | Raw translation keys on the page (a missing label in a module) |

## The sections

A site administrator can switch sections off for a site. A section switched off is not shown.

Page quality calls no AI model. When the module ai-assistant is installed, the AI review of the page
is in its own **AI review** tab of the drawer.

### Accessibility

Runs axe-core, an automated accessibility checker, against the WCAG A, AA and AAA rules and its own
best-practice rules.

- **Four cards: WCAG A, WCAG AA, WCAG AAA, Best practice.** Each shows the share of the automated
  rules that apply to this page and pass ("automated rules passed"), with the number of rules, or the
  number of violations and the elements they affect. A card showing "-" and "No applicable rule" had
  nothing to test. A 100% card means every automated rule passed, not that the page is compliant.
- **Violations**, most severe first. Each line shows the severity, the WCAG level, the rule and the
  number of elements. Open it to see what the rule checks (with a link to its documentation), the
  HTML of up to 25 failing elements, what is wrong with each, and **Highlight on the page**.
- **Automated checks needing human review**: cases marked **To verify**. Look at each one.
- **Other rules of the catalog**: the accessibility rules that axe-core does not run, such as the
  HTML rules of the server or a rule of another module. Examples are a button with no name and two
  elements with the same id. Each one shows its rule id and the elements that break it.
- **Manual checklist (not automatable)**: 14 WCAG criteria no tool can judge, such as keyboard use,
  meaningful alt text, captions, focus order, 200% zoom. Review them once per page type.

### SEO

- **Recommendations**: missing or badly sized title (`seo.title`, 30 to 60 characters by default),
  missing or badly sized meta description (`seo.metaDescription`, 50 to 160), page excluded from search engines (`noindex`) or links not
  followed (`nofollow`), no canonical URL, incomplete Open Graph tags, invalid or missing structured
  data, missing or wrong `lang` attribute, images without alt, generic link texts such as "click
  here" or "read more".
- **Social sharing preview**: roughly how the page looks when shared on a social network or in a chat
  tool, from its `og:title`, `og:description` and `og:image` (or its title and description).
- **Tag checklist**: each tag with a tick or a cross and its current value.

Title, description and social fields are set in the page's SEO properties; how they are named
depends on your site's templates. Canonical, structured data, `lang` and `hreflang` come from the
templates.

### Web Vitals

Shows how the editing preview loaded, and what weighs the page down.

| Figure | What it is |
|---|---|
| **LCP** | Time until the main content (usually the largest image) is shown |
| **CLS** | How much the layout jumps while loading |
| **TTFB** | Time until the server starts answering |
| **DOM ready**, **Full load** | Page structure ready, everything loaded |
| **INP** | Reaction time to a click, shown as "n/a" |

**These figures are indicative.** The page is measured once on its editing preview, in your browser
and with your session: no cache, no CDN, an editor's rendering. They do not match the live site and
cannot be made to, so they carry no colour, no count and no finding. Use them to compare before and
after a change on the same page. For the site's real Core Web Vitals, use Google Search Console or
PageSpeed Insights on the published URL. LCP is marked "estimated" with a "≈": the browser does not
report it inside the preview frame, so it is approximated from the largest image in view, or from DOM
ready when there is none.

Below the figures, what does not depend on where the page is measured, and is the same on the live
site: **Recommendations** (images without width and height, images heavier than needed, page weight,
number of requests, DOM size, heavy scripts and styles), **Diagnostics** and **Largest resources**.
An audit run before version 1.11 no longer shows its findings on LCP, CLS or TTFB.

What you can act on: upload images at the size they are shown, prefer fewer and lighter embeds.
Server response, scripts and styles are a developer's.

### Readability

Measures how easy the main text is to read. Navigation, header and footer are left out.

- **Readability score**, from 0 to 100, higher is easier, with the formula made for the page
  language: Flesch (English), Kandel-Moles (French), Amstad (German), Fernández Huerta (Spanish),
  Franchina-Vacca (Italian), Douma (Dutch), Martins (Portuguese). All of them use Flesch's scale. Bands: **Very easy** (90 and up), **Easy** (70 to 89), **Standard** (50 to 69),
  **Difficult** (30 to 49), **Very difficult** (below 30). Aim for 50 or more for a general audience.
- Swedish, Danish and Norwegian pages get a **LIX** score instead (words per sentence plus the share of
  words longer than six letters). It reads the other way round: higher is harder. Bands: **Very
  easy** (below 25), **Easy** (25 to 34), **Standard** (35 to 44), **Difficult** (45 to 54), **Very
  difficult** (55 and up); aim for 40 or less for a general audience.
- **Grade level** (Flesch-Kincaid, English only) and **Reading time** (at 200 words a minute).
- **Recommendations**: hard text, sentences over 25 words, dense paragraphs (over 80 words on
  average), not exactly one H1, skipped heading levels (H2 then H4), less than 300 words. These are
  the defaults of the catalog of content rules.
- **Text statistics** and **Structure** (the number of H1 headings and of skipped levels).
- **Heading outline**: every heading of the main content, in page order, indented by its level, so
  you see the page as a screen reader user or a search engine does. A heading that skips a level
  says so (**Skips from H2 to H4**), a heading without text is marked **Empty**, and the eye button
  shows the heading on the page (in the page under edit in Page Builder, otherwise in the preview).
  The first 200 headings are listed. An audit run before version 1.10 has no outline: **Re-run** it.

Under 30 words, the section shows "Not enough text on this page to compute readability", then the
structure and the heading outline, which do not depend on the amount of text. A page in a
language without a formula here (Finnish, Russian, Polish, Arabic...) gets no score rather than another
language's: the section says so, and sentence length, paragraphs and headings are still checked. Japanese,
Chinese, Thai and other languages written without spaces between words get neither a score nor a word
count: the section counts characters and sentences, and checks the headings. The H1 count covers the whole
page, including the template.

### Ecodesign

Page-level checks inspired by the French eco-design referential RGESN 2024.

- Cards: **Page criteria met** (out of 11), **Page weight** (MB), **Requests** with the number of
  third-party origins.
- **Recommendations**. Each finding on an image or a media element offers **Highlight on the page**.
- **Automated page criteria**: the 11 checks with their value, each named by the label of its rule.

| Check | Passes when |
|---|---|
| Page weight | 1 MB or less |
| HTTP requests | 70 or fewer |
| DOM nodes | 1,500 or fewer |
| Below-the-fold images not lazy-loaded | none |
| Web font files | at most 2, all WOFF2 |
| Autoplay media | none |
| Legacy-format heavy images | no JPEG, PNG, GIF, BMP or TIFF over 30 kB |
| Oversized images | no image more than twice as wide as displayed |
| Images without dimensions | none |
| Third-party origins | 5 or fewer |
| Heavy scripts and stylesheets | none over 300 kB |

- **Manual checklist (whole-service, not automatable)**: 8 criteria about strategy, hosting, backend
  and features, for the team responsible for the platform.

This is not an RGESN conformity score. RGESN assesses a whole service, most of it outside a single
page. The figures come from the editing version of the page and are indicative. The limits are the
defaults of the rules `ecodesign.*`. An administrator changes a limit on the rule in the system site.

Yours: image formats and sizes, autoplaying videos, embeds you added. A developer's: fonts, lazy
loading in templates, third-party scripts.

### Links

- **Link summary**: total links to a page, internal links (how many were checked, and how many were
  not because they name no page, such as a vanity URL) and external links.
- **Broken links**: each internal link that a visitor cannot open, with its reason, **Missing** (no
  such page) or **Not published**, its path and **Highlight on the page**.
- **Recommendations**: broken internal links, absolute URLs hardcoded to this host (they break when
  the site moves to another domain: use an internal page link instead), `http://` links,
  `target="_blank"` links without `rel="noopener"`. Open **Findings** under a recommendation to see
  each link.

The server checks each internal link in the live workspace, as a visitor would open it. A link to a
page that is not published yet is broken, even though you can open it while you edit. External links
are counted, not checked. Every `http://` link is a finding, whatever the address of the page you
edit: the published site is served over `https`.

### Jahia

The checks only Jahia can do.

- **Page summary**: **Page publication status**, the number of content items analysed, the site
  languages checked.
- **Content not visible to visitors**: content items of the page that are modified, never
  published, unpublished, marked for deletion or in conflict. Publish the page to align what
  visitors see with what you edit.
- **Missing translations**: content items with text in some site languages and not in others. Open
  each one and fill in the missing languages.
- **Raw translation keys visible on the page**: text like `namespace:key.path` shown instead of a
  label. This is a missing label in a module: report it to a developer.
- **Placeholder text**: "lorem ipsum", "TODO", "FIXME", "XXX", "à compléter", "to be completed".

The check covers up to 400 content items stored under the page, and stops at sub-pages. Content
displayed from elsewhere (a listing, a reused item) is not checked. A check that another module adds
on the server, with no section of its own, shows here with its rule id and its advice.

## Not checked

A module can add a check that runs in your browser, on the preview of the page, with its own rules.
When no installed module checks such a rule, the section of the rule lists it under **Not checked**. The
rule is neither passed nor failed. Example: a site keeps the rule of a module that was uninstalled.

## Missing or failing

| What you see | Why | Who to ask |
|---|---|---|
| A tab missing | A site administrator switched it off for this site. | A site administrator, in **jContent > Additional > Rules & domains**. |
| **Page quality** missing from the drawer | The section is switched off for this site. | A site administrator. |
| No **Content governance** button | The module is not enabled on this site, every section is switched off, or the item is not a page or a content item with its own URL. | A site administrator. |
| **Analysis failed** with "HTTP 404" | The page does not exist or does not render in this language. | Check the language; otherwise a developer. |
| **Analysis failed** with "HTTP 500" or another error | The page fails to render. | A developer. |

## Limits to keep in mind

- **Accessibility**: automated rules cover a subset of WCAG. A clean result does not certify
  compliance. The manual checklist covers the rest, and only a person can judge it.
- **Ecodesign**: page-level checks only. It is not an RGESN conformity score.
- **Web Vitals**: one measurement of the editing version, on your machine, inside a frame. LCP is
  estimated and INP is not measured. The timings are indicative and raise no finding: they cannot
  match the live site.
- **Readability**: a formula based on sentence and word length, with syllables estimated from vowel
  groups. It does not judge jargon or clarity, and only English, French, German, Spanish, Italian,
  Dutch and Portuguese get a score.
- **Links**: external links are not checked. Internal links are checked in the live workspace.
- **Jahia**: only content stored under the page, up to 400 items.
- Results are stored in your browser only and are not shared with your team; use **Export JSON** to
  pass them on.
