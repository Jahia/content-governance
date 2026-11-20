# The catalog of content rules

The checks of Page quality, of GEO readiness and of the editorial frame apply content rules. Each
rule is a content node of the site, with one id and one value. Example: the rule `seo.title` accepts
a title of 30 to 60 characters. The SEO tab and the GEO tab both report a title under this rule.

## What a rule holds

A rule is a node of type `ccs:contentRule`, and the module, a site or an import writes it with these
properties.

| Property | What it means |
|---|---|
| `ruleId` | The name of the rule, such as `seo.title`. Each finding of the rule shows it. One rule of a site has it. |
| `evaluator` | What checks the rule. The next table lists the values. |
| `domain` | The group of the rule, such as `seo`. Empty: the part of the id before the first dot. |
| `severity` | `critical`, `serious`, `moderate` or `minor`. |
| `blocking` | A finding of a blocking rule stops the publication of the content. |
| `enabled` | On or off. A rule that is off reports nothing. |
| `fixer` | The role of the person who fixes a finding: `editor`, `developer` or `administrator`. Example: `geo.reachable`, a crawler that gets a 403, names an administrator. |
| `advice` | What to do about a finding, one text per language. For the evaluator `model`, the criterion that the model checks. |
| `unit`, `minimum`, `maximum` | The limits of a rule of the evaluator `threshold`, in its unit. |
| `kind`, `pattern`, `matchMode`, `replacement`, `ruleLanguage` | The pattern of a rule of the evaluator `pattern`: a forbidden term, a term to replace, a mandatory mention or a style pattern. A term ignores case and accents, a `*` at its end matches the rest of the word, and a `*` at its start lets it end another word. |
| `patternForms`, `replacementForms` | The other forms of the term and of the replacement, such as the plurals "access tokens" and "jetons d'accès". |
| `patternLanguage` | For a glossary term to translate, the language of the term, such as `en` for "access token" in a rule of `fr`. `frame.check` on a translation then reports a missing replacement. |
| `wrongExamples`, `rightExamples`, `owner`, `decidedOn`, `validFrom`, `validUntil` | The examples, the decision and the dates of a writing rule. |
| `retired` | Set by the module on a rule that the site changed and that its module no longer ships. |

| Evaluator | What checks the rule |
|---|---|
| `pattern` | The pattern of the rule, with no model. `frame.check` reports each match. |
| `threshold` | A measured value against `minimum` and `maximum`. Example: `seo.title` measures the length of the title. |
| `presence` | Something that the rule names is present. |
| `model` | A model, against the advice of the rule. Only a module that calls a model registers it, so content-governance alone checks no such rule. |
| Any other value | The id of a check that a module registers. A built-in rule with no limit names a check of its own id, such as `seo.indexable`. |

Each evaluator is the id of a check that a module registers, as the section
[How a module adds a check](#how-a-module-adds-a-check) shows. Content-governance registers
`pattern`, `threshold`, `presence` and the check of each of its own rules. A rule whose check no
started module registers is inactive and never runs. Example: a rule with the evaluator `model`
shows `active: false`, `missingCheck: "model"` and `needs: "ai-assistant"` in the GraphQL field
`contentRules`.

## Where the rules live

```text
contents/content-insights/
    content-governance/rules/     the 59 rules of this module
        seo.title                 ruleId, evaluator, severity, minimum, maximum, advice...
        ...
    <module>/rules/               the rules of another module
    site/rules/                   the rules that the site adds
```

The checks read every `ccs:contentRule` node under the site, wherever it is. The folders above are
where the module writes, and where a site keeps its own rules. Another module can write rules
elsewhere, such as the writing rules that the frame import of ai-assistant writes.

## How a module ships rules

A module names a folder of its bundle in the manifest header `Jahia-Content-Rules`. Each `.md` file
of that folder is one rule. This module ships its rules in `src/main/resources/content-rules`:

```xml
<Jahia-Content-Rules>content-rules</Jahia-Content-Rules>
```

```markdown
---
id: seo.title
evaluator: threshold
severity: serious
fixer: editor
unit: characters
minimum: 30
maximum: 60
---
Give the page a title, with the main subject first, inside the length range.
```

The frontmatter holds the properties of the node, with `id` for `ruleId`. The body is the advice. A
file with an unknown key, no evaluator, or an unknown severity is skipped with a warning in the log.

The module copies the rules of a module into each site that enables content-governance and installs
that module, under `contents/content-insights/<module>/rules`. The copy runs when a site installs a
module, when a module that ships rules starts, and for every site when content-governance starts.

## How a site changes a rule

Open the node of the rule in jContent, under **Content folders > content-insights > content-governance
> rules**, and change a property. Example: a site sets the minimum of `seo.title` to 40. At the next
run, the SEO tab and the GEO tab report a title of 35 characters under `seo.title`, with the range 40
to 60.

The change applies at the next check, with no publication, because the checks read the edit
workspace. A property that the node lacks keeps the value that the module ships.

A node that a site deletes comes back with its shipped values when the module starts again. Delete a
node to reset its rule, and turn a rule off to stop it.

## How a site adds a rule

Create a **Content rule** in jContent, under **Content folders > content-insights > site > rules**.
Give it a rule id, an evaluator, a severity and an advice. For example, the rule `acme.tone` has the
evaluator `model`, the severity `minor` and the advice "Write in a friendly tone."

The id is free, and it needs no domain. jContent refuses a rule whose id another rule of the site
already has: "The rule seo.title already exists at /sites/...".

## How a new version of a module updates the rules

Each node that the module writes keeps a fingerprint of the values it wrote, in the hidden property
`seedDigest`. A node whose values still match the fingerprint is a node that nobody changed.
[content-governance#79](https://github.com/Jahia/content-governance/issues/79) decided this rule.

| The rule in the new version | Nobody changed the node | The site changed the node |
|---|---|---|
| New | The module adds it. | The module adds it. |
| Changed | The node takes the new values. | The node keeps its values, and gets the properties it lacks. |
| Gone | The module removes the node. | The node stays, with `retired` set. |

A module that stops keeps its rule nodes on the sites. A check that the module registered goes with
it, so its rules show as inactive until the module starts again.

## The upgrade from an earlier version

Content-governance converts the rules of an earlier version once, when it starts:

- The folder `contents/content-insights/jahia` becomes `contents/content-insights/content-governance`.
  A rule that the site changed keeps its values and gets its evaluator and its advice.
- Each `efnt:writingRule` of the editorial frame becomes a `ccs:contentRule` in place, in the edit
  and the live workspace, with the same identifier. Its id is `writing.<node name>`, and its domain is
  `writing`, and its evaluator is `pattern`. The severity `warning` becomes `moderate`, and
  `blocking` becomes `serious` with `blocking` set. A rule of the kind `style` is `minor`, so that it
  stays a soft rule. The justification of each language becomes the advice of that language. Every
  other value stays.

## How a module adds a check

A module registers a check as an OSGi service of the interface `ContentCheck`, from the package
`org.jahia.community.contentgovernance.check.spi` that content-governance exports. The property
`check.id` holds the id that the evaluator of a rule names:

```java
@Component(service = ContentCheck.class, property = "check.id=acme.erpPrice")
public class ErpPriceCheck implements ContentCheck {
    public Set<Scope> scopes() { return Set.of(Scope.VALUE); }
    public List<Finding> check(CheckInput input, Rule rule) { ... }
}
```

The module ships the rule of the check with the header `Jahia-Content-Rules`, as the section
[How a module ships rules](#how-a-module-ships-rules) shows, with `evaluator: acme.erpPrice`. The test
module of this repository, `tests/jahia-module`, does both, with no change to content-governance.

A check states the scopes in which it runs:

| Scope | What the check reads | Who runs it today |
|---|---|---|
| `VALUE` | One value that an editor writes. | The GraphQL field `checkText`, and `frame.check` of the editorial frame. |
| `PAGE` | The HTML of a rendered page or content item. | The page check (`HtmlCheck`): the GraphQL field `checkPage`, `frame.check` with a path, the tool `audit.accessibility`, and `checkText` with the scope `PAGE`. |
| `SITE` | A whole site. | No caller yet. |

A run applies each rule that the site keeps on and whose check is registered for the scope. Each
finding carries the id of the rule, the severity that the site gives the rule, and the text that
breaks the rule. A check that throws an exception gives no finding, and the other checks still run.
One check gives 50 findings or fewer on one input.

```graphql
query {
  contentGovernance {
    checkText(path: "/sites/digitall/home", language: "en", text: "The kit costs 12.50 EUR.") {
      ruleId severity text context
    }
  }
}
```

`threshold` and `presence` judge a value that the caller measured. The caller gives it as the fact
of the input whose key is the id of the rule: `input.withFact("seo.title", 72)`. With no such fact,
they find nothing.

The checks of SEO, readability, links and Jahia health run on the server, as checks of the scope
`PAGE`. A few rules need a browser, because they measure what a browser loads: the eco-design rules
of the web vitals, and `health.graphqlUnavailable`, which reports a failed GraphQL call of the drawer.
Content-governance registers their ids with the property `check.runtime=browser`, so that their rules
are active. A run on the server skips them, so the tools for agents never report them. A browser rule
never blocks, even when its node sets `blocking`.

## One id for each practice

Version 1.12 keeps one rule id for each practice. A check on the server and a check in the drawer
report one practice under the same id.

| Removed id | Kept id |
|---|---|
| `seo.imageAlt`, `geo.imageAlt` | `html.imageAltMissing` |
| `seo.lang` | `html.pageLanguageMissing` |
| `seo.langMatches` | `html.pageLanguageMismatch` |
| `seo.singleH1` | `html.headingH1Missing` when the page has no h1, `html.headingH1Multiple` when it has more than one |
| `readability.headingSkips` | `html.headingLevelSkipped` |
| `seo.genericAnchors` | `html.linkTextVague` |
| `html.pageTitleMissing` | `seo.title`: a missing title has the length 0 |
| `html.pageDescriptionMissing` | `seo.metaDescription`: a missing description has the length 0 |

A site keeps the nodes of the removed ids until content-governance starts again, and then the table
of [How a new version of a module updates the rules](#how-a-new-version-of-a-module-updates-the-rules)
applies. The module removes a node that nobody changed. A node that the site changed stays, with
`retired` set. No module registers its check any more, so its rule shows as inactive and names its
own id as the missing check. `geo.imageAlt` is the exception: its evaluator `threshold` stays
registered, but no caller measures its value, so it gives no finding. To keep a change, such as a
severity, set it again on the node of the kept id.

## How a check reads the rules

- The Java checks of GEO readiness read the rules of the site through `ContentRules.forSite`. A site
  scan reads them once, at its start.
- The checks of Page quality on the server read them through `ContentRules.forPath`, at the start of
  each check. The analyzers of the drawer read them through the GraphQL field
  `contentGovernance.contentRules(siteKey)`, at the start of each audit. Each rule gives its
  `evaluator` and `blocking` there too.
- The SEO assist reads the ranges of `seo.title` and `seo.metaDescription`, and states them in its
  prompt.
- The editorial frame reads the rules of the evaluator `pattern` from the live workspace.
  `frame.check` reports their matches, and the prompt of a model carries them.
- A rule with no node, and a site that cannot be read, use the values that the module ships.

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
- The instructions that ai-assistant gives a model for an SEO review state the default ranges of
  `seo.title` and `seo.metaDescription`. A change on a site does not reach them.

## The rules of this module

| Rule | Evaluator | Severity | Limits | Fixed by |
|---|---|---|---|---|
| `seo.title` | `threshold` | serious | minimum 30, maximum 60 characters | editor |
| `seo.metaDescription` | `threshold` | serious | minimum 50, maximum 160 characters | editor |
| `seo.indexable` | `seo.indexable` | critical | none | editor |
| `seo.followLinks` | `seo.followLinks` | moderate | none | editor |
| `seo.canonical` | `seo.canonical` | moderate | none | developer |
| `seo.openGraph` | `seo.openGraph` | moderate | none | editor |
| `seo.structuredData` | `seo.structuredData` | moderate | none | developer |
| `seo.structuredDataValid` | `seo.structuredDataValid` | serious | none | developer |
| `geo.fetched` | `geo.fetched` | critical | none | administrator |
| `geo.reachable` | `geo.reachable` | critical | none | administrator |
| `geo.policyMatchesReality` | `geo.policyMatchesReality` | serious | none | administrator |
| `geo.noClientRedirect` | `geo.noClientRedirect` | critical | none | developer |
| `geo.namedInRobots` | `geo.namedInRobots` | minor | none | administrator |
| `geo.guestReadable` | `geo.guestReadable` | critical | none | administrator |
| `geo.contentInInitialHtml` | `threshold` | critical | minimum 50 words | developer |
| `geo.sameContentForCrawlers` | `threshold` | critical | minimum 0.5 ratio | administrator |
| `geo.headingOutline` | `geo.headingOutline` | minor | none | editor |
| `geo.dateModified` | `geo.dateModified` | minor | none | developer |
| `geo.robotsPresent` | `geo.robotsPresent` | serious | none | administrator |
| `geo.llmsPresent` | `geo.llmsPresent` | minor | none | administrator |
| `readability.veryHardText` | `readability.veryHardText` | serious | none | editor |
| `readability.hardText` | `readability.hardText` | moderate | none | editor |
| `readability.longSentences` | `threshold` | minor | maximum 25 words | editor |
| `readability.longParagraphs` | `threshold` | moderate | maximum 80 words | editor |
| `readability.thinContent` | `threshold` | minor | minimum 300 words | editor |
| `ecodesign.pageWeight` | `threshold` | moderate | maximum 1024 kilobytes | developer |
| `ecodesign.requests` | `threshold` | moderate | maximum 70 requests | developer |
| `ecodesign.domSize` | `threshold` | moderate | maximum 1500 elements | developer |
| `ecodesign.lazyLoading` | `ecodesign.lazyLoading` | moderate | none | developer |
| `ecodesign.imageFormats` | `ecodesign.imageFormats` | moderate | none | editor |
| `ecodesign.oversizedImages` | `ecodesign.oversizedImages` | moderate | none | editor |
| `html.*` | the id of the rule | see below | none | see below |

The 14 rules `html.*` are the rules of the HTML check, and
[`html-rules.md`](../page-quality/html-rules.md) lists them. Each rule names a check of its own id.
| `ecodesign.imageDimensions` | `ecodesign.imageDimensions` | moderate | none | developer |
| `ecodesign.webFonts` | `threshold` | minor | maximum 2 files | developer |
| `ecodesign.autoplay` | `ecodesign.autoplay` | moderate | none | editor |
| `ecodesign.thirdParty` | `threshold` | moderate | maximum 5 hosts | developer |
| `ecodesign.heavyAssets` | `threshold` | moderate | maximum 300 kilobytes | developer |
| `links.broken` | `links.broken` | critical | none | editor |
| `links.hardcodedUrls` | `links.hardcodedUrls` | moderate | none | editor |
| `links.mixedContent` | `links.mixedContent` | serious | none | developer |
| `links.blankNoopener` | `links.blankNoopener` | minor | none | developer |
| `health.rawI18nKeys` | `health.rawI18nKeys` | critical | none | developer |
| `health.unpublishedChanges` | `health.unpublishedChanges` | serious | none | editor |
| `health.missingTranslations` | `health.missingTranslations` | serious | none | editor |
| `health.placeholderText` | `health.placeholderText` | serious | none | editor |
| `health.graphqlUnavailable` | `health.graphqlUnavailable` | minor | none | administrator |
