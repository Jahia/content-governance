# How the Compliance section works

The Compliance section is one of the three sections of the Content Governance bundle. Its parts: a
CND (`ccs:glossary`, `ccs:glossaryTerm`), a Java service that scans, GraphQL fields that expose it, a
per-site switch checked on the server, and two React 18 entry points of the module's UI extension
(the **Compliance** section of the drawer and the jContent dashboard).

```
Drawer, Compliance section ──► complianceScan.scanPage ─┐
                                                        ├─► SiteFeatures gate ─► ComplianceScanService ─► JCR (caller's session, default workspace)
jContent dashboard         ──► complianceScan.scanSite ─┘                               │
                                                                                         └─► GlossaryReader: ccs:glossary under /sites/<site>/contents
```

## Code

Java, under `org.jahia.community.contentgovernance.compliance`:

| Package | Holds |
|---|---|
| `model` (exported) | Records of the reports: `ComplianceReport`, `SiteComplianceReport`, `ComplianceIssue`, `LocationReport`, `TermUsage`, `Glossary`, `GlossaryTerm`, enums |
| `service.spi` (exported) | `ComplianceScanService`: `scan(page, uiLocale, renderedPaths)` and `scanSite(site, uiLocale)` |
| `choicelist` | `SiteLanguagesInitializer`: `choicelist[complianceSiteLanguages]`, every language of the site (Jahia's `siteLanguage` offers the live-active ones only) |
| `service.internal` | `ComplianceScanServiceImpl` (tree walk), `GlossaryReader`, `TermMatcher` and `FoldedText` (matching), `PlainText` (rich text to text), `Excerpt` |
| `graphql` | `GqlComplianceScan` (`scanPage`, `scanSite`) and the `Gql*` views; schema names are prefixed (`ComplianceIssue`, `ComplianceScanStatus`...) |

Shared with the other sections:

| Package or folder | Holds |
|---|---|
| `org.jahia.community.contentgovernance.graphql` | `ContentGovernanceQueryExtension`, which adds `complianceScan` and `contentGovernance` to the root `Query`, and the bundle's `DXGraphQLExtensionsProvider` |
| `org.jahia.community.contentgovernance.settings` | `Features` (section and tab ids: the Compliance section is `compliance`, with no tabs) and `SiteFeatures` (the server-side gate) |
| `src/javascript/drawer/` | `GovernanceAction` (the header button, its node checks) and `GovernanceDrawer` (tabs, badges, sections mounted on first visit) |
| `src/javascript/compliance/` | `ComplianceSection.jsx`, `scan.gql.js`, `renderedPaths.js` (frame paths), `dashboard/` (dashboard, `scanSite.gql.js`, CSV and JSON exports) |
| `src/javascript/common/SectionGate.jsx` | Shows the dashboard only while the site keeps the section on |

## Content types

Declared in `src/main/resources/META-INF/definitions.cnd`, with the namespaces of Content Compliance
Scan (`ccs`, `ccsmix`) so existing glossaries carry over.

| Type | Properties |
|---|---|
| `ccs:glossary` (`mix:title`, `jmix:list`, category `ccsmix:complianceContent`) | `language` (`choicelist[complianceSiteLanguages]`, mandatory), `description` (text area); children `ccs:glossaryTerm` |
| `ccs:glossaryTerm` | `term` (mandatory, primary item, so jContent lists the term), `matchMode` (`word` by default, `prefix`, `anywhere`), `reason` (text area), `alternative` |

`ccsmix:complianceContent` extends `jmix:droppableContent`: that is what lets a glossary be created in
a content folder, and also why Page Builder offers it on pages. Labels and tooltips in English and
French in `src/main/resources/resources/content-governance*.properties`. Icons in
`src/main/resources/icons/`, rendered from the Lucide glyphs listed in `icons.json`: `book-a`
(glossary), `whole-word` (term), `shield-check` (the Compliance category).

## Where the scans are started

**Drawer.** `GovernanceAction` (header action `headerPrimaryActions`) shows the **Content governance**
button on a `jnt:page` or a `jmix:mainResource` of a site that enables the module, once the site's
settings (`contentGovernance.siteSettings`) list at least one section switched on. The Compliance tab
is listed only when `compliance` is on, and greyed out (`drawer.unavailable.notPage`,
`drawer.unavailable.noWrite`) when the node is not a `jnt:page` or the user lacks `jcr:write` on it
(`useNodeChecks`). `ComplianceSection` mounts the first time its tab is shown and runs `scanPage`
then; it reports the number of issues to the drawer, which shows it as the tab's badge. **Edit**
closes the drawer and opens Content Editor with an `onClosedCallback` that reopens the drawer on the
Compliance section, which mounts again and rescans.

**Dashboard.** An `adminRoute` on `jcontent:90` (jContent's **Additional** accordion), with
`requiredPermission: 'jContentAccess'` and `requireModuleInstalledOnSite`. `SectionGate` reads the
site's settings and shows "Switched off for this site" instead of the dashboard when `compliance` is
off: the route is registered once for every site, so the entry stays listed. The dashboard remounts
on a site or language switch and runs `scanSite`.

## What a scan reads

Both scans read the **default** workspace in one content language, with the session of the user who
asks. Nothing is written.

**Text properties.** A `STRING` property whose editor is text, text area or rich text, with no value
constraint (choice lists and tokens are technical values) and not hidden. `jcr:title` and
`jcr:description` are read; every other property of the `jcr`, `j`, `jmix`, `jnt`, `mix`, `nt` and
`rep` namespaces is not. Rich text goes through `PlainText.fromHtml`: scripts, styles and comments
dropped, block tags turned into spaces, inline tags removed (so `<b>gra</b>tuit` stays one word),
entities decoded. System children (`j:translation_*`, `j:acl`, vanity URLs) and the glossaries
themselves are skipped.

**Page scan** (`scanPage`), what the page shows:

| Origin | Read |
|---|---|
| `PAGE` | The page and every node under it, sub-pages excluded |
| `REFERENCE` | The target of each `jmix:nodeReference` (`j:node`) met on the way, with its tree |
| `SHARED` | The `jmix:list` children (areas) of each ancestor page up to home for which the page has no child of the same name: the header and footer on home |
| `SHARED` | The `renderedPaths` the Compliance section collected from the Page Builder frame (`iframe[data-sel-role="page-builder-frame-active"]`, when its `[jahiatype="mainmodule"]` is the scanned page; `[jahiatype="module"][path]`): listing items, chrome editable on home. Only paths in the page's site, outside the page's tree and not its ancestors, at most 2,000 |

Without that frame (the drawer opened from jContent rather than Page Builder), `renderedPaths` is
empty and the section shows the **Listings not included** banner.

Locked chrome (a `readOnly` absolute area shown on a page other than its owner) renders in the edit
frame without any `jahiatype` marker, so the frame cannot name it; that is why the ancestor areas
are read on the server.

**Site scan** (`scanSite`), where content is stored: every `jnt:page` of the site with its own tree,
then every content item (each non-folder child of the `contents` folders) with its tree. References
are not followed and ancestor areas are not added, so each node counts once. Locations with issues
come pages first in path order, then content items; terms are ranked by uses.

## Matching

`TermMatcher` folds both the glossary term and the text the same way (`FoldedText`): lower case, NFD
with combining marks removed, typographic apostrophes to `'`, `œ`/`æ`/`ß` to `oe`/`ae`/`ss`. It keeps
an offset map, so a match is reported on the original text, combining marks after the last letter
included. Each term becomes one regular expression (quoted, Unicode classes):

| `matchMode` | Pattern around the term |
|---|---|
| `word` | no word character before, none after |
| `prefix` | no word character before, any word characters after |
| `anywhere` | nothing around |

A word character is a letter or digit of a script that separates words with spaces: Han, Hiragana,
Katakana, Thai, Lao, Khmer and Myanmar letters are not, so a whole-word term matches inside a
Japanese or Chinese sentence and a prefix stops at the end of the term there. As with `\b`, the
check before (after) the term applies only when the term's first (last) character is a word
character: 情報 is found in ユーザー情報, where ー is a Common-script letter. Folding leaves kana and
Han as written but composed (removing combining marks would turn ガ into カ; カ + U+3099 is composed
into ガ, U+F900 into U+8C48) and turns fullwidth ASCII into plain ASCII.

White space inside a term matches any run of white space. Two entries of the same glossary with the
same folded term at the same place are reported once; entries of different glossaries are each
reported, so each glossary owner sees their own reason.

`PlainText.fromHtml` strips tags in one forward pass (every search starts where the previous one
ended; an unclosed tag, comment or script drops the rest of the value), so a hostile value stays
linear: the regular expressions it replaces took 23 s on 160 KB of `<`. `Excerpt` keeps about 60
characters on each side, cut at spaces.

## API

`complianceScan` is a root `Query` field, as it was in Content Compliance Scan; the module's own
settings live under `contentGovernance`.

```graphql
query {
  complianceScan {
    scanPage(path: "/sites/mySite/home/about", language: "en", uiLanguage: "en", renderedPaths: []) {
      pagePath language status scannedContents truncated
      glossaries { path uuid title termCount }
      issues { nodePath nodeUuid nodeName nodeType propertyName propertyLabel term matchedText
               before after reason alternative glossaryPath origin originPath }
    }
    scanSite(siteKey: "mySite", language: "en", uiLanguage: "en") {
      siteKey language status scannedPages scannedContents skippedLocations truncated
      glossaries { path uuid title termCount }
      terms { term glossaryPath reason alternative uses locations }
      locations { path uuid name kind typeLabel issueCount issues { nodePath propertyLabel term matchedText before after reason } }
    }
  }
}
```

`status` is `SAFE`, `ISSUES` or `NO_GLOSSARY` (no glossary with terms for the language). `uiLanguage`
sets the language of the type and field labels (English by default). Report text is plain text;
clients escape it.

Whether the section is on for a site:

```graphql
query { contentGovernance { siteSettings(siteKey: "mySite") { sections { id enabled } } } }
```

A site administrator switches it with `mutation { contentGovernance { saveSiteSettings(siteKey:
"mySite", disabled: ["compliance"]) { disabled } } }` (`site-admin` on the site); the screen is
**Site Settings > Content governance**, tab **Features**. The site stores the ids it switched off
(`disabledFeatures` on `/sites/<key>/content-governance`, `jmix:nolive`), so a site never configured
has the section on.

## Permissions and checks

All checks run on the server; the button, the tab and the dashboard entry only decide what is drawn.

| Field | Refused when |
|---|---|
| `scanPage` | guest; invalid language; the path is missing, unreadable or not a `jnt:page`; the caller lacks `jcr:write` on the page; the site does not enable the module or switched the Compliance section off; more than 5 `scanPage` in one request |
| `scanSite` | guest; invalid language or site key; the site is missing or unreadable; the caller lacks `jContentAccess` on the site; the site does not enable the module or switched the Compliance section off; more than 1 `scanSite` in one request |

The site check is `SiteFeatures.isEnabled(<site path>, "compliance")`: the module enabled on the
site directly or through a module the site enables (`getInstalledModulesWithAllDependencies`, as
jContent checks it), and `compliance` not in the site's switched-off ids. A refusal answers "The
compliance scan is not enabled on this site". The per-request caps stop one request from running a
scan many times through aliases.

`SiteFeatures` reads the site's switches with a system session, because the same gate serves
scheduled jobs of the GEO section that have no caller; it reads only that settings node. The scans
themselves never open a system session: what the caller cannot read, glossaries included, is not
scanned. Both are read-only queries, so the CSRF guard has nothing to decide.

## Limits

| Scan | Contents read | Issues listed |
|---|---|---|
| Page | 5,000 | 1,000 |
| Site | 20,000 | 5,000 |

Past a limit the report sets `truncated` and the UI says so. A page or content item that cannot be
read (a `RepositoryException`: a type definition gone, a provider down) is skipped and counted in
`skippedLocations`; in a page scan, a rendered path or ancestor that cannot be read is skipped.
Rendered paths taken from the frame: 2,000. On a site of 86 pages and 2,272 contents the site scan
takes about a second.

## Migration from Content Compliance Scan

Content Governance declares the same `ccs:` and `ccsmix:` types as Content Compliance Scan 1.0.1 and
the same root GraphQL field (`complianceScan`), and its scan code is the old module's, moved from
`org.jahia.community.compliancescan` to `org.jahia.community.contentgovernance.compliance`. The only
behaviour change on the server is the gate: the old module checked that the site enables
`content-compliance-scan`; this one checks `content-governance` and the site's Compliance switch.

The old module must be uninstalled before Content Governance is installed: both declare the same
types and the same GraphQL field.

**Known gap: labels and icons on a migrated instance.** Jahia registers a node type under the module
that declared it first (its `systemId`), and keeps it there after that module is uninstalled while
content uses it. On an instance that had Content Compliance Scan, the `ccs:` types therefore stay
registered under `content-compliance-scan`, and the definitions in Content Governance's CND are
ignored. Jahia looks up a type's labels and tooltips in the resource bundle, and its icon in the
icons folder, of the module the type is registered under; that module is gone, so jContent and
Content Editor show the technical names (`glossary`, `language`) and a generic icon. The content
itself is untouched and the scan matches types by name, so scans find what they found before (an
instance migrated from 1.0.1 returned the same 159 issues before and after). A fresh install
registers the types under `content-governance` and is not affected. A procedure to hand types in use
over to the new module is not validated yet. What authors see: [`glossary-guide.md`](glossary-guide.md#on-an-instance-that-had-content-compliance-scan).

## Known gaps

- Not read: images (`alt` texts in rich text, image titles), choice-list labels, tags and categories,
  files and their metadata.
- A page scan can list an ancestor's area that the page's template does not display (an area name
  used only on the home page template).
- The site scan does not follow content references to other sites.
- Matching edge cases not handled: halfwidth katakana (ｶﾞｲﾄﾞ is not ガイド), Thai tone marks
  (stripped with other combining marks, so ไม่ also finds ไม), Greek final ς (not folded to σ), and a
  supplementary-plane letter right before a term is not seen by the boundary check.
- The glossary type shows in Page Builder's add-content menu (content folders accept only
  `jmix:droppableContent`); a glossary on a page is never read.
- A translator with `jcr:modifyProperties_<lang>` but no `jcr:write` on the page cannot run the page
  scan: the Compliance tab is greyed out for them.
- The drawer's Compliance section reads pages only: on a main resource (an article on its own URL)
  the tab is greyed out. The site dashboard covers content items.
- The drawer opened from jContent (not Page Builder) has no frame, so listing items are not read;
  the section says so.
- Labels and icons of the glossary types on an instance migrated from Content Compliance Scan (above).
- Publication is not blocked: [`publication-blocking.md`](publication-blocking.md).
- No Cypress suite yet. Unit tests cover matching, match modes, rich text (hostile values included),
  excerpts and the rendered-path filter (`TermMatcherTest`, `MatchModeTest`, `PlainTextTest`,
  `ExcerptTest`, `RenderedRootsTest`) and the feature ids (`FeaturesTest`). The permission refusals
  were verified by hand on Content Compliance Scan (GraphQL probes) and the code is unchanged; the
  refusal when the section is switched off is not covered by an automated test.
