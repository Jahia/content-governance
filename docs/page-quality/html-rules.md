# The HTML check on the server

The HTML check reads the HTML that Jahia renders for a page or for one content item, and reports
the rules that the HTML breaks. It runs on the Jahia server and needs no browser, so a scheduled
job and an AI agent can run it. One service, `HtmlCheck`, runs every check of the server on the
render. These checks are the HTML rules of this page, and the checks of SEO, readability, links and
Jahia health.

These callers use the service, and get the same findings under the same rule ids for one page:

- the GraphQL field `checkPage(path, language)`, which the drawer of **Page quality** calls;
- the tool `frame.check`, with a `path` instead of a `text`.

The GraphQL field `checkText` with the scope `PAGE` runs the same checks on an HTML text that the
caller gives. It reads nothing in the repository, so it gives no finding of broken internal links,
unpublished content or missing translations.

The tool `audit.accessibility` calls the service too, and keeps only the findings of the HTML rules
and of the rules `seo.title` and `seo.metaDescription`.

`checkPage` also returns the facts that the tabs of the drawer show, as
[`architecture.md`](architecture.md#the-server-check) states. Three checks of the drawer stay in the
browser of the editor: accessibility with axe-core, the web vitals and eco-design. They measure what
a browser loads and runs.

The GEO site scan and the GEO readiness tab of the drawer fetch the page as a crawler. They run the
SEO and HTML checks of the service on the HTML that the crawler receives. [The GEO checks](../geo-readiness/checks.md)
list them.

## How a check runs

1. Jahia renders the page or the content item inside the server, with no HTTP request.
2. The render uses the user, the workspace and the language of the call. In the `default`
   workspace, the HTML holds the changes that are not published yet.
3. Each check of the scope `PAGE` reads the HTML with the rules of the site. These checks are the
   HTML rules, the checks of SEO, readability, links and Jahia health, and the checks that
   other modules register. A page gets every HTML rule, and a
   content item gets only the HTML rules with the scope `item`.

A content item renders with its own view, as a page includes it. The item has no language, title or
main heading of its own, so the rules with the scope `page` do not run on it.

## The rules

Each rule is a rule of the [catalog](../content-rules/catalog.md), with a node under
`contents/content-insights/content-governance/rules`. A site switches a rule off, or changes its
severity, on that node. A finding gives the severity that the site gives the rule. The HTML rules
report 50 findings or fewer on one page.

| Rule | Scope | WCAG 2.2 | What the rule reports |
| -- | -- | -- | -- |
| `html.pageLanguageMissing` | page | 3.1.1 | The `html` element has no `lang` attribute. |
| `html.pageLanguageMismatch` | page | 3.1.1 | The `lang` attribute names another language than the content. |
| `html.imageAltMissing` | item | 1.1.1 | An image has no `alt` attribute. |
| `html.headingLevelSkipped` | item | 1.3.1 | A heading is more than one level below the heading before it. |
| `html.headingEmpty` | item | 2.4.6 | A heading has no text and no image with a text alternative. |
| `html.headingH1Missing` | page | 1.3.1 | The page has headings, and none of them is an `h1`. |
| `html.headingH1Multiple` | page | 1.3.1 | The page has more than one `h1`. |
| `html.linkNameMissing` | item | 2.4.4 | A link has no text, no label and no image with a text alternative. |
| `html.linkTextVague` | item | 2.4.4 | The text of a link says nothing about its target, such as "click here". |
| `html.buttonNameMissing` | item | 4.1.2 | A button has no text, no label and no title. |
| `html.formLabelMissing` | item | 1.3.1 | A form field has no label. |
| `html.frameTitleMissing` | item | 4.1.2 | An `iframe` has no `title` attribute, or an empty one. |
| `html.tableHeaderMissing` | item | 1.3.1 | A data table has more than one row and no header cell. |
| `html.duplicateId` | page | none | Two elements of the page have the same `id`. |

The rule `html.linkTextVague` knows the vague texts in English, French and German. The rule
`html.headingLevelSkipped` compares the headings inside the HTML only. On a content item, the first
heading of the item is not compared with the headings of the page.

The browser keeps the checks that need a rendering engine: colour contrast, keyboard focus, web
vitals and eco-design.

## The link check

The link check reads the links of the same HTML. It sorts each link into one of two groups.

- **An internal link** names a node of Jahia, through its path or through a host name of the site.
  The link is `missing` when its node does not exist. The link is `not_published` when its node
  exists and is not in the live workspace, because a visitor then gets a 404 page. A link with no
  JCR path, such as a vanity URL, is counted and not checked.
- **An external link** names another web site. The Jahia server sends a `HEAD` request to it, and a
  `GET` request when the site refuses `HEAD`. The server reads the status and never the body, and
  it follows no redirect.

The status of an external link gives its state.

| Status | State |
| -- | -- |
| 404 or 410 | `broken` |
| 401, 403 or 429 | `notVerified`, because a site can refuse a script |
| 500 and above, or no answer | `unreachable` |
| Any other status | `ok` |

The server never calls a private, loopback, link-local, multicast or unique local address. A link to
such an address gets the state `blocked`, so a page author cannot make the server call an internal
address. The checks of GEO readiness obey the same rule.

The server checks 100 external links or fewer for one render, and counts the others. It waits 5
seconds or less to connect to a link, and 5 seconds or less for the status.

## The tools for AI agents

The module adds two tools when the module mcp-server is installed. Without mcp-server, the module
adds no tool.

| Tool | What it returns |
| -- | -- |
| `audit.accessibility` | The findings of the rules, each with its rule and its element. A finding of an HTML rule also has its WCAG criterion and its scope. |
| `audit.links` | The broken internal links, and the external links by state. |

Each tool takes two inputs:

- `path`: the path of a page or of a content item, under `/sites/`.
- `locale`: the language of the content, such as `en` or `fr_FR`.

A tool answers only when three conditions are true:

- The user has the permission `jContentAccess` on the site.
- The site enables the module.
- The tab of **Page quality** with the same id is on for the site. Example: `audit.links` needs the
  tab `audit.links`, which a site administrator switches on or off in **Site Settings > Content
  governance**.

A site that does not exist and a site that the user cannot open get the same answer.
