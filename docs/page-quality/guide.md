# Page quality: user guide

**Page quality** is the first section of the **Content governance** drawer. It checks the page you
are editing before you publish it: accessibility, SEO, speed, readability, eco-design, links and
Jahia-specific problems such as missing translations. When a server administrator has configured an
AI model, it can also write suggestions for you to copy into the page.

Everything runs in your browser, on a preview of the page in the language you are editing. The
preview shows the editing version of the page, so it includes changes you have not published yet.
Nothing in this section changes your content: you read the findings, then fix them in Page Builder
or Content Editor.

## Open it

1. Open a page in Page Builder or jContent. The button also appears on a content item that has its
   own URL, such as an article.
2. Click **Content governance** in the header.
3. The drawer opens on the right. **Page quality** is the first section tab.

The first time, the section shows "Loading page preview…" then "Analyzing page…". The analysis
waits for the page to settle before it measures, so expect several seconds.

A page that keeps loading (an image, a font or a script that never answers) is audited after 20
seconds as it stands. A check that takes too long (Accessibility 60 seconds, Links 45, Jahia 30, Web
Vitals 15) or fails is stopped: its tab says "This check did not finish" or "This check failed" with
a **Re-run** button, and the other tabs show their results. Such an audit is not kept for the next
opening.

Close the drawer with the **Close panel** button, the Escape key, or a click outside it. Results are
kept: the next time anyone opens the drawer on the same page and language, the newest audit comes
back at once, whoever ran it.

The number on the **Page quality** section tab is the total of the badges of its tabs.

### The language that is checked

The section checks the page in the language currently selected in jContent or Page Builder. There
is no language picker in the drawer. To check another language, switch the language in jContent or
Page Builder and open the drawer again. Each language has its own results.

## The toolbar

| Control | What it does |
|---|---|
| **Re-run** | Loads the page again and runs every check from scratch. Use it after you have fixed something. It also clears the AI answers of the previous run. |
| **Export JSON** | Downloads the results of the automated checks as a JSON file (`content-governance-audit-<page name>.json`), with the page path, language and date. AI answers are not included. Useful to attach to a ticket for a developer. |
| **Hide preview** / **Show preview** | Folds the page preview away to give the results the full height of the drawer. The page stays loaded, so highlighting still works and reopens the preview. |
| "Last audit: date by user" | When the results shown were produced, and who ran the audit. |
| "Issues over the last N audits: 12 → 9 → 7" | The total of the badges at each of the page's last audits (up to 10), oldest first. Hover a number for its date and who ran it. Shown from the second audit. |

Under the toolbar is the page preview. In most tabs, a **Highlight in preview** button scrolls the
preview to the element concerned and draws a red outline around it.

If the page was changed after the results were produced, a warning appears: **Page modified since
this audit**, with the text "The page has been modified since this audit - the results below may be
outdated. Re-run when you are done editing." and a **Re-run** button. The old results stay visible,
so you can keep using them as a to-do list while you edit.

Every complete audit is stored on the site for its page and language, the last 10 of each, so every
editor of the site sees the newest one and the trend. An audit with a check that did not finish is
not stored. AI answers stay in your browser only, for your last 10 audits.

## The site dashboard

**Additional > Page quality** in jContent lists the latest audit of every audited page of the site, in
the language selected in jContent, most issues first: the page, when and by whom it was audited, its
issues by tab, the total and how it moved since the audit before (green when it went down, red when
it went up). **Open** goes to the page in Page Builder. **Export CSV** gives one row per page with a
column per tab; **Export JSON** gives the list as shown. Pages nobody has audited are counted in the
heading ("12 of 85 pages audited"), not listed: open them and run their audit. Anyone who may open
jContent on the site sees it; it says so when the site switched Page quality off.

## Reading the results

### Badges

A red badge on a tab is the number of issues to review in that tab (hover it: "N issues to review").
No badge means nothing to review.

| Tab | The badge counts |
|---|---|
| **Accessibility** | Automated rules the page fails. One rule can fail on many elements; it counts once. Items "to verify" are not counted. |
| **SEO**, **Web Vitals**, **Readability**, **Ecodesign**, **Links**, **Jahia** | Recommendations in the tab's **Recommendations** list. |
| **AI review** | Recommendations of the AI review, once you have generated one. |

Some problems show in two tabs, because they matter for both: heavy pages, oversized images and
images without width and height appear in **Web Vitals** and **Ecodesign**; images without alt text
appear in **SEO** and **Accessibility**. The section total counts them twice.

### Severity

Each finding carries one of four levels. Fix them in this order.

| Level | Meaning |
|---|---|
| **Critical** | Visitors are blocked or the page is broken for them: a broken link, a page hidden from search engines, raw technical keys on screen, a content element some visitors cannot use at all. |
| **Serious** | A real problem for many visitors or for search engines: unpublished content, missing translations, a missing meta description, a wrong page language. |
| **Moderate** | Worth fixing, with a smaller effect. |
| **Minor** | Good practice. |

The **Accessibility** tab also uses **To verify**: the checking engine suspects a problem but a
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

## The tabs

A site administrator can switch tabs off for a site. A tab switched off is not shown.

### Accessibility

Runs axe-core, an automated accessibility checker, against the WCAG A, AA and AAA rules and its own
best-practice rules.

- **Four cards: WCAG A, WCAG AA, WCAG AAA, Best practice.** Each shows the share of the automated
  rules that apply to this page and pass ("automated rules passed"), with the number of rules, or the
  number of violations and the elements they affect. A card showing "-" and "No applicable rule" had
  nothing to test. A 100% card means every automated rule passed, not that the page is compliant.
- **Violations**, most severe first. Each line shows the severity, the WCAG level, the rule and the
  number of elements. Open it to see what the rule checks (with a link to its documentation), the
  HTML of up to 25 failing elements, what is wrong with each, and **Highlight in preview**.
- **Automated checks needing human review**: cases marked **To verify**. Look at each one.
- **Manual checklist (not automatable)**: 14 WCAG criteria no tool can judge, such as keyboard use,
  meaningful alt text, captions, focus order, 200% zoom. Review them once per page type.
- **AI alt text**: see [AI assists](#ai-assists).

### SEO

- **Recommendations**: missing or badly sized title (aim for 30 to 60 characters), missing or badly
  sized meta description (50 to 160), page excluded from search engines (`noindex`) or links not
  followed (`nofollow`), no canonical URL, incomplete Open Graph tags, invalid or missing structured
  data, missing or wrong `lang` attribute, images without alt, generic link texts such as "click
  here" or "read more".
- **AI suggestions**: see [AI assists](#ai-assists).
- **Social sharing preview**: roughly how the page looks when shared on a social network or in a chat
  tool, from its `og:title`, `og:description` and `og:image` (or its title and description).
- **Tag checklist**: each tag with a tick or a cross and its current value.

Title, description and social fields are set in the page's SEO properties; how they are named
depends on your site's templates. Canonical, structured data, `lang` and `hreflang` come from the
templates.

### Web Vitals

Measures how fast the preview loads.

| Metric | What it is | Good | Poor |
|---|---|---|---|
| **LCP** | Time until the main content (usually the largest image) is shown | up to 2.5 s | over 4 s |
| **CLS** | How much the layout jumps while loading | up to 0.1 | over 0.25 |
| **TTFB** | Time until the server starts answering | up to 800 ms | over 1.8 s |
| **DOM ready**, **Full load** | Page structure ready, everything loaded | no band | no band |
| **INP** | Reaction time to a click | shown as "n/a" | |

Between good and poor, the card says **Needs improvement**. Below the cards: **Recommendations**,
**Diagnostics** (requests, page weight, DOM nodes, images without width and height, oversized
images) and **Largest resources**.

These are lab values from one load of the preview on your machine and network. They differ from what
visitors experience. LCP is marked "estimated" with a "≈": the browser does not report it inside the
preview frame, so it is approximated from the largest image in view, or from DOM ready when there is
none.

What you can act on: upload images at the size they are shown, prefer fewer and lighter embeds.
Server response, scripts and styles are a developer's.

### Readability

Measures how easy the main text is to read. Navigation, header and footer are left out.

- **Readability score**, from 0 to 100, higher is easier, with the formula made for the page
  language: Flesch (English), Kandel-Moles (French), Amstad (German), Fernández Huerta (Spanish),
  Franchina-Vacca (Italian), Douma (Dutch), Martins (Portuguese). All of them use Flesch's scale. Bands: **Very easy** (90 and up), **Easy** (70 to 89), **Standard** (50 to 69),
  **Difficult** (30 to 49), **Very difficult** (below 30). Aim for 50 or more for a general audience.
- **Grade level** (Flesch-Kincaid, English only) and **Reading time** (at 200 words a minute).
- **Recommendations**: hard text, sentences over 25 words, dense paragraphs (over 80 words on
  average), not exactly one H1, skipped heading levels (H2 then H4), less than 300 words.
- **Text statistics** and **Structure**.
- **AI plain-language rewrites**: see [AI assists](#ai-assists).

Under 30 words, the tab shows "Not enough text on this page to compute readability". A page in a
language without a formula here (Russian, Polish, Arabic...) gets no score rather than another
language's: the tab says so, and sentence length, paragraphs and headings are still checked. Japanese,
Chinese, Thai and other languages written without spaces between words get neither a score nor a word
count: the tab counts characters and sentences, and checks the headings. The H1 count covers the whole
page, including the template.

### Ecodesign

Page-level checks inspired by the French eco-design referential RGESN 2024.

- Cards: **Page criteria met** (out of 10), **Page weight** (MB), **Requests** with the number of
  third-party origins.
- **Recommendations**.
- **Automated page criteria**: the 10 checks with their value.

| Check | Passes when |
|---|---|
| Page weight | 1 MB or less (poor above 2.5 MB) |
| HTTP requests | 40 or fewer (poor above 70) |
| DOM nodes | 1,000 or fewer (poor above 1,500) |
| Below-the-fold images not lazy-loaded | none |
| Web font files | at most 2, all WOFF2 |
| Autoplay media | none |
| Legacy-format heavy images | no JPEG, PNG, GIF, BMP or TIFF over 30 kB |
| Oversized images | no image more than twice as wide as displayed |
| Images without dimensions | none |
| Third-party origins | 5 or fewer |

- **Manual checklist (whole-service, not automatable)**: 8 criteria about strategy, hosting, backend
  and features, for the team responsible for the platform.

This is not an RGESN conformity score. RGESN assesses a whole service, most of it outside a single
page. The figures come from the editing version of the page and are indicative. The thresholds
differ from **Web Vitals**: requests are flagged above 40 here and above 80 there, and page weight
becomes serious above 2.5 MB here and above 2 MB there.

Yours: image formats and sizes, autoplaying videos, embeds you added. A developer's: fonts, lazy
loading in templates, third-party scripts.

### Links

- **Link summary**: total links, internal links (how many were verified, skipped or not verified
  because of the limit) and external links.
- **Broken links**: each with its HTTP status (or "error" when the request failed), its text, its
  URL and **Highlight in preview**.
- **Recommendations**: broken internal links, absolute URLs hardcoded to this host (they break when
  the site moves to another domain: use an internal page link instead), `http://` links on an
  `https` page, `target="_blank"` links without `rel="noopener"`.

Only internal links to pages and files are requested (paths under `/cms/render`, `/cms/file` and
`/files`), at most 60 different URLs, with your own session. Login, logout and action URLs are never
requested, and neither is any other internal path: they are all counted under "skipped
(login/logout/actions)". External links are counted but cannot be verified from the browser: "external
links are not verified (browser cross-origin restriction)".

Because the check runs as you on the editing version, a link to a page you can see but visitors
cannot (not published yet, restricted) passes here.

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
displayed from elsewhere (a listing, a reused item) is not checked. If the repository query fails,
the tab says **Repository checks could not run** and keeps the on-page checks.

### AI review

Shown only when a model is configured (see [Greyed out](#greyed-out-missing-or-failing)).

Click **Generate AI review**. The page text, its title, meta description and a summary of all the
findings above go to the configured provider. After a few seconds you get:

- **Overall assessment**: two or three sentences.
- **Recommendations**, most important first (the model is asked for 15 at most), each with a
  severity and a category: Content, SEO,
  Accessibility, Performance, UX, Proofreading, Factuality, Consistency, Conversion, Localization,
  Legal, Ecodesign. Proofreading, factuality (outdated dates, contradictions), consistency,
  conversion, localization and legal wording are things no automated check covers.
- When a recommendation is about specific words, the quoted text with **Highlight in preview**, and
  often a **Suggested fix** with **Copy**.

The assessment and recommendations are written in your jContent interface language; quotes and
suggested fixes are in the page language, since they would be published. **Regenerate** asks again.

## AI assists

When a model is configured, three tabs offer suggestions next to their findings. They share these
rules:

- **You start them.** Nothing is sent until you click.
- **Nothing is written to the repository.** Each suggestion has a **Copy** button: paste it in
  Content Editor yourself.
- **Content is in the page language, explanations in yours.** Titles, descriptions, alt texts,
  labels and rewrites are in the language being checked. The one-line reasons follow your jContent
  interface language.
- **Review before you paste.** Every answer ends with "AI-generated suggestions: review before acting
  on them." The model can be wrong; it is told to use only what the page says, but check names,
  numbers and facts.
- Each answer shows the provider and model, the tokens used and, when the administrator set prices,
  an estimated cost.
- Answers are kept with the results until the next **Re-run**.

| Assist | Where | Button | What you get |
|---|---|---|---|
| **AI suggestions** | **SEO** | **Suggest with AI** | 2 or 3 title tags, 3 meta descriptions, an `og:title` and `og:description` for social sharing, a focus keyword with supporting terms, heading rewrites (current, suggested, reason), and for each generic link or button label ("Read more", "Click here", "Submit"...) 2 or 3 specific labels. Character counts turn to a warning colour outside the recommended length. |
| **AI alt text** | **Accessibility**, when images have no alt | **Suggest alt text** | For up to 8 images without alt, largest first: a thumbnail, a suggested alt (125 characters at most), or "Decorative - use an empty alt", a reason, and **Highlight in preview**. |
| **AI plain-language rewrites** | **Readability**, when the page has long sentences | **Rewrite the hardest sentences** | The 6 longest sentences (22 words or more, in paragraphs, list items, tables and quotes of the main content), each with a rewrite, word counts before and after, a reason and **Highlight in preview**. |

Where each suggestion goes:

- Titles, descriptions and social fields: the page's SEO properties.
- Headings and link or button labels: the component that holds them, in Content Editor. Headings are
  content, not SEO properties.
- Keywords: guidance for your writing. Search engines ignore a keywords meta tag.
- Alt text: the alt field of the image in Content Editor.
- Rewrites: paste over the original sentence.

**Pictures or context only.** Anthropic and OpenAI models see a small copy of each picture. A
DeepSeek model reads text only, so its alt texts are guessed from the file name, caption, nearby
heading and text; the tab says so. Even with Anthropic or OpenAI, a picture served from another
domain cannot be read by the browser and is described from context only ("described from context
only"). To know which case applies, a site administrator can look at **Reads images (alt text)** in
**Site Settings > Content governance**, **AI provider** tab.

If more images or sentences are waiting than one request takes, the tab says how many were not sent:
fix the first ones, **Re-run**, then ask again.

## Greyed out, missing or failing

| What you see | Why | Who to ask |
|---|---|---|
| **AI review** tab greyed out, tooltip "No AI provider is configured on this server." | No model is configured on the server. | A server administrator. |
| An AI assist box with a greyed button and "AI is not configured on this server..." | Same. | A server administrator. |
| A tab missing | A site administrator switched it off for this site. The AI assists follow their own tab: with the **AI review** tab switched off, the SEO, alt text and plain-language assists still work. | A site administrator, in **Site Settings > Content governance**, **Features** tab. |
| **Page quality** missing from the drawer | The section is switched off for this site. | A site administrator. |
| No **Content governance** button | The module is not enabled on this site, every section is switched off, or the item is not a page or a content item with its own URL. | A site administrator. |
| **Analysis failed** with "HTTP 404" | The page does not exist or does not render in this language. | Check the language; otherwise a developer. |
| **Analysis failed** with "HTTP 500" or another error | The page fails to render. | A developer. |
| "You have reached the number of AI requests allowed in ten minutes. Retry a little later." | You reached the limit of AI calls (30 per 10 minutes by default; a call the provider failed counts, a refused one does not). | Wait a few minutes. |
| "A site administrator has switched this feature off for this site." | The feature was switched off since the drawer opened. | A site administrator. |
| "You cannot read this page any more, so the AI cannot review it." | Your rights on the page changed since the drawer opened. | The person who manages rights. |
| "The AI model used its whole output budget before answering. A server administrator raises AI_MAX_TOKENS in the module's AI configuration." | A reasoning model spent its budget before writing the answer. | A server administrator. |
| "The AI provider did not answer. Retry in a moment; if it persists, tell a server administrator." | The provider failed or answered something unreadable. | Try again later; if it persists, a server administrator (server log). |
| "The answer was cut short by the AI_MAX_TOKENS limit..." | The answer hit the output limit; complete items are kept. | A server administrator can raise `AI_MAX_TOKENS`. |

## Limits to keep in mind

- **Accessibility**: automated rules cover a subset of WCAG. A clean result does not certify
  compliance. The manual checklist covers the rest, and only a person can judge it.
- **Ecodesign**: page-level checks only. It is not an RGESN conformity score.
- **Web Vitals**: one lab measurement of the editing version, on your machine, inside a frame. LCP is
  estimated and INP is not measured. Real visitors' figures differ.
- **Readability**: a formula based on sentence and word length, with syllables estimated from vowel
  groups. It does not judge jargon or clarity, and only English, French, German, Spanish, Italian,
  Dutch and Portuguese get a score.
- **Links**: external links are not verified. Internal links are verified as you, on the editing
  version, up to 60 URLs.
- **Jahia**: only content stored under the page, up to 400 items.
- **AI**: suggestions can be wrong and are never applied for you. The page text sent includes
  unpublished content.
- Results are stored in your browser only and are not shared with your team; use **Export JSON** to
  pass them on.
