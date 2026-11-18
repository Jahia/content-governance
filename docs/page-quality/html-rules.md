# The HTML check on the server

The HTML check reads the HTML that Jahia renders for a page or for one content item, and reports
the rules that the HTML breaks. It runs on the Jahia server and needs no browser, so a scheduled
job and an AI agent can run it. The checks of the **Page quality** drawer still run in the browser
of the editor, as [`architecture.md`](architecture.md) states.

Today, the two tools for AI agents call the HTML check. The drawer and a scan of the whole site do
not call it yet.

## How a check runs

1. Jahia renders the page or the content item inside the server, with no HTTP request.
2. The render uses the user, the workspace and the language of the call. In the `default`
   workspace, the HTML holds the changes that are not published yet.
3. The rules read the HTML. A page gets every rule. A content item gets only the rules with the
   scope `item`.

A content item renders with its own view, as a page includes it. The item has no language, title or
main heading of its own, so the rules with the scope `page` do not run on it.

## The rules

The rule ids are provisional. The catalog of rules of
[content-governance#70](https://github.com/Jahia/content-governance/issues/70) gives them their final
names. A check reports 50 findings or fewer.

| Rule | Scope | WCAG 2.2 | What the rule reports |
| -- | -- | -- | -- |
| `page-language-missing` | page | 3.1.1 | The `html` element has no `lang` attribute. |
| `page-language-mismatch` | page | 3.1.1 | The `lang` attribute names another language than the content. |
| `page-title-missing` | page | 2.4.2 | The page has no `title` element, or an empty one. |
| `page-description-missing` | page | none | The page has no `description` meta tag, or an empty one. |
| `image-alt-missing` | item | 1.1.1 | An image has no `alt` attribute. |
| `heading-level-skipped` | item | 1.3.1 | A heading is more than one level below the heading before it. |
| `heading-empty` | item | 2.4.6 | A heading has no text and no image with a text alternative. |
| `heading-h1-missing` | page | 1.3.1 | The page has headings, and none of them is an `h1`. |
| `heading-h1-multiple` | page | 1.3.1 | The page has more than one `h1`. |
| `link-name-missing` | item | 2.4.4 | A link has no text, no label and no image with a text alternative. |
| `link-text-vague` | item | 2.4.4 | The text of a link says nothing about its target, such as "click here". |
| `button-name-missing` | item | 4.1.2 | A button has no text, no label and no title. |
| `form-label-missing` | item | 1.3.1 | A form field has no label. |
| `frame-title-missing` | item | 4.1.2 | An `iframe` has no `title` attribute, or an empty one. |
| `table-header-missing` | item | 1.3.1 | A data table has more than one row and no header cell. |
| `duplicate-id` | page | none | Two elements of the page have the same `id`. |

The rule `link-text-vague` knows the vague texts in English, French and German. The rule
`heading-level-skipped` compares the headings inside the HTML only. On a content item, the first
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
| `audit.accessibility` | The findings of the rules, each with its rule, its WCAG criterion, its scope and its element. |
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
