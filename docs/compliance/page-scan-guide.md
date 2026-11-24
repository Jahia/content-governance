# The Compliance section of the drawer, for editors

The **Compliance** section of the **Content governance** drawer checks the page you are editing, in
the language you are editing, against the term rules of the site: the rules of the check
`pattern`, such as the terms of the legal team or the glossary of the brand. It is informational: it tells you what to fix before publishing, it does not stop you publishing.

## Open it

1. Open the page in Page Builder (or select it in jContent).
2. Click **Content governance** in the header. A drawer opens on the right, beside the page.
3. Click the **Compliance** tab. The scan runs the first time the section shows.

The drawer opens on its first section the site uses, usually **Page quality**. When Compliance is
the only section the site uses, the drawer opens on it, still under its **Compliance** tab.

Once the scan has run, the **Compliance** tab carries a red badge with the number of uses found (no
badge when there are none). Switching to another section and back keeps the result. Closing the
drawer drops it: the next time you open the section, the scan runs again.

## When it is not there, or greyed out

| You see | Why |
|---|---|
| No **Content governance** button | The module is not enabled on the site, every section is switched off, or the selected content is neither a page nor a content item with its own URL |
| No **Compliance** tab | A site administrator switched the domain off in **jContent > Additional > Rules & domains** |
| **Compliance** greyed out, with "The compliance scan reads a page and what it shows: open a page to use it." on hover | The selected content is not a page (an article or news item shown on its own URL, for one). The site dashboard covers those: [`dashboard-guide.md`](dashboard-guide.md) |
| **Compliance** greyed out, with "The compliance scan is for people who may edit this page." on hover | You cannot edit this page |
| "No section of Content governance can be used on this content." | Compliance is the only section the site uses, and one of the two reasons above applies |

## The three results

| Result | Meaning |
|---|---|
| **Compliant** | Term rules apply to this language and no text of the page breaks one |
| **Not compliant** | At least one term is used. The banner **Do not publish this page yet** asks you to replace or remove each term first |
| **Not checked** | No term rule applies to this language, so nothing was checked. The banner **No term rule for** (language) says how to create one. Ask the person who maintains the terms |

The summary line counts the uses and the contents that hold them. **Rule folders applied:** lists the
folders of the rules, with their number of rules. Click a folder to open it in Content Editor.

## Reading an issue

Issues are grouped by content. For each content:

- its name, type and path, and **Edit**, which opens it in Content Editor. The drawer closes while
  you edit; when you close Content Editor, the drawer opens again on the Compliance section and the
  scan runs again;
- each use: the field ("Text", "Title"), the sentence with the term highlighted, then **Term**,
  **Reason** (the advice of the rule), **Write instead** (the replacement of the rule), **Rule** (its
  id) and **Folder**. A term that two folders share is listed once for each rule, with the advice of
  each rule.

A content can carry a second label:

| Label | What it means for you |
|---|---|
| *(none)* | Stored on this page: fixing it here fixes it for this page, published with the page |
| **Reused content** | Shown here through a content reference. Editing it changes every page that reuses it, and it is published on its own |
| **Shared content** | Stored on another page (the header and footer on the home page) or shown by a listing. Editing it changes every page that shows it; it is published with the page that stores it, or on its own when it comes from a listing |

The line under a labelled content says where it comes from (the reference, or the page that stores
it).

## What the scan reads

Everything a visitor can read on the page in this language: the page title and SEO description, every
text, text area and rich text of the content on the page, the content it reuses, the header and
footer, and the items that listings show. Choice lists, links and other technical values are not
read. Images are not read (their alternative text included).

**Scan again** reruns the scan, after you edit content without the Edit button for instance.

Opened from jContent instead of Page Builder, the scan cannot see the items listings show: the banner
**Listings not included** says so. Open the page in Page Builder to include them.

## Other messages

| Message | What to do |
|---|---|
| **Scan stopped at its limit** | The page holds more content or issues than one scan reads (5,000 contents, 1,000 issues). Fix the issues listed, then scan again |
| **The scan did not run** | The page could not be scanned. Check that you can still edit it, then scan again |
