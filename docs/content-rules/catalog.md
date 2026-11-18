# The catalog of content rules

The checks of Page quality and of GEO readiness apply content rules. Each rule exists once, in a
catalog, with one id and one value. Example: the rule `seo.title` accepts a title of 30 to 60
characters. The SEO tab and the GEO tab both report a title under this rule.

## What a rule holds

| Field | What it means |
|---|---|
| Id | The name of the rule, such as `seo.title`. Each finding of the rule shows it. |
| Domain | The group of the rule, the part of the id before the dot: `seo`, `geo`, `readability`, `ecodesign`, `links` or `health`. |
| Severity | `critical`, `serious`, `moderate` or `minor`. |
| Switch | On or off. A rule that is off reports nothing. |
| Limits | A minimum, a maximum, or both, in the unit of the rule. Many rules have no limit. |
| Advice | What to do about a finding, in English. |
| Fixer | The role of the person who fixes a finding: `editor`, `developer` or `administrator`. Example: `geo.reachable`, a crawler that gets a 403, names an administrator. |

## Where the rules live

The module ships the base catalog in `src/main/resources/META-INF/content-rules/catalog.json`.

The module copies the base catalog into each site that enables it. Each rule becomes one content
node of type `ccs:contentRule`, in this folder of the site:

```text
contents/content-insights/jahia/rules/
    seo.title          ruleId, enabled, severity, minimum, maximum
    seo.metaDescription
    ...
```

The copy runs when a site enables the module, and for every such site when the module starts. It
adds the rules that a site does not have. It never changes a node that exists.

A customer can keep its own content beside the folder `jahia`, in
`contents/content-insights/<customer>/`. In this version, the checks read only the folder
`jahia/rules`.

## How a site changes a rule

Open the node of the rule in jContent, under **Content folders > content-insights > jahia > rules**.
Change one of these fields:

- **On**: clear it to turn the rule off.
- **Severity**: the severity of each finding of the rule.
- **Minimum** and **Maximum**: the limits. A limit counts only for a rule that the catalog gives
  that limit. Example: a minimum on `seo.canonical` changes nothing.

The change applies at the next check, with no publication, because the checks read the edit workspace.
Example: a site sets the minimum of `seo.title` to 40. At the next run, the SEO tab and the GEO tab
report a title of 35 characters under `seo.title`, with the range 40 to 60.

A node that a site deletes comes back with its default when the module starts again, so turn a
rule off to stop it.

[content-governance#79](https://github.com/Jahia/content-governance/issues/79) decides how a
customer changes a Jahia rule in a later version, and how a new version updates the folder `jahia`.

## How a check reads the rules

- The Java checks of GEO readiness read the rules of the site through `ContentRules.forSite`. A site
  scan reads them once, at its start.
- The analyzers of Page quality read them through the GraphQL field
  `contentGovernance.contentRules(siteKey)`, at the start of each audit.
- The SEO assist reads the ranges of `seo.title` and `seo.metaDescription`, and states them in its
  prompt.
- A rule with no node, and a site that cannot be read, use the base catalog.

Each finding carries the id of its rule: `rule` on a recommendation of Page quality, and `rule` on a
check of GEO readiness. The site scan of GEO readiness stores the rule of each check in `rules`,
beside `severities`.

GEO readiness shows three severities. `critical` stays critical. `serious` and `moderate` show as
important. `minor` shows as advisory.

## What the catalog does not hold

- The accessibility findings keep the ids and the impacts of axe-core.
- The readability bands come from the formula of each language. The rules `readability.hardText`
  and `readability.veryHardText` have a switch and a severity, and no limit.
- The limits of a scan, such as the number of links that the Links tab checks, are not rules.
- The length hints of the social card of the SEO assist (`og:description`) are not rules.
- The skill `seo-review` states the default ranges of `seo.title` and `seo.metaDescription`. A change
  on a site does not reach it.

## The rules of the base catalog

| Rule | Severity | Limits | Fixed by |
|---|---|---|---|
| `seo.title` | serious | minimum 30, maximum 60 characters | editor |
| `seo.metaDescription` | serious | minimum 50, maximum 160 characters | editor |
| `seo.indexable` | critical | none | editor |
| `seo.followLinks` | moderate | none | editor |
| `seo.canonical` | moderate | none | developer |
| `seo.openGraph` | moderate | none | editor |
| `seo.structuredData` | moderate | none | developer |
| `seo.structuredDataValid` | serious | none | developer |
| `seo.lang` | moderate | none | developer |
| `seo.langMatches` | serious | none | developer |
| `seo.imageAlt` | moderate | none | editor |
| `seo.genericAnchors` | minor | none | editor |
| `seo.singleH1` | serious | none | editor |
| `geo.fetched` | critical | none | administrator |
| `geo.reachable` | critical | none | administrator |
| `geo.policyMatchesReality` | serious | none | administrator |
| `geo.noClientRedirect` | critical | none | developer |
| `geo.namedInRobots` | minor | none | administrator |
| `geo.guestReadable` | critical | none | administrator |
| `geo.contentInInitialHtml` | critical | minimum 50 words | developer |
| `geo.sameContentForCrawlers` | critical | minimum 0.5 ratio | administrator |
| `geo.headingOutline` | minor | none | editor |
| `geo.dateModified` | minor | none | developer |
| `geo.imageAlt` | minor | minimum 0.8 ratio | editor |
| `geo.robotsPresent` | serious | none | administrator |
| `geo.llmsPresent` | minor | none | administrator |
| `readability.veryHardText` | serious | none | editor |
| `readability.hardText` | moderate | none | editor |
| `readability.longSentences` | minor | maximum 25 words | editor |
| `readability.longParagraphs` | moderate | maximum 80 words | editor |
| `readability.headingSkips` | moderate | none | editor |
| `readability.thinContent` | minor | minimum 300 words | editor |
| `ecodesign.pageWeight` | moderate | maximum 1024 kilobytes | developer |
| `ecodesign.requests` | moderate | maximum 70 requests | developer |
| `ecodesign.domSize` | moderate | maximum 1500 elements | developer |
| `ecodesign.lazyLoading` | moderate | none | developer |
| `ecodesign.imageFormats` | moderate | none | editor |
| `ecodesign.oversizedImages` | moderate | none | editor |
| `ecodesign.imageDimensions` | moderate | none | developer |
| `ecodesign.webFonts` | minor | maximum 2 files | developer |
| `ecodesign.autoplay` | moderate | none | editor |
| `ecodesign.thirdParty` | moderate | maximum 5 hosts | developer |
| `ecodesign.heavyAssets` | moderate | maximum 300 kilobytes | developer |
| `links.broken` | critical | none | editor |
| `links.hardcodedUrls` | moderate | none | editor |
| `links.mixedContent` | serious | none | developer |
| `links.blankNoopener` | minor | none | developer |
| `health.rawI18nKeys` | critical | none | developer |
| `health.unpublishedChanges` | serious | none | editor |
| `health.missingTranslations` | serious | none | editor |
| `health.placeholderText` | serious | none | editor |
| `health.graphqlUnavailable` | minor | none | administrator |
