# The site dashboard

The Compliance section of the drawer answers for one page. The dashboard answers for the whole site:
which terms are used, how often, and where.

Open it in jContent under **Additional > Compliance**. The page is titled **Site compliance**. It
scans the site in the language selected in jContent's language switcher; switch language (or site)
and it scans again in that language. The scan runs when the page opens.

Who sees it:

- the entry is listed for people with access to jContent on the site (`jContentAccess`), on sites
  that enable the Content Governance module;
- when a site administrator switched the Compliance section off in **Site Settings > Content
  governance**, the entry stays listed but shows **Switched off for this site** instead of the
  report, and the server refuses the scan.

## The summary card

- **Compliant**, **Not compliant** or **Not checked** (no glossary for this language), and the number
  of uses on how many pages and content items.
- How much was read ("Pages read: ... Contents read: ..."), and the glossaries applied, under
  **Glossaries applied:**; click one to open it in Content Editor.
- A warning when pages or content items could not be read (a missing content type, an external
  provider down): they are left out of the report and the Jahia log names them.
- A warning when the site is larger than one scan reads (20,000 contents or 5,000 issues): the report
  then covers the first part of the site only.

When no glossary exists for the language, the card says how to create one and the two tables below
are not shown.

## Terms found

One row per term and glossary (the glossary is named under the term when several apply): the
**Reason** with the suggested wording (**Write instead**), the number of **Uses**, and the number of
**Pages and items** that use it. The most used terms come first: they are where a wording change pays
off most.

## Pages and content items with issues

One row per page, then one per content item of the content folders (articles, news items), with
their number of issues. Expand a row to see each issue: the content and field, the sentence with the
term highlighted, the term and the reason (and the glossary when several apply).

- **Open in Page Builder** opens a page, where the Compliance section of the drawer gives the full
  detail.
- **Edit** opens a content item, or the content holding one issue, in Content Editor.
- Ten rows per page by default (**Rows per page**: 10, 20 or 50); the pagination is under the table
  when there are more than ten rows.

Each content counts once, where it is stored: the header and footer count for the home page, not for
every page that shows them, and reused content counts where the original is stored. That is why the
total can be lower than the sum of the page scans.

Content items of the content folders are scanned here only: the drawer's Compliance section reads
pages, and is greyed out on an article or news item opened on its own.

## Exports

Both buttons are in the page header.

- **Export CSV**: one row per issue, with the page or content item, the content, the field, the term,
  the matched text, the sentence, the reason, the suggested wording, the glossary name and its path.
  It opens in Excel with accents intact (UTF-8 with a byte order mark). A cell that starts with `=`,
  `+`, `-` or `@` gets a leading apostrophe, so a spreadsheet never runs it as a formula. Not
  available when no glossary exists for the language.
- **Export JSON**: the whole report as the dashboard received it, with the time of the scan.

File names carry the site, the language and the date: `compliance-<site>-<language>-<date>.csv`
(`.json` for the JSON export).

**Scan again** reruns the scan; the dashboard does not refresh by itself.
